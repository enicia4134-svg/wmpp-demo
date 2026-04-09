package com.zqw.wmpp.registry;

import com.zqw.wmpp.role.WmppRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RegistryClient {

    private final WmppRole role;
    private final RestClient http;

    public RegistryClient(
            WmppRole role,
            @Value("${wmpp.registry.baseUrl:http://localhost:8090}") String baseUrl
    ) {
        this.role = role;
        this.http = RestClient.builder().baseUrl(baseUrl).build();
    }

    public void register(String appId, String userId, String pusherId) {
        if (role == WmppRole.mono) return;
        http.post()
                .uri("/registry/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegistryController.RegisterRequest(appId, userId, pusherId))
                .retrieve()
                .toBodilessEntity();
    }

    public void unregister(String appId, String userId, String pusherId) {
        if (role == WmppRole.mono) return;
        http.post()
                .uri("/registry/unregister")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegistryController.RegisterRequest(appId, userId, pusherId))
                .retrieve()
                .toBodilessEntity();
    }

    public String lookupPusher(String appId, String userId) {
        if (role == WmppRole.mono) return "local";
        return http.get()
                .uri(uriBuilder -> uriBuilder.path("/registry/lookup")
                        .queryParam("appId", appId)
                        .queryParam("userId", userId)
                        .build())
                .retrieve()
                .body(String.class);
    }

    public java.util.Map<String, Integer> pusherCounts(String appId) {
        if (role == WmppRole.mono) return java.util.Map.of("local", 0);
        java.util.Map<String, Integer> result = http.get()
                .uri(uriBuilder -> uriBuilder.path("/registry/pusher-counts")
                        .queryParam("appId", appId)
                        .build())
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<java.util.Map<String, Integer>>() {});
        return result == null ? java.util.Map.of() : result;
    }
}

