package com.zqw.wmpp.admin;

import com.zqw.wmpp.SchedulerService;
import com.zqw.wmpp.auth.AdminAuth;
import com.zqw.wmpp.auth.AppRegistryService;
import com.zqw.wmpp.registry.RegistryClient;
import com.zqw.wmpp.reliability.PushProgressService;
import com.zqw.wmpp.reliability.PushTaskQueueService;
import com.zqw.wmpp.role.WmppRole;
import com.zqw.wmpp.scheduler.PusherClient;
import com.zqw.wmpp.session.SessionRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/console")
public class GatewayAdminConsoleController {

    private final AdminAuth adminAuth;
    private final AppRegistryService appRegistryService;
    private final RegistryClient registryClient;
    private final SessionRegistry sessionRegistry;
    private final WmppRole role;
    private final PusherClient pusherClient;
    private final SchedulerService schedulerService;
    private final PusherPoolService pusherPoolService;
    private final PushProgressService pushProgressService;
    private final PushTaskQueueService pushTaskQueueService;

    @Autowired
    public GatewayAdminConsoleController(
            AdminAuth adminAuth,
            AppRegistryService appRegistryService,
            RegistryClient registryClient,
            SessionRegistry sessionRegistry,
            WmppRole role,
            PusherClient pusherClient,
            SchedulerService schedulerService,
            PusherPoolService pusherPoolService,
            PushProgressService pushProgressService,
            PushTaskQueueService pushTaskQueueService
    ) {
        this.adminAuth = adminAuth;
        this.appRegistryService = appRegistryService;
        this.registryClient = registryClient;
        this.sessionRegistry = sessionRegistry;
        this.role = role;
        this.pusherClient = pusherClient;
        this.schedulerService = schedulerService;
        this.pusherPoolService = pusherPoolService;
        this.pushProgressService = pushProgressService;
        this.pushTaskQueueService = pushTaskQueueService;
    }

    public record TenantRow(String appId, boolean allowPush, int onlineClients) {}

    public record AdminOverview(
            int tenantCount,
            int totalOnlineClients,
            int activeTenantCount,
            int nodeCount,
            int queueSize,
            String role,
            String schedulerStrategy,
            String concurrencyNote,
            List<TenantRow> tenants,
            Map<String, Integer> nodeConnections,
            Map<String, Map<String, String>> routes,
            PusherPoolService.PusherPoolStatus pusherPool,
            Map<String, PushProgressService.ProgressSnapshot> progressByApp
    ) {}

    @GetMapping("/overview")
    public AdminOverview overview(@RequestHeader(name = "X-Admin-Token", required = false) String token) {
        System.out.println("[ADMIN_CONSOLE_HIT] overview");
        adminAuth.require(token);

        List<AppRegistryService.AppInfo> apps = appRegistryService.list();
        Map<String, Map<String, String>> routes = snapshotRoutes();
        List<TenantRow> tenants = apps.stream()
                .map(app -> new TenantRow(app.appId(), app.allowPush(), onlineFor(app.appId(), routes)))
                .toList();

        int totalOnline = tenants.stream().mapToInt(TenantRow::onlineClients).sum();
        int activeTenants = (int) tenants.stream().filter(t -> t.onlineClients() > 0).count();
        Map<String, Integer> nodeConnections = aggregateNodes(routes);

        return new AdminOverview(
                apps.size(),
                totalOnline,
                activeTenants,
                Math.max(1, nodeConnections.size()),
                pushTaskQueueService.queueSize(),
                role.name(),
                schedulerService.currentStrategyName(),
                concurrencyNote(nodeConnections),
                tenants,
                nodeConnections,
                routes,
                pusherPoolService.snapshot(nodeConnections),
                pushProgressService.snapshotAll()
        );
    }

    @GetMapping("/pusher-pool")
    public PusherPoolService.PusherPoolStatus pusherPool(@RequestHeader(name = "X-Admin-Token", required = false) String token) {
        System.out.println("[ADMIN_CONSOLE_HIT] pusherPool");
        adminAuth.require(token);
        return pusherPoolService.snapshot(aggregateNodes(snapshotRoutes()));
    }

    private Map<String, Map<String, String>> snapshotRoutes() {
        if (role == WmppRole.mono) {
            Map<String, Map<String, String>> mono = new LinkedHashMap<>();
            for (AppRegistryService.AppInfo app : appRegistryService.list()) {
                int online = sessionRegistry.getOnlineCount(app.appId());
                Map<String, String> users = new LinkedHashMap<>();
                for (int i = 1; i <= online; i++) {
                    users.put("online-" + i, "local");
                }
                mono.put(app.appId(), users);
            }
            return mono;
        }
        return registryClient.snapshot();
    }

    private int onlineFor(String appId, Map<String, Map<String, String>> routes) {
        if (role == WmppRole.mono) {
            return sessionRegistry.getOnlineCount(appId);
        }
        Map<String, String> appRoutes = routes.get(appId);
        return appRoutes == null ? 0 : appRoutes.size();
    }

    private Map<String, Integer> aggregateNodes(Map<String, Map<String, String>> routes) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (role == WmppRole.mono || pusherClient == null) {
            counts.put("local", routes.values().stream().mapToInt(Map::size).sum());
            return counts;
        }
        for (String pusherId : pusherClient.getAllBaseUrls().keySet()) {
            counts.put(pusherId, 0);
        }
        for (Map<String, String> appRoutes : routes.values()) {
            if (appRoutes == null) continue;
            for (String pusherId : appRoutes.values()) {
                if (pusherId == null || pusherId.isBlank()) continue;
                counts.merge(pusherId, 1, Integer::sum);
            }
        }
        return counts;
    }

    private String concurrencyNote(Map<String, Integer> nodeConnections) {
        if (nodeConnections == null || nodeConnections.isEmpty()) {
            return "当前暂无可观测节点负载数据";
        }
        int total = nodeConnections.values().stream().mapToInt(Integer::intValue).sum();
        int nodeCount = nodeConnections.size();
        if (nodeCount <= 1) {
            return "当前为单节点承载，可通过增加 pusher 实例演示横向扩展";
        }
        return "当前已检测到 " + nodeCount + " 个承载节点，总连接数 " + total + "，可继续打开更多 Client 观察连接分布";
    }
}
