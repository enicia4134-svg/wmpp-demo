package com.zqw.wmpp.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PusherPoolService {

    private final AtomicInteger currentInstances = new AtomicInteger(3);

    @Value("${wmpp.pusher.pool.min-instances:2}")
    private int minInstances;

    @Value("${wmpp.pusher.pool.max-instances:50}")
    private int maxInstances;

    @Value("${wmpp.pusher.pool.scale-up-threshold:800}")
    private int scaleUpThreshold;

    @Value("${wmpp.pusher.pool.scale-down-threshold:250}")
    private int scaleDownThreshold;

    public record PusherPoolStatus(
            boolean autoScaleEnabled,
            int minInstances,
            int maxInstances,
            int scaleUpThreshold,
            int scaleDownThreshold,
            int desiredInstances,
            int runningInstances,
            int totalConnections,
            double avgLoadPerInstance,
            List<Map<String, Object>> series
    ) {}

    public PusherPoolStatus snapshot(Map<String, Integer> nodeConnections) {
        int runningByNodes = Math.max(1, nodeConnections == null ? 0 : nodeConnections.size());

        int total = 0;
        if (nodeConnections != null) {
            for (Integer c : nodeConnections.values()) {
                total += c == null ? 0 : c;
            }
        }

        int current = Math.max(minInstances, Math.min(maxInstances, currentInstances.get()));
        int desired = current;

        if (total >= Math.max(scaleUpThreshold, scaleDownThreshold + 1)) {
            desired = Math.min(maxInstances, current + 1);
        } else if (total <= scaleDownThreshold) {
            desired = Math.max(minInstances, current - 1);
        }

        currentInstances.set(desired);

        int running = Math.max(desired, runningByNodes);
        double avg = running == 0 ? 0.0 : (double) total / running;

        List<Map<String, Object>> series = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 9; i >= 0; i--) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("ts", now - i * 5000L);
            point.put("connections", Math.max(0, total - (i * 2)));
            point.put("instances", running);
            series.add(point);
        }

        return new PusherPoolStatus(
                true,
                minInstances,
                maxInstances,
                scaleUpThreshold,
                scaleDownThreshold,
                desired,
                running,
                total,
                Math.round(avg * 100.0) / 100.0,
                series
        );
    }
}
