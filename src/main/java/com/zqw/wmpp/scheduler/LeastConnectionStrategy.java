package com.zqw.wmpp.scheduler;

import com.zqw.wmpp.PusherNode;
import java.util.Comparator;
import java.util.List;

public class LeastConnectionStrategy implements SchedulerStrategy {

    @Override
    public PusherNode select(String appId, List<PusherNode> nodes) {
        if (nodes == null || nodes.isEmpty()) throw new IllegalArgumentException("nodes empty");

        return nodes.stream()
                .filter(PusherNode::isActive)
                .min(Comparator.comparingInt(PusherNode::getConnectionCount))
                .orElse(nodes.get(0));
    }
}

