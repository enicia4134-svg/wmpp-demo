package com.zqw.wmpp.tenant;

import com.zqw.wmpp.auth.AppAuthInterceptor;
import com.zqw.wmpp.auth.AppRegistryService;
import com.zqw.wmpp.registry.RegistryClient;
import com.zqw.wmpp.reliability.PushAuditService;
import com.zqw.wmpp.reliability.PushProgressService;
import com.zqw.wmpp.role.WmppRole;
import com.zqw.wmpp.scheduler.PusherClient;
import com.zqw.wmpp.session.SessionRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/tenant")
public class TenantConsoleController {

    @Autowired
    private AppRegistryService appRegistryService;

    @Autowired
    private SessionRegistry sessionRegistry;

    @Autowired
    private RegistryClient registryClient;

    @Autowired
    private WmppRole role;

    @Autowired
    private PusherClient pusherClient;


    @Autowired
    private PushProgressService pushProgressService;

    @Autowired
    private PushAuditService pushAuditService;

    public record TenantSummary(
            String appId,
            boolean allowPush,
            String secretKey,
            int onlineClients,
            Map<String, Integer> nodeConnections,
            int nodeCount,
            String role,
            boolean isolated
    ) {}


    @GetMapping("/me")
    public TenantSummary me(HttpServletRequest request) {
        String appId = (String) request.getAttribute(AppAuthInterceptor.ATTR_APP_ID);
        AppRegistryService.AppInfo app = appRegistryService.find(appId);
        Map<String, Integer> nodeConnections = nodeConnections(appId);
        int onlineClients = nodeConnections.values().stream().mapToInt(Integer::intValue).sum();
        if (role == WmppRole.mono) {
            onlineClients = sessionRegistry.getOnlineCount(appId);
        }
        return new TenantSummary(
                app == null ? appId : app.appId(),
                app != null && app.allowPush(),
                app == null ? "" : app.secretKey(),
                onlineClients,
                nodeConnections,
                Math.max(1, nodeConnections.size()),
                role.name(),
                true
        );
    }

    @GetMapping("/routes")
    public Map<String, String> routes(HttpServletRequest request) {
        String appId = (String) request.getAttribute(AppAuthInterceptor.ATTR_APP_ID);
        if (role == WmppRole.mono) {
            Map<String, String> mono = new LinkedHashMap<>();
            mono.put("local", "mono-inline-registry");
            return mono;
        }
        Map<String, Map<String, String>> snapshot = registryClient.snapshot();
        Map<String, String> appRoutes = snapshot.get(appId);
        return appRoutes == null ? Map.of() : appRoutes;
    }

    @GetMapping("/sdk-config")
    public Map<String, Object> sdkConfig(HttpServletRequest request) {
        String appId = (String) request.getAttribute(AppAuthInterceptor.ATTR_APP_ID);
        AppRegistryService.AppInfo app = appRegistryService.find(appId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("appId", appId);
        result.put("secretKey", app == null ? "" : app.secretKey());
        result.put("serverSdkClass", "SMSetup");
        result.put("clientSdkClass", "SMClient");
        result.put("serverJsPackage", "sdk/server-js/sm_setup.js");
        result.put("serverPythonPackage", "sdk/server-python/wmpp_server_sdk.py");
        result.put("clientJsPackage", "src/main/resources/static/wmpp-sdk.js");
        result.put("broadcastApi", "/push/broadcast");
        result.put("userApi", "/push/user");
        result.put("usersApi", "/push/users");
        result.put("topicApi", "/push/topic");
        return result;
    }

    public record TenantRealtime(
            String appId,
            PushProgressService.ProgressSnapshot progress,
            List<PushAuditService.PushAuditRow> latestMessages,
            long ts
    ) {}

    @GetMapping("/progress")
    public PushProgressService.ProgressSnapshot progress(HttpServletRequest request) {
        String appId = (String) request.getAttribute(AppAuthInterceptor.ATTR_APP_ID);
        return pushProgressService.snapshot(appId);
    }

    @GetMapping("/realtime")
    public TenantRealtime realtime(HttpServletRequest request) {
        String appId = (String) request.getAttribute(AppAuthInterceptor.ATTR_APP_ID);
        return new TenantRealtime(
                appId,
                pushProgressService.snapshot(appId),
                pushAuditService.latest(appId, 30),
                System.currentTimeMillis()
        );
    }

    private Map<String, Integer> nodeConnections(String appId) {
        try {
            if (role == WmppRole.mono) {
                return Map.of("local", sessionRegistry.getOnlineCount(appId));
            }
            Map<String, Integer> counts = registryClient.pusherCounts(appId);
            if (counts == null || counts.isEmpty()) {
                Map<String, Integer> fallback = new LinkedHashMap<>();
                for (String pusherId : pusherClient.getAllBaseUrls().keySet()) {
                    fallback.put(pusherId, 0);
                }
                return fallback;
            }
            return counts;
        } catch (Exception ignored) {
            return Map.of();
        }
    }
}
