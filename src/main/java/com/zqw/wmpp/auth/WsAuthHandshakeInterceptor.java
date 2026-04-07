package com.zqw.wmpp.auth;

import com.zqw.wmpp.PushWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

@Component
public class WsAuthHandshakeInterceptor implements HandshakeInterceptor {

    @Autowired
    private AppRegistryService appRegistryService;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        URI uri = request.getURI();
        String appId = getQueryParam(uri, "appId");
        String userId = getQueryParam(uri, "userId");

        if (appId == null || appId.isBlank() || userId == null || userId.isBlank()) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }

        if (appRegistryService.find(appId) == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        attributes.put(PushWebSocketHandler.ATTR_APP_ID, appId);
        attributes.put(PushWebSocketHandler.ATTR_USER_ID, userId);
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
        // no-op
    }

    private static String getQueryParam(URI uri, String key) {
        if (uri == null) return null;
        String query = uri.getQuery();
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && Objects.equals(kv[0], key)) {
                return kv[1];
            }
        }
        return null;
    }
}

