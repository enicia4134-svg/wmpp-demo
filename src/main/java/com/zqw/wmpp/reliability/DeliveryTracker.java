package com.zqw.wmpp.reliability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DeliveryTracker {

    public record DeliveryEvent(String msgId, String appId, String userId, String payload, int attempts, long nextRetryAtMs) {}

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_ACKED = "ACKED";
    private static final String STATUS_FAILED = "FAILED";

    private final Map<String, DeliveryState> pendingByMsgId = new ConcurrentHashMap<>();
    private final Clock clock = Clock.systemUTC();

    @Value("${wmpp.delivery.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${wmpp.delivery.retry.base-delay-ms:500}")
    private long baseDelayMs;

    public void trackSend(String appId, String userId, String msgId, String payload) {
        if (blank(appId) || blank(userId) || blank(msgId)) return;
        pendingByMsgId.compute(msgId, (k, old) -> {
            long now = clock.millis();
            if (old == null) {
                return new DeliveryState(msgId, appId, userId, payload, STATUS_PENDING, 1, now + nextDelayMs(1));
            }
            if (!STATUS_PENDING.equals(old.status)) return old;
            old.attempts += 1;
            old.nextRetryAtMs = now + nextDelayMs(old.attempts);
            if (old.payload == null || old.payload.isBlank()) {
                old.payload = payload;
            }
            return old;
        });
    }

    public boolean markAcked(String appId, String userId, String msgId) {
        if (blank(appId) || blank(userId) || blank(msgId)) return false;
        DeliveryState state = pendingByMsgId.get(msgId);
        if (state == null) return false;
        if (!appId.equals(state.appId) || !userId.equals(state.userId)) return false;
        state.status = STATUS_ACKED;
        return true;
    }

    public List<DeliveryEvent> dueRetries(long nowMs) {
        List<DeliveryEvent> due = new ArrayList<>();
        for (DeliveryState st : pendingByMsgId.values()) {
            if (!STATUS_PENDING.equals(st.status)) continue;
            if (st.nextRetryAtMs > nowMs) continue;
            if (st.attempts >= Math.max(1, maxAttempts)) {
                st.status = STATUS_FAILED;
                continue;
            }
            due.add(new DeliveryEvent(st.msgId, st.appId, st.userId, st.payload, st.attempts, st.nextRetryAtMs));
        }
        return due;
    }

    public void pruneTerminalStates() {
        pendingByMsgId.entrySet().removeIf(e -> {
            String s = e.getValue().status;
            return STATUS_ACKED.equals(s) || STATUS_FAILED.equals(s);
        });
    }

    private long nextDelayMs(int attempt) {
        long base = Math.max(50L, baseDelayMs);
        long exp = Math.min(10_000L, base * (1L << Math.max(0, attempt - 1)));
        long jitter = (long) (Math.random() * Math.max(50L, exp / 5));
        return exp + jitter;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static final class DeliveryState {
        private final String msgId;
        private final String appId;
        private final String userId;
        private String payload;
        private volatile String status;
        private volatile int attempts;
        private volatile long nextRetryAtMs;

        private DeliveryState(String msgId, String appId, String userId, String payload, String status, int attempts, long nextRetryAtMs) {
            this.msgId = msgId;
            this.appId = appId;
            this.userId = userId;
            this.payload = payload;
            this.status = status;
            this.attempts = attempts;
            this.nextRetryAtMs = nextRetryAtMs;
        }
    }
}
