package com.zqw.wmpp.reliability;

import com.zqw.wmpp.SchedulerService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Service
public class PushTaskQueueService {

    public enum TaskType { BROADCAST, USER, USERS }

    public record PushTask(
            String taskId,
            TaskType type,
            String appId,
            String message,
            String userId,
            List<String> userIds
    ) {}

    private final BlockingQueue<PushTask> queue = new LinkedBlockingQueue<>(50_000);

    @Autowired
    private SchedulerService schedulerService;

    @Autowired
    private PushProgressService pushProgressService;

    @PostConstruct
    public void init() {
        System.out.println("PushTaskQueueService initialized, queue capacity=50000");
    }

    public String enqueueBroadcast(String appId, String message) {
        return enqueue(new PushTask(UUID.randomUUID().toString(), TaskType.BROADCAST, appId, message, null, null));
    }

    public String enqueueUser(String appId, String userId, String message) {
        return enqueue(new PushTask(UUID.randomUUID().toString(), TaskType.USER, appId, message, userId, null));
    }

    public String enqueueUsers(String appId, List<String> userIds, String message) {
        return enqueue(new PushTask(UUID.randomUUID().toString(), TaskType.USERS, appId, message, null, userIds));
    }

    private String enqueue(PushTask task) {
        boolean ok = queue.offer(task);
        if (!ok) {
            pushProgressService.recordFailure(task.appId(), 1);
            throw new IllegalStateException("push task queue is full");
        }
        return task.taskId();
    }

    @Scheduled(fixedDelayString = "${wmpp.push.queue.worker-delay-ms:20}")
    public void consume() {
        for (int i = 0; i < 200; i++) {
            PushTask task = queue.poll();
            if (task == null) return;
            try {
                switch (task.type()) {
                    case BROADCAST -> schedulerService.dispatchBroadcast(task.appId(), task.message());
                    case USER -> schedulerService.dispatchUser(task.appId(), task.userId(), task.message());
                    case USERS -> schedulerService.dispatchUsers(task.appId(), task.userIds(), task.message());
                }
            } catch (Exception ex) {
                long target = task.type() == TaskType.USERS && task.userIds() != null ? Math.max(1, task.userIds().size()) : 1;
                pushProgressService.recordFailure(task.appId(), target);
            }
        }
    }

    public int queueSize() {
        return queue.size();
    }
}
