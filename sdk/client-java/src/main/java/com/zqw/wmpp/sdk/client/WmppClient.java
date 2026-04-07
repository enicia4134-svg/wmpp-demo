package com.zqw.wmpp.sdk.client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.*;

public class WmppClient implements AutoCloseable {

    public interface MessageHandler {
        void onMessage(String msg);
    }

    private final String baseHttp;
    private final String baseWs;
    private final String appId;
    private final String userId;
    private final MessageHandler handler;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService sseExecutor = Executors.newSingleThreadExecutor();

    private volatile WebSocket ws;
    private volatile boolean closed;

    public WmppClient(String baseHttp, String baseWs, String appId, String userId, MessageHandler handler) {
        this.baseHttp = Objects.requireNonNull(baseHttp);
        this.baseWs = Objects.requireNonNull(baseWs);
        this.appId = Objects.requireNonNull(appId);
        this.userId = Objects.requireNonNull(userId);
        this.handler = handler == null ? System.out::println : handler;
    }

    public void start() {
        startSse();
        startWs();
    }

    private void startWs() {
        scheduler.execute(this::connectWsLoop);
        scheduler.scheduleAtFixedRate(() -> {
            WebSocket w = ws;
            if (w != null) w.sendText("ping", true);
        }, 5, 15, TimeUnit.SECONDS);
    }

    private void connectWsLoop() {
        while (!closed) {
            try {
                URI uri = URI.create(baseWs + "/ws/push?appId=" + appId + "&userId=" + userId);
                ws = http.newWebSocketBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .buildAsync(uri, new WebSocket.Listener() {
                            @Override
                            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                                String msg = data.toString();
                                if (!"pong".equalsIgnoreCase(msg)) {
                                    handler.onMessage("(ws)" + msg);
                                }
                                webSocket.request(1);
                                return CompletableFuture.completedFuture(null);
                            }

                            @Override
                            public void onOpen(WebSocket webSocket) {
                                webSocket.request(1);
                            }

                            @Override
                            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                                return CompletableFuture.completedFuture(null);
                            }

                            @Override
                            public void onError(WebSocket webSocket, Throwable error) {
                            }
                        }).join();

                // block until close; we simply sleep and retry when websocket becomes null/closed
                while (!closed) {
                    Thread.sleep(1000);
                }
            } catch (Exception ignored) {
                sleepQuietly(2000);
            }
        }
    }

    private void startSse() {
        sseExecutor.execute(() -> {
            while (!closed) {
                try {
                    URI uri = URI.create(baseHttp + "/stream?appId=" + appId + "&userId=" + userId);
                    HttpRequest req = HttpRequest.newBuilder(uri)
                            .header("Accept", "text/event-stream")
                            .timeout(Duration.ofSeconds(30))
                            .GET()
                            .build();
                    HttpResponse<java.io.InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
                    if (resp.statusCode() / 100 != 2) {
                        sleepQuietly(2000);
                        continue;
                    }
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
                        String line;
                        while (!closed && (line = br.readLine()) != null) {
                            if (line.startsWith("data:")) {
                                String data = line.substring("data:".length()).trim();
                                if (!data.isEmpty()) handler.onMessage(data);
                            }
                        }
                    }
                } catch (Exception ignored) {
                    sleepQuietly(2000);
                }
            }
        });
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        closed = true;
        try {
            WebSocket w = ws;
            if (w != null) w.abort();
        } catch (Exception ignored) {
        }
        scheduler.shutdownNow();
        sseExecutor.shutdownNow();
    }
}

