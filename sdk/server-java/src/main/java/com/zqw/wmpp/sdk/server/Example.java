package com.zqw.wmpp.sdk.server;

import java.util.List;

public class Example {
    public static void main(String[] args) throws Exception {
        String baseUrl = System.getenv().getOrDefault("WMPP_BASE_URL", "http://localhost:8082");
        WmppServerClient client = new WmppServerClient(
                baseUrl,
                "systemA",
                "systemA-secret"
        );

        System.out.println(client.broadcast("hello from java sdk"));
        System.out.println(client.pushUser("1001", "hello 1001 from java sdk"));
        System.out.println(client.pushUsers(List.of("1001", "2002"), "hello batch from java sdk"));
        System.out.println(client.pushTopic("starA", "hello topic from java sdk"));
    }
}

