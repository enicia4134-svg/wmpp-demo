package com.zqw.wmpp;

import com.zqw.wmpp.registry.RegistryClient;
import com.zqw.wmpp.role.WmppRole;
import com.zqw.wmpp.session.SessionRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class PushWebSocketHandler extends TextWebSocketHandler {

    public static final String ATTR_APP_ID = "wmpp.appId";
    public static final String ATTR_USER_ID = "wmpp.userId";

    @Autowired
    private SessionRegistry sessionRegistry;

    @Autowired
    private WmppRole role;

    @Autowired
    private RegistryClient registryClient;

    @Value("${wmpp.pusher.id:pusher-1}")
    private String pusherId;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {

        String appId = (String) session.getAttributes().get(ATTR_APP_ID);
        String userId = (String) session.getAttributes().get(ATTR_USER_ID);

        if (appId != null && userId != null) {
            sessionRegistry.registerWebSocket(appId, userId, session);
            if (role == WmppRole.pusher) {
                registryClient.register(appId, userId, pusherId);
            }
            System.out.println("✅ 用户上线: appId=" + appId + ", userId=" + userId);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {

        String appId = (String) session.getAttributes().get(ATTR_APP_ID);
        String userId = (String) session.getAttributes().get(ATTR_USER_ID);

        if (appId != null && userId != null) {
            sessionRegistry.unregisterWebSocket(appId, userId, session);
            if (role == WmppRole.pusher) {
                registryClient.unregister(appId, userId, pusherId);
            }
            System.out.println("❌ 用户下线: appId=" + appId + ", userId=" + userId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Control channel: heartbeat + control commands
        String payload = message.getPayload();
        if (payload == null) return;

        String appId = (String) session.getAttributes().get(ATTR_APP_ID);
        String userId = (String) session.getAttributes().get(ATTR_USER_ID);
        if (appId == null || userId == null) return;

        // Minimal protocol: "ping" / "heartbeat"
        if ("ping".equalsIgnoreCase(payload) || "heartbeat".equalsIgnoreCase(payload)) {
            sessionRegistry.touchHeartbeat(appId, userId);
            try {
                session.sendMessage(new TextMessage("pong"));
            } catch (Exception ignored) {
            }
        }
    }

    // NOTE: In microservices mode, delivery is done via SessionRegistry and pusher internal HTTP APIs.
}