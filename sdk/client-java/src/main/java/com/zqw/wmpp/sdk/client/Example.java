package com.zqw.wmpp.sdk.client;

public class Example {
    public static void main(String[] args) throws Exception {
        String baseHttp = System.getenv().getOrDefault("WMPP_HTTP", "http://localhost:8084");
        String baseWs = System.getenv().getOrDefault("WMPP_WS", "ws://localhost:8084");
        String appId = System.getenv().getOrDefault("WMPP_APP_ID", "systemA");
        String userId = System.getenv().getOrDefault("WMPP_USER_ID", "1001");

        try (WmppClient client = new WmppClient(baseHttp, baseWs, appId, userId, System.out::println)) {
            client.start();
            System.out.println("Client started. Ctrl+C to exit.");
            Thread.sleep(Long.MAX_VALUE);
        }
    }
}

