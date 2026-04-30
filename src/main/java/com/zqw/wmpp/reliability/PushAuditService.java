package com.zqw.wmpp.reliability;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PushAuditService {

    public record PushAuditRow(
            String taskId,
            String appId,
            String targetType,
            String message,
            List<String> targets,
            long targetCount,
            long successCount,
            long failedCount,
            int percent,
            long createdAtMs,
            String createdAtText
    ) {}

    private static final int APP_HISTORY_LIMIT = 200;

    private final Map<String, Deque<PushAuditRow>> byApp = new ConcurrentHashMap<>();
    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public void record(
            String taskId,
            String appId,
            String targetType,
            String message,
            List<String> targets,
            long targetCount,
            long successCount,
            long failedCount
    ) {
        if (appId == null || appId.isBlank()) return;
        long total = Math.max(1L, targetCount);
        long ok = Math.max(0L, successCount);
        long fail = Math.max(0L, failedCount);
        int percent = (int) Math.min(100, Math.round((ok * 100.0) / total));
        long now = System.currentTimeMillis();

        PushAuditRow row = new PushAuditRow(
                taskId,
                appId,
                targetType == null ? "UNKNOWN" : targetType,
                message == null ? "" : message,
                targets == null ? List.of() : List.copyOf(targets),
                total,
                ok,
                fail,
                percent,
                now,
                fmt.format(Instant.ofEpochMilli(now))
        );

        Deque<PushAuditRow> q = byApp.computeIfAbsent(appId, k -> new ArrayDeque<>());
        synchronized (q) {
            q.addFirst(row);
            while (q.size() > APP_HISTORY_LIMIT) {
                q.removeLast();
            }
        }
    }

    public List<PushAuditRow> latest(String appId, int limit) {
        if (appId == null || appId.isBlank()) return List.of();
        Deque<PushAuditRow> q = byApp.get(appId);
        if (q == null || q.isEmpty()) return List.of();
        int n = Math.max(1, Math.min(100, limit));
        List<PushAuditRow> out = new ArrayList<>(n);
        synchronized (q) {
            int i = 0;
            for (PushAuditRow row : q) {
                out.add(row);
                i++;
                if (i >= n) break;
            }
        }
        return out;
    }
}
