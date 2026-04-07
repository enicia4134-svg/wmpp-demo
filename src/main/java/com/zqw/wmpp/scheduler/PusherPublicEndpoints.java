package com.zqw.wmpp.scheduler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class PusherPublicEndpoints {

    private final Map<String, String> publicBaseUrls = new HashMap<>();

    public PusherPublicEndpoints(@Value("${wmpp.public.pusher.nodes:}") String nodes) {
        // format: Pusher-1=http://localhost:9084,Pusher-2=http://localhost:9085
        if (nodes == null) return;
        for (String part : nodes.split(",")) {
            String p = part.trim();
            if (p.isBlank()) continue;
            String[] kv = p.split("=", 2);
            if (kv.length != 2) continue;
            publicBaseUrls.put(kv[0].trim(), kv[1].trim());
        }
    }

    public String publicBaseUrl(String pusherId, String fallbackInternalBaseUrl) {
        return publicBaseUrls.getOrDefault(pusherId, fallbackInternalBaseUrl);
    }

    public Map<String, String> snapshot() {
        return Map.copyOf(publicBaseUrls);
    }
}

