package com.zqw.wmpp.scheduler;

import com.zqw.wmpp.TopicService;
import com.zqw.wmpp.SchedulerService;
import com.zqw.wmpp.auth.AppRegistryService;
import com.zqw.wmpp.registry.RegistryClient;
import com.zqw.wmpp.role.WmppRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/scheduler")
public class SchedulerController {

    @Autowired
    private WmppRole role;
    @Autowired
    private SchedulerService schedulerService;
    @Autowired
    private TopicService topicService;
    @Autowired
    private AppRegistryService appRegistryService;
    @Autowired
    private PusherClient pusherClient;
    @Autowired
    private PusherPublicEndpoints pusherPublicEndpoints;
    @Autowired
    private RegistryClient registryClient;

    @PostMapping("/broadcast")
    public void broadcast(@RequestParam String appId, @RequestParam String message) throws Exception {
        requireScheduler();
        requireApp(appId);
        schedulerService.dispatchBroadcast(appId, message);
    }

    @PostMapping("/user")
    public void user(@RequestParam String appId, @RequestParam String userId, @RequestParam String message) throws Exception {
        requireScheduler();
        requireApp(appId);
        schedulerService.dispatchUser(appId, userId, message);
    }

    public record UsersBody(List<String> userIds, String message) {}
    public record UsersResponse(String status, int total, int success, int failed, List<String> failedUsers) {}

    @PostMapping("/users")
    public UsersResponse users(@RequestParam String appId, @RequestBody UsersBody body) throws Exception {
        requireScheduler();
        requireApp(appId);
        if (body == null || body.userIds == null || body.userIds.isEmpty()) {
            return new UsersResponse("empty", 0, 0, 0, List.of());
        }

        int success = 0;
        int failed = 0;
        List<String> failedUsers = new ArrayList<>();
        for (String uid : body.userIds) {
            if (uid == null || uid.isBlank()) continue;
            try {
                schedulerService.dispatchUser(appId, uid, body.message == null ? "" : body.message);
                success++;
            } catch (Exception ex) {
                failed++;
                failedUsers.add(uid);
                System.out.println("[SCHEDULER_USERS_ITEM_FAIL] appId=" + appId + ", userId=" + uid + ", err=" + ex.getMessage());
            }
        }
        return new UsersResponse("ok", body.userIds.size(), success, failed, failedUsers);
    }

    @PostMapping("/topic")
    public void topic(@RequestParam String appId, @RequestParam String topic, @RequestParam String message) throws Exception {
        requireScheduler();
        requireApp(appId);
        schedulerService.dispatchTopicPush(appId, topic, message, topicService);
    }

    public record AssignResponse(String pusherId, String httpBaseUrl, String wsBaseUrl) {}

    @GetMapping("/assign")
    public AssignResponse assign(
            @RequestParam String appId,
            @RequestParam String userId,
            @RequestHeader(name = "X-Forwarded-Proto", required = false) String forwardedProto,
            @RequestHeader(name = "X-Forwarded-Host", required = false) String forwardedHost,
            @RequestHeader(name = "Host", required = false) String hostHeader
    ) {
        requireScheduler();
        requireApp(appId);
        String routed = registryClient.lookupPusher(appId, userId);
        String pusherId = (routed != null && !routed.isBlank()) ? routed : schedulerService.selectPusherIdForNewConnection(appId);
        String internalHttp = pusherClient.getBaseUrl(pusherId);
        String publicHttp = pusherPublicEndpoints.publicBaseUrlOrDetect(pusherId, internalHttp, forwardedProto, forwardedHost, null, hostHeader);
        String ws = toWsBase(publicHttp);
        return new AssignResponse(pusherId, publicHttp, ws);
    }

    private void requireScheduler() {
        if (role != WmppRole.scheduler && role != WmppRole.mono) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not scheduler role");
        }
    }

    private void requireApp(String appId) {
        if (appRegistryService.find(appId) == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid appId");
        }
    }

    private static String toWsBase(String httpBase) {
        if (httpBase == null) return null;
        if (httpBase.startsWith("https://")) return "wss://" + httpBase.substring("https://".length());
        if (httpBase.startsWith("http://")) return "ws://" + httpBase.substring("http://".length());
        return httpBase;
    }
}
