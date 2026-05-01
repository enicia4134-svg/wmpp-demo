package com.zqw.wmpp.scheduler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

@Component
public class PusherClient {

    private final Map<String, RestClient> pushers = new HashMap<>();
    private final Map<String, String> pusherBaseUrls = new HashMap<>();

    public PusherClient(@Value("${wmpp.pusher.nodes:}") String nodes) {
        // format: pusher-1=http://host1:8084,pusher-2=http://host2:8085
        if (nodes == null) return;
        for (String part : nodes.split(",")) {
            String p = part.trim();
            if (p.isBlank()) continue;
            String[] kv = p.split("=", 2);
            if (kv.length != 2) continue;
            String id = kv[0].trim();
            String baseUrl = kv[1].trim();
            pushers.put(id, RestClient.builder().baseUrl(baseUrl).build());
            pusherBaseUrls.put(id, baseUrl);
        }
    }

    public record PushBody(String message) {}

    public void broadcast(String pusherId, String appId, String msg) {
        System.out.println("[PUSHER_CLIENT_BROADCAST] pusherId=" + pusherId + ", appId=" + appId + ", msg=" + summarize(msg));
        RestClient http = require(pusherId);
        http.post()
                .uri(uriBuilder -> uriBuilder.path("/internal/push/broadcast")
                        .queryParam("appId", appId)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PushBody(msg))
                .retrieve()
                .toBodilessEntity();
        System.out.println("[PUSHER_CLIENT_BROADCAST_DONE] pusherId=" + pusherId + ", appId=" + appId);
    }

    public void pushUser(String pusherId, String appId, String userId, String msg) {
        System.out.println("[PUSHER_CLIENT_USER] pusherId=" + pusherId + ", appId=" + appId + ", userId=" + userId + ", msg=" + summarize(msg));
        RestClient http = require(pusherId);
        http.post()
                .uri(uriBuilder -> uriBuilder.path("/internal/push/user")
                        .queryParam("appId", appId)
                        .queryParam("userId", userId)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PushBody(msg))
                .retrieve()
                .toBodilessEntity();
        System.out.println("[PUSHER_CLIENT_USER_DONE] pusherId=" + pusherId + ", appId=" + appId + ", userId=" + userId);
    }

    private String summarize(String message) {
        if (message == null) return "";
        String t = message.replace('\n', ' ').trim();
        return t.length() <= 80 ? t : t.substring(0, 80) + "...";
    }

    private RestClient require(String pusherId) {
        RestClient rc = pushers.get(pusherId);
        if (rc == null) throw new IllegalStateException("Unknown pusherId: " + pusherId);
        return rc;
    }

    public String getBaseUrl(String pusherId) {
        String url = pusherBaseUrls.get(pusherId);
        if (url == null) throw new IllegalStateException("Unknown pusherId: " + pusherId);
        return url;
    }

    public Map<String, String> getAllBaseUrls() {
        return Map.copyOf(pusherBaseUrls);
    }
}

