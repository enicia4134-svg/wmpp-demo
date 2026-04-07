package com.zqw.wmpp.session;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SessionCleanupJob {

    private final SessionRegistry sessionRegistry;

    private final long staleAfterMs;

    public SessionCleanupJob(
            SessionRegistry sessionRegistry,
            @Value("${wmpp.heartbeat.staleAfterMs:45000}") long staleAfterMs
    ) {
        this.sessionRegistry = sessionRegistry;
        this.staleAfterMs = staleAfterMs;
    }

    @Scheduled(fixedDelayString = "${wmpp.heartbeat.cleanupIntervalMs:5000}")
    public void cleanup() {
        sessionRegistry.closeStaleWebSockets(staleAfterMs);
    }
}

