package com.zqw.wmpp.scheduler;

import com.zqw.wmpp.TopicService;
import com.zqw.wmpp.SchedulerService;
import com.zqw.wmpp.auth.AppRegistryService;
import com.zqw.wmpp.role.WmppRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
    private com.zqw.wmpp.registry.RegistryClient registryClient;

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

    @PostMapping("/users")
    public void users(@RequestParam String appId, @RequestBody UsersBody body) throws Exception {
        requireScheduler();
        requireApp(appId);
        if (body == null || body.userIds == null) return;
        schedulerService.dispatchUsers(appId, body.userIds, body.message == null ? "" : body.message);
    }

    @PostMapping("/topic")
    public void topic(@RequestParam String appId, @RequestParam String topic, @RequestParam String message) throws Exception {
        requireScheduler();
        requireApp(appId);
        schedulerService.dispatchTopicPush(appId, topic, message, topicService);
    }

    public record AssignResponse(String pusherId, String httpBaseUrl, String wsBaseUrl) {}

    @GetMapping("/assign")
    public AssignResponse assign(@RequestParam String appId, @RequestParam String userId) {
        requireScheduler();
        requireApp(appId);

        // If user already has route, stick to it
        String routed = registryClient.lookupPusher(appId, userId);
        String pusherId = (routed != null && !routed.isBlank()) ? routed : schedulerService.selectPusherIdForNewConnection(appId);

        String internalHttp = pusherClient.getBaseUrl(pusherId);
        String publicHttp = pusherPublicEndpoints.publicBaseUrl(pusherId, internalHttp);
        String ws = toWsBase(publicHttp);
        return new AssignResponse(pusherId, publicHttp, ws);
    }

    public static class UsersBody {
        public List<String> userIds;
        public String message;
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

