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

    public String publicBaseUrlOrDetect(String pusherId, String fallbackInternalBaseUrl, String forwardedProto, String forwardedHost, String requestScheme, String requestHost) {
        String configured = publicBaseUrls.get(pusherId);
        if (configured != null && !configured.isBlank()) {
            return configured;
        }

        String host = firstNonBlank(forwardedHost, requestHost, fallbackHostFromUrl(fallbackInternalBaseUrl));
        String proto = firstNonBlank(forwardedProto, schemeFromUrl(fallbackInternalBaseUrl), requestScheme);
        if (host == null || host.isBlank()) {
            return fallbackInternalBaseUrl;
        }
        if (proto == null || proto.isBlank()) {
            proto = fallbackInternalBaseUrl != null && fallbackInternalBaseUrl.startsWith("https://") ? "https" : "http";
        }
        return proto + "://" + host;
    }

    public Map<String, String> snapshot() {
        return Map.copyOf(publicBaseUrls);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private static String schemeFromUrl(String url) {
        if (url == null) return null;
        if (url.startsWith("https://")) return "https";
        if (url.startsWith("http://")) return "http";
        return null;
    }

    private static String fallbackHostFromUrl(String url) {
        if (url == null) return null;
        int idx = url.indexOf("//");
        if (idx < 0) return null;
        String rest = url.substring(idx + 2);
        int slash = rest.indexOf('/');
        return slash >= 0 ? rest.substring(0, slash) : rest;
    }
}

