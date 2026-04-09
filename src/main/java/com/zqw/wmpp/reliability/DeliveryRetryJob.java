package com.zqw.wmpp.reliability;

import com.zqw.wmpp.session.SessionRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DeliveryRetryJob {

    private final DeliveryTracker deliveryTracker;
    private final SessionRegistry sessionRegistry;

    public DeliveryRetryJob(DeliveryTracker deliveryTracker, SessionRegistry sessionRegistry) {
        this.deliveryTracker = deliveryTracker;
        this.sessionRegistry = sessionRegistry;
    }

    @Scheduled(fixedDelayString = "${wmpp.delivery.retry.scan-interval-ms:1000}")
    public void retryPending() {
        long now = System.currentTimeMillis();
        for (DeliveryTracker.DeliveryEvent evt : deliveryTracker.dueRetries(now)) {
            sessionRegistry.pushToUser(evt.appId(), evt.userId(), evt.payload());
        }
        deliveryTracker.pruneTerminalStates();
    }
}
