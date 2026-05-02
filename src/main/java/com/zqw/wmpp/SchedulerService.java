package com.zqw.wmpp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zqw.wmpp.registry.RegistryClient;
import com.zqw.wmpp.reliability.PushProgressService;
import com.zqw.wmpp.role.WmppRole;
import com.zqw.wmpp.scheduler.LeastConnectionStrategy;
import com.zqw.wmpp.scheduler.PusherClient;
import com.zqw.wmpp.scheduler.RoundRobinStrategy;
import com.zqw.wmpp.scheduler.SchedulerClient;
import com.zqw.wmpp.scheduler.SchedulerStrategy;
import com.zqw.wmpp.session.SessionRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class SchedulerService {

    private final Map<String, List<PusherNode>> appNodes = new HashMap<>();
    private SchedulerStrategy strategy;

    @Autowired
    private SessionRegistry sessionRegistry;
    @Autowired
    private WmppRole role;
    @Autowired
    private RegistryClient registryClient;
    @Autowired
    private PusherClient pusherClient;
    @Autowired
    private SchedulerClient schedulerClient;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PushProgressService pushProgressService;

    @Value("${wmpp.scheduler.strategy:rr}")
    private String strategyName;
    @Value("${wmpp.push.retry.max-attempts:3}")
    private int pushRetryMaxAttempts;
    @Value("${wmpp.push.retry.base-delay-ms:200}")
    private long pushRetryBaseDelayMs;

    @PostConstruct
    public void init() {
        initAppNodes("systemA");
        initAppNodes("systemB");
        System.out.println("初始化Pusher节点池: systemA/systemB");
    }

    public void dispatchBroadcast(String appId, String message) throws Exception {
        if (role == WmppRole.gateway) {
            try {
                schedulerClient.broadcast(appId, message);
                System.out.println("[SCHEDULER_CLIENT_BROADCAST] appId=" + appId + ", msg=" + summarize(message));
                return;
            } catch (Exception ex) {
                System.out.println("[SCHEDULER_CLIENT_BROADCAST_FAIL] appId=" + appId + ", err=" + ex.getMessage());
                throw ex;
            }
        }

        if (role == WmppRole.scheduler) {
            List<String> pusherIds = availablePusherIds();
            if (pusherIds.isEmpty()) throw new IllegalStateException("No available pusher nodes for broadcast");
            String payload = buildMessageEnvelope("broadcast", message);
            for (String pusherId : pusherIds) {
                withRetry("broadcast", appId, pusherId, () -> pusherClient.broadcast(pusherId, appId, payload));
            }
            pushProgressService.recordSuccess(appId, pusherIds.size());
            System.out.println("Broadcast fanout completed: appId=" + appId + ", nodes=" + pusherIds.size());
            return;
        }

        String payload = buildMessageEnvelope("broadcast", message);
        sessionRegistry.broadcast(appId, payload);
        pushProgressService.recordSuccess(appId, Math.max(1, sessionRegistry.getOnlineCount(appId)));
    }

    public void dispatchUser(String appId, String userId, String msg) {
        String payload = buildMessageEnvelope("notification", "【通知】" + msg);
        try {
            if (role == WmppRole.gateway) {
                try {
                    schedulerClient.user(appId, userId, msg);
                    System.out.println("[SCHEDULER_CLIENT_USER] appId=" + appId + ", userId=" + userId + ", msg=" + summarize(msg));
                } catch (Exception ex) {
                    System.out.println("[SCHEDULER_CLIENT_USER_FAIL] appId=" + appId + ", userId=" + userId + ", err=" + ex.getMessage());
                    throw ex;
                }
                return;
            }
            if (role == WmppRole.scheduler) {
                String pusherId = registryClient.lookupPusher(appId, userId);
                if (pusherId == null || pusherId.isBlank()) {
                    System.out.println("[DISPATCH_USER_ROUTE_MISS] appId=" + appId + ", userId=" + userId + ", msg=" + summarize(payload));
                    pushProgressService.recordFailure(appId, 1);
                    return;
                }
                withRetry("user", appId, pusherId, () -> pusherClient.pushUser(pusherId, appId, userId, payload));
            } else {
                sessionRegistry.pushToUser(appId, userId, payload);
            }
            pushProgressService.recordSuccess(appId, 1);
            System.out.println("[DISPATCH_USER] appId=" + appId + ", userId=" + userId + ", payload=" + summarize(payload));
        } catch (Exception ex) {
            pushProgressService.recordFailure(appId, 1);
            throw new RuntimeException(ex);
        }
    }

    private String summarize(String message) {
        if (message == null) return "";
        String t = message.replace('\n', ' ').trim();
        return t.length() <= 80 ? t : t.substring(0, 80) + "...";
    }

    public void dispatchUsers(String appId, List<String> userIds, String msg) {
        if (userIds == null) return;
        if (role == WmppRole.gateway) {
            try {
                schedulerClient.users(appId, userIds, msg);
                System.out.println("[SCHEDULER_CLIENT_USERS] appId=" + appId + ", count=" + userIds.size() + ", msg=" + summarize(msg));
                return;
            } catch (Exception ex) {
                System.out.println("[SCHEDULER_CLIENT_USERS_FAIL] appId=" + appId + ", err=" + ex.getMessage());
                throw ex;
            }
        }
        for (String uid : userIds) {
            if (uid == null || uid.isBlank()) continue;
            try {
                dispatchUser(appId, uid, msg);
            } catch (Exception ex) {
                System.out.println("[DISPATCH_USERS_ITEM_FAIL] appId=" + appId + ", userId=" + uid + ", err=" + ex.getMessage());
            }
        }
    }

    public void dispatchTopicPush(String appId, String topic, String msg, TopicService topicService) throws Exception {
        System.out.println("Topic调度开始: appId=" + appId + ", topic=" + topic);
        Set<String> users = topicService.getSubscribers(appId, topic);
        for (String uid : users) {
            dispatchUser(appId, uid, "[Topic:" + topic + "] " + msg);
        }
        System.out.println("Topic调度完成");
    }

    private void initAppNodes(String appId) {
        List<PusherNode> nodes = new ArrayList<>();
        List<String> pusherIds = availablePusherIds();
        if (pusherIds.isEmpty()) {
            nodes.add(new PusherNode(appId, "Pusher-1"));
            nodes.add(new PusherNode(appId, "Pusher-2"));
            nodes.add(new PusherNode(appId, "Pusher-3"));
        } else {
            for (String pid : pusherIds) nodes.add(new PusherNode(appId, pid));
        }
        appNodes.put(appId, nodes);
    }

    public String selectPusherIdForNewConnection(String appId) {
        return selectNode(appId).getPusherId();
    }

    private PusherNode selectNode(String appId) {
        List<PusherNode> nodes = appNodes.get(appId);
        if (nodes == null || nodes.isEmpty()) {
            initAppNodes(appId);
            nodes = appNodes.get(appId);
        }
        if (role == WmppRole.scheduler) {
            List<String> allowed = availablePusherIds();
            if (!allowed.isEmpty()) {
                nodes.removeIf(n -> !allowed.contains(n.getPusherId()));
                if (nodes.isEmpty()) {
                    initAppNodes(appId);
                    nodes = appNodes.get(appId);
                    nodes.removeIf(n -> !allowed.contains(n.getPusherId()));
                }
            }
        }
        if (role == WmppRole.scheduler) {
            Map<String, Integer> counts = registryClient.pusherCounts(appId);
            for (PusherNode n : nodes) n.setConnectionCountSnapshot(counts.getOrDefault(n.getPusherId(), 0));
        } else {
            int online = sessionRegistry.getOnlineCount(appId);
            for (PusherNode n : nodes) n.setConnectionCountSnapshot(online);
        }
        return getStrategy().select(appId, nodes);
    }

    private List<String> availablePusherIds() {
        try { return pusherClient.getAllBaseUrls().keySet().stream().sorted().toList(); } catch (Exception ignored) { return List.of(); }
    }

    private SchedulerStrategy getStrategy() {
        if (strategy != null) return strategy;
        String name = strategyName == null ? "rr" : strategyName.trim().toLowerCase();
        switch (name) {
            case "least":
            case "leastconn":
            case "least_connection":
                strategy = new LeastConnectionStrategy();
                return strategy;
            case "rr":
            case "roundrobin":
            default:
                strategy = new RoundRobinStrategy();
                return strategy;
        }
    }

    public String currentStrategyName() { return effectiveStrategyName(); }
    private String effectiveStrategyName() { String name = strategyName == null ? "rr" : strategyName.trim().toLowerCase(); return name.isBlank() ? "rr" : name; }

    private String buildMessageEnvelope(String type, String payload) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("msgId", UUID.randomUUID().toString());
            event.put("type", type == null ? "message" : type);
            event.put("timestamp", System.currentTimeMillis());
            event.put("payload", payload == null ? "" : payload);
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            return payload == null ? "" : payload;
        }
    }

    private void withRetry(String op, String appId, String pusherId, ThrowingRunnable action) {
        int attempts = Math.max(1, pushRetryMaxAttempts);
        RuntimeException last = null;
        for (int i = 1; i <= attempts; i++) {
            try {
                action.run();
                return;
            } catch (Exception ex) {
                last = (ex instanceof RuntimeException re) ? re : new RuntimeException(ex);
                if (i >= attempts) break;
                long delay = nextRetryDelayMs(i);
                System.out.println("push retry scheduled: op=" + op + ", appId=" + appId + ", pusherId=" + pusherId + ", attempt=" + i + "/" + attempts + ", delayMs=" + delay + ", err=" + ex.getMessage());
                sleepQuietly(delay);
            }
        }
        throw new RuntimeException("push failed after retries: op=" + op + ", appId=" + appId + ", pusherId=" + pusherId, last);
    }

    private long nextRetryDelayMs(int attempt) {
        long base = Math.max(1L, pushRetryBaseDelayMs);
        long exp = Math.min(5000L, base * (1L << Math.max(0, attempt - 1)));
        long jitter = (long) (Math.random() * Math.max(50L, exp / 5));
        return exp + jitter;
    }

    private void sleepQuietly(long ms) { try { Thread.sleep(Math.max(1L, ms)); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); } }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Exception; }
}
