package com.zqw.wmpp.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zqw.wmpp.reliability.DeliveryTracker;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionRegistry {

    public record Key(String appId, String userId) {}

    public static final long DEFAULT_SSE_TIMEOUT_MS = 0L; // never timeout by server

    private final Clock clock = Clock.systemUTC();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DeliveryTracker deliveryTracker;

    public SessionRegistry(DeliveryTracker deliveryTracker) {
        this.deliveryTracker = deliveryTracker;
    }

    // Map<AppId, Map<UserId, SessionInfo>>
    private final Map<String, Map<String, SessionInfo>> sessions = new ConcurrentHashMap<>();

    public void registerWebSocket(String appId, String userId, WebSocketSession session) {
        Objects.requireNonNull(appId);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(session);

        SessionInfo info = sessions
                .computeIfAbsent(appId, k -> new ConcurrentHashMap<>())
                .compute(userId, (uid, old) -> {
                    if (old == null) return new SessionInfo(session, null, clock.millis());
                    old.closeWebSocketQuietly(CloseStatus.NORMAL);
                    return new SessionInfo(session, old.sseEmitter, clock.millis());
                });

        // ensure lastHeartbeat updated
        info.touch(clock.millis());
    }

    public void unregisterWebSocket(String appId, String userId, WebSocketSession session) {
        Map<String, SessionInfo> appMap = sessions.get(appId);
        if (appMap == null) return;
        appMap.computeIfPresent(userId, (uid, info) -> {
            if (info.webSocketSession == session) {
                info.webSocketSession = null;
            }
            return info.isEmpty() ? null : info;
        });
        if (appMap.isEmpty()) sessions.remove(appId);
    }

    public SseEmitter registerSse(String appId, String userId) {
        Objects.requireNonNull(appId);
        Objects.requireNonNull(userId);

        SseEmitter emitter = new SseEmitter(DEFAULT_SSE_TIMEOUT_MS);
        emitter.onCompletion(() -> removeSse(appId, userId, emitter));
        emitter.onTimeout(() -> removeSse(appId, userId, emitter));
        emitter.onError(e -> removeSse(appId, userId, emitter));

        sessions.computeIfAbsent(appId, k -> new ConcurrentHashMap<>())
                .compute(userId, (uid, old) -> {
                    if (old == null) return new SessionInfo(null, emitter, clock.millis());
                    old.completeSseQuietly();
                    old.sseEmitter = emitter;
                    old.touch(clock.millis());
                    return old;
                });

        return emitter;
    }

    public void touchHeartbeat(String appId, String userId) {
        Map<String, SessionInfo> appMap = sessions.get(appId);
        if (appMap == null) return;
        SessionInfo info = appMap.get(userId);
        if (info == null) return;
        info.touch(clock.millis());
    }

    public int getOnlineCount(String appId) {
        Map<String, SessionInfo> appMap = sessions.get(appId);
        if (appMap == null) return 0;
        int n = 0;
        for (SessionInfo info : appMap.values()) {
            if (info != null && info.webSocketSession != null && info.webSocketSession.isOpen()) n++;
        }
        return n;
    }

    public void broadcast(String appId, String message) {
        Map<String, SessionInfo> appMap = sessions.get(appId);
        if (appMap == null || appMap.isEmpty()) return;
        for (Map.Entry<String, SessionInfo> e : appMap.entrySet()) {
            pushToUser(appId, e.getKey(), message);
        }
    }

    public void pushToUser(String appId, String userId, String message) {
        Map<String, SessionInfo> appMap = sessions.get(appId);
        if (appMap == null) return;
        SessionInfo info = appMap.get(userId);
        if (info == null) return;

        String msgId = extractMsgId(message);

        // Prefer SSE for data push channel if present
        if (info.sseEmitter != null) {
            try {
                info.sseEmitter.send(SseEmitter.event().name("message").data(message));
                if (msgId != null) {
                    deliveryTracker.trackSend(appId, userId, msgId, message);
                }
                return;
            } catch (IOException ex) {
                removeSse(appId, userId, info.sseEmitter);
            }
        }

        WebSocketSession ws = info.webSocketSession;
        if (ws != null && ws.isOpen()) {
            try {
                ws.sendMessage(new TextMessage(message));
                if (msgId != null) {
                    deliveryTracker.trackSend(appId, userId, msgId, message);
                }
            } catch (IOException ignored) {
                // ignore
            }
        }
    }

    public Optional<WebSocketSession> getWebSocket(String appId, String userId) {
        Map<String, SessionInfo> appMap = sessions.get(appId);
        if (appMap == null) return Optional.empty();
        SessionInfo info = appMap.get(userId);
        if (info == null) return Optional.empty();
        return Optional.ofNullable(info.webSocketSession);
    }

    public boolean ack(String appId, String userId, String msgId) {
        return deliveryTracker.markAcked(appId, userId, msgId);
    }

    public void closeStaleWebSockets(long staleAfterMs) {
        long now = clock.millis();
        for (var appEntry : sessions.entrySet()) {
            String appId = appEntry.getKey();
            Map<String, SessionInfo> appMap = appEntry.getValue();
            if (appMap == null) continue;
            for (var userEntry : appMap.entrySet()) {
                String userId = userEntry.getKey();
                SessionInfo info = userEntry.getValue();
                if (info == null) continue;
                WebSocketSession ws = info.webSocketSession;
                if (ws == null || !ws.isOpen()) continue;
                if (now - info.lastHeartbeatAtMs > staleAfterMs) {
                    info.closeWebSocketQuietly(CloseStatus.SESSION_NOT_RELIABLE);
                    unregisterWebSocket(appId, userId, ws);
                }
            }
            if (appMap.isEmpty()) sessions.remove(appId);
        }
    }

    private void removeSse(String appId, String userId, SseEmitter emitter) {
        Map<String, SessionInfo> appMap = sessions.get(appId);
        if (appMap == null) return;
        appMap.computeIfPresent(userId, (uid, info) -> {
            if (info.sseEmitter == emitter) {
                info.sseEmitter = null;
            }
            return info.isEmpty() ? null : info;
        });
        if (appMap.isEmpty()) sessions.remove(appId);
    }

    private String extractMsgId(String message) {
        if (message == null || message.isBlank()) return null;
        try {
            JsonNode node = objectMapper.readTree(message);
            JsonNode idNode = node.get("msgId");
            if (idNode == null || idNode.isNull()) return null;
            String id = idNode.asText();
            return (id == null || id.isBlank()) ? null : id;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static final class SessionInfo {
        private volatile WebSocketSession webSocketSession;
        private volatile SseEmitter sseEmitter;
        private volatile long lastHeartbeatAtMs;

        private SessionInfo(WebSocketSession webSocketSession, SseEmitter sseEmitter, long lastHeartbeatAtMs) {
            this.webSocketSession = webSocketSession;
            this.sseEmitter = sseEmitter;
            this.lastHeartbeatAtMs = lastHeartbeatAtMs;
        }

        private void touch(long nowMs) {
            this.lastHeartbeatAtMs = nowMs;
        }

        private boolean isEmpty() {
            boolean wsEmpty = (webSocketSession == null) || !webSocketSession.isOpen();
            return wsEmpty && sseEmitter == null;
        }

        private void closeWebSocketQuietly(CloseStatus status) {
            if (webSocketSession == null) return;
            try {
                if (webSocketSession.isOpen()) webSocketSession.close(status);
            } catch (Exception ignored) {
            }
        }

        private void completeSseQuietly() {
            if (sseEmitter == null) return;
            try {
                sseEmitter.complete();
            } catch (Exception ignored) {
            }
        }
    }
}

