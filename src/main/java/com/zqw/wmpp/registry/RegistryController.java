package com.zqw.wmpp.registry;

import com.zqw.wmpp.role.WmppRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/registry")
public class RegistryController {

    @Autowired
    private WmppRole role;

    // Map<AppId, Map<UserId, PusherId>>
    private final Map<String, Map<String, String>> routes = new ConcurrentHashMap<>();

    public record RegisterRequest(String appId, String userId, String pusherId) {}

    @PostMapping("/register")
    public void register(@RequestBody RegisterRequest body) {
        requireRegistryRole();
        if (body == null || blank(body.appId()) || blank(body.userId()) || blank(body.pusherId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing fields");
        }
        routes.computeIfAbsent(body.appId(), k -> new ConcurrentHashMap<>()).put(body.userId(), body.pusherId());
    }

    @PostMapping("/unregister")
    public void unregister(@RequestBody RegisterRequest body) {
        requireRegistryRole();
        if (body == null || blank(body.appId()) || blank(body.userId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing fields");
        }
        Map<String, String> appMap = routes.get(body.appId());
        if (appMap != null) {
            if (blank(body.pusherId())) {
                appMap.remove(body.userId());
            } else {
                appMap.computeIfPresent(body.userId(), (uid, currentPusherId) ->
                        currentPusherId.equals(body.pusherId()) ? null : currentPusherId);
            }
            if (appMap.isEmpty()) routes.remove(body.appId());
        }
    }

    @GetMapping("/lookup")
    public String lookup(@RequestParam String appId, @RequestParam String userId) {
        requireRegistryRole();
        Map<String, String> appMap = routes.get(appId);
        if (appMap == null) return "";
        return appMap.getOrDefault(userId, "");
    }

    @GetMapping("/snapshot")
    public Map<String, Map<String, String>> snapshot() {
        requireRegistryRole();
        return routes;
    }

    @GetMapping("/pusher-counts")
    public Map<String, Integer> pusherCounts(@RequestParam String appId) {
        requireRegistryRole();
        Map<String, Integer> counts = new ConcurrentHashMap<>();
        Map<String, String> appMap = routes.get(appId);
        if (appMap == null || appMap.isEmpty()) return counts;
        for (String pusherId : appMap.values()) {
            if (blank(pusherId)) continue;
            counts.merge(pusherId, 1, Integer::sum);
        }
        return counts;
    }

    private void requireRegistryRole() {
        if (role != WmppRole.registry && role != WmppRole.mono) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not registry role");
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}

