package com.zqw.wmpp;

import com.zqw.wmpp.auth.AppAuthInterceptor;
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

    @GetMapping("/broadcast")
    public String broadcast(@RequestParam(required = false) String message, HttpServletRequest request) throws Exception {
        requireGateway();
        String payload = message == null ? "" : message;

        System.out.println("🚀 Gateway 收到推送请求: " + payload);

        String appId = (String) request.getAttribute(AppAuthInterceptor.ATTR_APP_ID);
        if (role == WmppRole.gateway) {
            schedulerClient.broadcast(appId, payload);
        } else {
            schedulerService.dispatchBroadcast(appId, payload);
        }

        return "success";
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
        if (role == WmppRole.gateway) {
            schedulerClient.user(appId, userId, payload);
        } else {
            schedulerService.dispatchUser(appId, userId, payload);
        }

        return "user push ok";
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
        if (role == WmppRole.gateway) {
            schedulerClient.users(appId, body.userIds(), payload);
        } else {
            schedulerService.dispatchUsers(appId, body.userIds(), payload);
        }
        return "users push ok";
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
        if (role == WmppRole.gateway) {
            schedulerClient.users(appId, ids, payload);
        } else {
            schedulerService.dispatchUsers(appId, ids, payload);
        }
        return "users push ok";
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
