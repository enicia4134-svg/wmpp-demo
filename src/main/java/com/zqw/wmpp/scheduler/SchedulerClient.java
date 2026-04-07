package com.zqw.wmpp.scheduler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class SchedulerClient {

    private final RestClient http;

    public SchedulerClient(@Value("${wmpp.scheduler.baseUrl:http://localhost:8081}") String baseUrl) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
    }

    public SchedulerController.AssignResponse assign(String appId, String userId) {
        return http.get()
                .uri(uriBuilder -> uriBuilder.path("/scheduler/assign")
                        .queryParam("appId", appId)
                        .queryParam("userId", userId)
                        .build())
                .retrieve()
                .body(SchedulerController.AssignResponse.class);
    }

    public void broadcast(String appId, String msg) {
        http.post()
                .uri(uriBuilder -> uriBuilder.path("/scheduler/broadcast")
                        .queryParam("appId", appId)
                        .queryParam("message", msg)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .toBodilessEntity();
    }

    public void user(String appId, String userId, String msg) {
        http.post()
                .uri(uriBuilder -> uriBuilder.path("/scheduler/user")
                        .queryParam("appId", appId)
                        .queryParam("userId", userId)
                        .queryParam("message", msg)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .toBodilessEntity();
    }

    public void users(String appId, List<String> userIds, String msg) {
        SchedulerController.UsersBody body = new SchedulerController.UsersBody();
        body.userIds = userIds;
        body.message = msg;
        http.post()
                .uri(uriBuilder -> uriBuilder.path("/scheduler/users")
                        .queryParam("appId", appId)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    public void topic(String appId, String topic, String msg) {
        http.post()
                .uri(uriBuilder -> uriBuilder.path("/scheduler/topic")
                        .queryParam("appId", appId)
                        .queryParam("topic", topic)
                        .queryParam("message", msg)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .toBodilessEntity();
    }
}

