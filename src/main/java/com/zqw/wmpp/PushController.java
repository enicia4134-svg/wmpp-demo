package com.zqw.wmpp;

import com.zqw.wmpp.auth.AppAuthInterceptor;
import com.zqw.wmpp.reliability.PushAuditService;
import com.zqw.wmpp.reliability.PushTaskQueueService;
import com.zqw.wmpp.role.WmppRole;
import com.zqw.wmpp.scheduler.SchedulerClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/push")
public class PushController {

    @Autowired
    private SchedulerService schedulerService;

    @Autowired
    private SchedulerClient schedulerClient;

    @Autowired
    private WmppRole role;

    @Autowired
    private PushTaskQueueService pushTaskQueueService;

    @Autowired
    private PushAuditService pushAuditService;

    @Autowired
    private com.zqw.wmpp.session.SessionRegistry sessionRegistry;

    @GetMapping("/broadcast")
    public String broadcast(@RequestParam(required = false) String message, HttpServletRequest request) throws Exception {
        requireGateway();
        String payload = message == null ? "" : message;

        System.out.println("🚀 Gateway 收到推送请求: " + payload);

        String appId = (String) request.getAttribute(AppAuthInterceptor.ATTR_APP_ID);
        long target = sessionRegistry.getOnlineCount(appId);
        String taskId = pushTaskQueueService.enqueueBroadcast(appId, payload);
        pushAuditService.record(taskId, appId, "BROADCAST", payload, List.of("ALL"), target, target, 0);
        return "queued:" + taskId;
    }

    @PostMapping("/broadcast")
    public String broadcastPost(@RequestParam(required = false) String message, HttpServletRequest request) throws Exception {
        return broadcast(message, request);
    }

    @GetMapping("/user")
    public String pushUser(String userId, @RequestParam(required = false) String message, HttpServletRequest request) throws Exception {
        requireGateway();
        String payload = message == null ? "" : message;

        String appId = (String) request.getAttribute(AppAuthInterceptor.ATTR_APP_ID);
        long target = sessionRegistry.getWebSocket(appId, userId).isPresent() ? 1L : 0L;
        String taskId = pushTaskQueueService.enqueueUser(appId, userId, payload);
        pushAuditService.record(taskId, appId, "USER", payload, List.of(userId), target, target, 0);
        return "queued-user:" + taskId;
    }

    @PostMapping("/user")
    public String pushUserPost(String userId, @RequestParam(required = false) String message, HttpServletRequest request) throws Exception {
        return pushUser(userId, message, request);
    }

    public record UsersPushRequest(List<String> userIds, String message) {}

    @PostMapping("/users")
    public String pushUsers(@RequestBody UsersPushRequest body, HttpServletRequest request) throws Exception {
        requireGateway();
        String appId = (String) request.getAttribute(AppAuthInterceptor.ATTR_APP_ID);
        if (body == null || body.userIds() == null || body.userIds().isEmpty()) return "userIds required";
        String payload = body.message() == null ? "" : body.message();
        long target = body.userIds().stream().filter(id -> sessionRegistry.getWebSocket(appId, id).isPresent()).count();
        String taskId = pushTaskQueueService.enqueueUsers(appId, body.userIds(), payload);
        pushAuditService.record(taskId, appId, "GROUP", payload, body.userIds(), target, target, 0);
        return "queued-users:" + taskId;
    }

    @GetMapping("/users")
    public String pushUsersGet(@RequestParam String userIds, @RequestParam(required = false) String message, HttpServletRequest request) throws Exception {
        requireGateway();
        String payload = message == null ? "" : message;
        String appId = (String) request.getAttribute(AppAuthInterceptor.ATTR_APP_ID);
        List<String> ids = Arrays.stream(userIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
        long target = ids.stream().filter(id -> sessionRegistry.getWebSocket(appId, id).isPresent()).count();
        String taskId = pushTaskQueueService.enqueueUsers(appId, ids, payload);
        pushAuditService.record(taskId, appId, "GROUP", payload, ids, target, target, 0);
        return "queued-users:" + taskId;
    }

    @Autowired
    private TopicService topicService;

    @GetMapping("/topic")
    public String pushTopic(String topic, @RequestParam(required = false) String message, HttpServletRequest request) throws Exception {
        requireGateway();
        String payload = message == null ? "" : message;

        String appId = (String) request.getAttribute(AppAuthInterceptor.ATTR_APP_ID);
        if (role == WmppRole.gateway) {
            schedulerClient.topic(appId, topic, payload);
        } else {
            schedulerService.dispatchTopicPush(appId, topic, payload, topicService);
        }

        return "topic push ok";
    }

    @PostMapping("/topic")
    public String pushTopicPost(String topic, @RequestParam(required = false) String message, HttpServletRequest request) throws Exception {
        return pushTopic(topic, message, request);
    }

    private void requireGateway() {
        if (role != WmppRole.gateway && role != WmppRole.mono) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not gateway role");
        }
    }
}
