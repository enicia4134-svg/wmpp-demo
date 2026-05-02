package com.zqw.wmpp.registry;

import com.zqw.wmpp.role.WmppRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/registry")
public class RegistryController {

    @Autowired
    private WmppRole role;

    private record RouteEntry(String pusherId, long lastSeenAtMs) {}

    // Map<AppId, Map<UserId, RouteEntry>>
    private final Map<String, Map<String, RouteEntry>> routes = new ConcurrentHashMap<>();

    @Value("${wmpp.registry.route-ttl-ms:1800000}")
    private long routeTtlMs;

    public record RegisterRequest(String appId, String userId, String pusherId) {}

    @PostMapping("/register")
    public void register(@RequestBody RegisterRequest body) {
        requireRegistryRole();
        if (body == null || blank(body.appId()) || blank(body.userId()) || blank(body.pusherId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing fields");
        }
        routes.computeIfAbsent(body.appId(), k -> new ConcurrentHashMap<>())
                .put(body.userId(), new RouteEntry(body.pusherId(), System.currentTimeMillis()));
    }

    @PostMapping("/unregister")
    public void unregister(@RequestBody RegisterRequest body) {
        requireRegistryRole();
        if (body == null || blank(body.appId()) || blank(body.userId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing fields");
        }
        Map<String, RouteEntry> appMap = routes.get(body.appId());
        if (appMap != null) {
            if (blank(body.pusherId())) {
                appMap.remove(body.userId());
            } else {
                appMap.computeIfPresent(body.userId(), (uid, current) ->
                        current != null && body.pusherId().equals(current.pusherId()) ? null : current);
            }
            if (appMap.isEmpty()) routes.remove(body.appId());
        }
    }

    @GetMapping("/lookup")
    public String lookup(@RequestParam String appId, @RequestParam String userId) {
        requireRegistryRole();
        purgeExpired();
        Map<String, RouteEntry> appMap = routes.get(appId);
        if (appMap == null) return "";
        RouteEntry entry = appMap.get(userId);
        if (entry == null || expired(entry)) {
            if (entry != null) appMap.remove(userId);
            if (appMap.isEmpty()) routes.remove(appId);
            return "";
        }
        return entry.pusherId() == null ? "" : entry.pusherId();
    }

    @GetMapping("/snapshot")
    public Map<String, Map<String, String>> snapshot() {
        requireRegistryRole();
        purgeExpired();
        Map<String, Map<String, String>> out = new ConcurrentHashMap<>();
        for (var appEntry : routes.entrySet()) {
            Map<String, String> app = new ConcurrentHashMap<>();
            for (var userEntry : appEntry.getValue().entrySet()) {
                RouteEntry entry = userEntry.getValue();
                if (entry != null && !expired(entry)) {
                    app.put(userEntry.getKey(), entry.pusherId());
                }
            }
            if (!app.isEmpty()) out.put(appEntry.getKey(), app);
        }
        return out;
    }

    @GetMapping("/pusher-counts")
    public Map<String, Integer> pusherCounts(@RequestParam String appId) {
        requireRegistryRole();
        purgeExpired();
        Map<String, Integer> counts = new ConcurrentHashMap<>();
        Map<String, RouteEntry> appMap = routes.get(appId);
        if (appMap == null || appMap.isEmpty()) return counts;
        for (RouteEntry entry : appMap.values()) {
            if (entry == null || expired(entry) || blank(entry.pusherId())) continue;
            counts.merge(entry.pusherId(), 1, Integer::sum);
        }
        return counts;
    }

    @Scheduled(fixedDelayString = "${wmpp.registry.route-cleanup-ms:60000}")
    public void cleanupExpiredRoutes() {
        requireRegistryRole();
        purgeExpired();
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<String, Map<String, RouteEntry>>> it = routes.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Map<String, RouteEntry>> appEntry = it.next();
            Map<String, RouteEntry> appMap = appEntry.getValue();
            if (appMap == null) {
                it.remove();
                continue;
            }
            appMap.entrySet().removeIf(e -> e.getValue() == null || now - e.getValue().lastSeenAtMs() > routeTtlMs || blank(e.getValue().pusherId()));
            if (appMap.isEmpty()) it.remove();
        }
    }

    private boolean expired(RouteEntry entry) {
        return entry == null || System.currentTimeMillis() - entry.lastSeenAtMs() > routeTtlMs;
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
