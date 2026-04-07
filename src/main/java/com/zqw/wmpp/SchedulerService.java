package com.zqw.wmpp;

import com.zqw.wmpp.scheduler.LeastConnectionStrategy;
import com.zqw.wmpp.scheduler.PusherClient;
import com.zqw.wmpp.scheduler.RoundRobinStrategy;
import com.zqw.wmpp.scheduler.SchedulerStrategy;
import com.zqw.wmpp.registry.RegistryClient;
import com.zqw.wmpp.role.WmppRole;
import com.zqw.wmpp.session.SessionRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    @Value("${wmpp.scheduler.strategy:rr}")
    private String strategyName;

    @PostConstruct
    public void init() {
        // demo seed
        initAppNodes("systemA");
        initAppNodes("systemB");
        System.out.println("✅ 初始化Pusher节点池: systemA/systemB");
    }

    public void dispatchBroadcast(String appId, String message) throws Exception {

        PusherNode node = selectNode(appId);

        System.out.println("🧠 Scheduler选择节点: " + node.getPusherId() + ", strategy=" + effectiveStrategyName());

        String payload = "[" + node.getPusherId() + "] " + message;
        if (role == WmppRole.scheduler) {
            pusherClient.broadcast(node.getPusherId(), appId, payload);
        } else {
            // mono
            sessionRegistry.broadcast(appId, payload);
        }
    }

    public void dispatchUser(String appId, String userId, String msg) {
        String payload = "【私聊】" + msg;
        if (role == WmppRole.scheduler) {
            String pusherId = registryClient.lookupPusher(appId, userId);
            if (pusherId == null || pusherId.isBlank()) return;
            pusherClient.pushUser(pusherId, appId, userId, payload);
        } else {
            sessionRegistry.pushToUser(appId, userId, payload);
        }
    }

    public void dispatchUsers(String appId, List<String> userIds, String msg) {
        if (userIds == null) return;
        for (String uid : userIds) {
            if (uid == null || uid.isBlank()) continue;
            dispatchUser(appId, uid, msg);
        }
    }

    public void dispatchTopicPush(String appId, String topic, String msg,
                                  TopicService topicService) throws Exception {

        System.out.println("🧠 Topic调度开始: appId=" + appId + ", topic=" + topic);

        Set<String> users = topicService.getSubscribers(appId, topic);

        for(String uid : users){

            dispatchUser(appId, uid, "[Topic:"+topic+"] " + msg);
        }

        System.out.println("🧠 Topic调度完成");
    }

    private void initAppNodes(String appId) {
        List<PusherNode> nodes = new ArrayList<>();
        List<String> pusherIds = availablePusherIds();
        if (pusherIds.isEmpty()) {
            nodes.add(new PusherNode(appId, "Pusher-1"));
            nodes.add(new PusherNode(appId, "Pusher-2"));
            nodes.add(new PusherNode(appId, "Pusher-3"));
        } else {
            for (String pid : pusherIds) {
                nodes.add(new PusherNode(appId, pid));
            }
        }
        appNodes.put(appId, nodes);
    }

    public String selectPusherIdForNewConnection(String appId) {
        return selectNode(appId).getPusherId();
    }

    private PusherNode selectNode(String appId) {
        List<PusherNode> nodes = appNodes.get(appId);
        if (nodes == null || nodes.isEmpty()) {
            // lazy init for unknown appId (still single-node demo)
            initAppNodes(appId);
            nodes = appNodes.get(appId);
        }
        // In scheduler role, ensure nodes are consistent with configured pusher nodes.
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
        // refresh snapshot connectionCount for "least" demo
        int online = (role == WmppRole.scheduler) ? 0 : sessionRegistry.getOnlineCount(appId);
        for (PusherNode n : nodes) {
            n.setConnectionCountSnapshot(online);
        }
        return getStrategy().select(appId, nodes);
    }

    private List<String> availablePusherIds() {
        try {
            return pusherClient.getAllBaseUrls().keySet().stream().sorted().toList();
        } catch (Exception ignored) {
            return List.of();
        }
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

    private String effectiveStrategyName() {
        String name = strategyName == null ? "rr" : strategyName.trim().toLowerCase();
        return name.isBlank() ? "rr" : name;
    }
}