package de.uniwue.zpd.dachs.larex.backend.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class JobRealtimePublisher {

    private static final Logger log = LoggerFactory.getLogger(JobRealtimePublisher.class);

    private final NotificationBridgeClient notificationBridgeClient;
    private final TaskExecutor taskExecutor;

    public JobRealtimePublisher(NotificationBridgeClient notificationBridgeClient,
                                @Qualifier("actionNotificationTaskExecutor") TaskExecutor taskExecutor) {
        this.notificationBridgeClient = notificationBridgeClient;
        this.taskExecutor = taskExecutor;
    }

    public void publish(String kind,
                        String jobId,
                        String workspaceId,
                        String projectId,
                        String status,
                        String userId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", kind);
        payload.put("jobId", jobId);
        if (workspaceId != null) payload.put("workspaceId", workspaceId);
        if (projectId != null) payload.put("projectId", projectId);
        if (status != null) payload.put("status", status);

        try {
            taskExecutor.execute(() -> notificationBridgeClient.pushJobEvent(userId, payload, "job-service"));
        } catch (RuntimeException error) {
            log.warn("Failed to schedule realtime update for {} job {}", kind, jobId, error);
        }
    }
}
