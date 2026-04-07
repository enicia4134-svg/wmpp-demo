package com.zqw.wmpp.scheduler;

import com.zqw.wmpp.PusherNode;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RoundRobinStrategy implements SchedulerStrategy {

    private final Map<String, Integer> rrIndex = new ConcurrentHashMap<>();

    @Override
    public PusherNode select(String appId, List<PusherNode> nodes) {
        if (nodes == null || nodes.isEmpty()) throw new IllegalArgumentException("nodes empty");
        int idx = rrIndex.getOrDefault(appId, 0);
        PusherNode node = nodes.get(idx % nodes.size());
        rrIndex.put(appId, (idx + 1) % nodes.size());
        return node;
    }
}

