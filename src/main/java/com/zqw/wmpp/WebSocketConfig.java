package com.zqw.wmpp;

import com.zqw.wmpp.auth.WsAuthHandshakeInterceptor;
import com.zqw.wmpp.role.WmppRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private WsAuthHandshakeInterceptor wsAuthHandshakeInterceptor;

    @Autowired
    private PushWebSocketHandler pushWebSocketHandler;

    @Autowired
    private WmppRole role;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        if (role != WmppRole.pusher && role != WmppRole.mono) {
            return;
        }
        registry.addHandler(pushWebSocketHandler, "/ws/push", "/connect")
                .addInterceptors(wsAuthHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}