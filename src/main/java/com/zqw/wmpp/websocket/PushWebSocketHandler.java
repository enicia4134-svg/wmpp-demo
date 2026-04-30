package com.zqw.wmpp.websocket;

import com.zqw.wmpp.session.SessionRegistry;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;



public class PushWebSocketHandler extends TextWebSocketHandler {

    private final SessionRegistry sessionRegistry;

    public PushWebSocketHandler(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String appId = session.getAttributes().getOrDefault("appId", "").toString();
        String userId = session.getAttributes().getOrDefault("userId", "").toString();
        sessionRegistry.registerWebSocket(appId, userId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String appId = session.getAttributes().getOrDefault("appId", "").toString();
        String userId = session.getAttributes().getOrDefault("userId", "").toString();
        String payload = message.getPayload();
        if (payload != null && payload.contains("\"type\":\"ack\"")) {
            String msgId = extractField(payload, "msgId");
            if (msgId != null && !msgId.isBlank()) {
                sessionRegistry.ack(appId, userId, msgId);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String appId = session.getAttributes().getOrDefault("appId", "").toString();
        String userId = session.getAttributes().getOrDefault("userId", "").toString();
        sessionRegistry.disconnect(appId, userId);
    }

    private String extractField(String json, String key) {
        String needle = "\"" + key + "\"" + ":";
        int idx = json.indexOf(needle);
        if (idx < 0) return null;
        int start = json.indexOf('"', idx + needle.length());
        if (start < 0) return null;
        int end = json.indexOf('"', start + 1);
        if (end < 0) return null;
        return json.substring(start + 1, end);
    }
}
