package com.zqw.wmpp.sdk.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public class WmppServerClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final HttpUrl baseUrl;
    private final String appId;
    private final String appSecret;
    private final OkHttpClient http;
    private final ObjectMapper om = new ObjectMapper();

    public WmppServerClient(String baseUrl, String appId, String appSecret) {
        this(baseUrl, appId, appSecret, Duration.ofSeconds(5));
    }

    public WmppServerClient(String baseUrl, String appId, String appSecret, Duration timeout) {
        this.baseUrl = Objects.requireNonNull(HttpUrl.parse(baseUrl), "baseUrl");
        this.appId = Objects.requireNonNull(appId, "appId");
        this.appSecret = Objects.requireNonNull(appSecret, "appSecret");
        this.http = new OkHttpClient.Builder()
                .callTimeout(timeout)
                .build();
    }

    public String broadcast(String msg) throws IOException {
        HttpUrl url = baseUrl.newBuilder()
                .addPathSegments("push/broadcast")
                .addQueryParameter("message", msg)
                .build();
        return callPost(url, null);
    }

    public String pushUser(String userId, String msg) throws IOException {
        HttpUrl url = baseUrl.newBuilder()
                .addPathSegments("push/user")
                .addQueryParameter("userId", userId)
                .addQueryParameter("message", msg)
                .build();
        return callPost(url, null);
    }

    public String pushUsers(List<String> userIds, String msg) throws IOException {
        HttpUrl url = baseUrl.newBuilder()
                .addPathSegments("push/users")
                .build();
        String json = om.writeValueAsString(new UsersPushRequest(userIds, msg));
        return callPost(url, RequestBody.create(json, JSON));
    }

    public String pushTopic(String topic, String msg) throws IOException {
        HttpUrl url = baseUrl.newBuilder()
                .addPathSegments("push/topic")
                .addQueryParameter("topic", topic)
                .addQueryParameter("message", msg)
                .build();
        return callPost(url, null);
    }

    private String callPost(HttpUrl url, RequestBody body) throws IOException {
        Request.Builder rb = new Request.Builder()
                .url(url)
                .post(body == null ? RequestBody.create(new byte[0], null) : body)
                .header("X-App-Id", appId)
                .header("X-App-Secret-Key", appSecret);

        try (Response resp = http.newCall(rb.build()).execute()) {
            if (!resp.isSuccessful()) {
                String err = resp.body() == null ? "" : resp.body().string();
                throw new IOException("HTTP " + resp.code() + " " + err);
            }
            return resp.body() == null ? "" : resp.body().string();
        }
    }

    /** JSON 字段名 message，与 SRS/详细设计一致 */
    public record UsersPushRequest(List<String> userIds, String message) {}
}

