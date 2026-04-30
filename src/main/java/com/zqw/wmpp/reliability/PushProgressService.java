package com.zqw.wmpp.reliability;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class PushProgressService {

    public record ProgressSnapshot(long totalTarget, long pushed, long success, long failed, int percent) {}

    private static final class Counter {
        private final AtomicLong totalTarget = new AtomicLong(0);
        private final AtomicLong pushed = new AtomicLong(0);
        private final AtomicLong success = new AtomicLong(0);
        private final AtomicLong failed = new AtomicLong(0);
    }

    private final Map<String, Counter> byApp = new ConcurrentHashMap<>();

    public void recordSuccess(String appId, long target) {
        Counter c = byApp.computeIfAbsent(appId, k -> new Counter());
        long t = Math.max(1L, target);
        c.totalTarget.addAndGet(t);
        c.pushed.addAndGet(t);
        c.success.addAndGet(t);
    }

    public void recordFailure(String appId, long target) {
        Counter c = byApp.computeIfAbsent(appId, k -> new Counter());
        long t = Math.max(1L, target);
        c.totalTarget.addAndGet(t);
        c.failed.addAndGet(t);
    }

    public ProgressSnapshot snapshot(String appId) {
        Counter c = byApp.computeIfAbsent(appId, k -> new Counter());
        long total = c.totalTarget.get();
        long pushed = c.pushed.get();
        long success = c.success.get();
        long failed = c.failed.get();
        int percent = total <= 0 ? 0 : (int) Math.min(100, Math.round((pushed * 100.0) / total));
        return new ProgressSnapshot(total, pushed, success, failed, percent);
    }

    public Map<String, ProgressSnapshot> snapshotAll() {
        Map<String, ProgressSnapshot> result = new LinkedHashMap<>();
        byApp.keySet().stream().sorted().forEach(appId -> result.put(appId, snapshot(appId)));
        return result;
    }
}
