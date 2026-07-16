package de.uniwue.zpd.dachs.larex.backend.service.action;

import de.uniwue.zpd.dachs.larex.backend.entity.ActionRun;
import de.uniwue.zpd.dachs.larex.backend.service.notification.NotificationBridgeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ActionRealtimePublisher {

    private static final Logger log = LoggerFactory.getLogger(ActionRealtimePublisher.class);

    private final NotificationBridgeClient notificationBridgeClient;
    private final TaskExecutor notificationTaskExecutor;
    private final ActionMetrics metrics;

    public ActionRealtimePublisher(
            NotificationBridgeClient notificationBridgeClient,
            @Qualifier("actionNotificationTaskExecutor") TaskExecutor notificationTaskExecutor,
            ActionMetrics metrics
    ) {
        this.notificationBridgeClient = notificationBridgeClient;
        this.notificationTaskExecutor = notificationTaskExecutor;
        this.metrics = metrics;
    }

    public void publishRunUpdated(ActionRun run) {
        publishAfterCommit("ACTION_RUN_UPDATED", run, Map.of());
    }

    public void publishPageResult(ActionRun run, String pageId, Set<String> resultTypes) {
        publishAfterCommit("ACTION_PAGE_RESULT_IMPORTED", run, Map.of(
                "pageId", pageId,
                "resultTypes", List.copyOf(resultTypes)
        ));
        publishRunUpdated(run);
    }

    private void publishAfterCommit(String type, ActionRun run, Map<String, Object> details) {
        String runId = run.getId();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", runId);
        payload.put("workspaceId", run.getWorkspaceId());
        payload.put("projectId", run.getProjectId());
        payload.putAll(details);

        Runnable schedule = () -> {
            try {
                notificationTaskExecutor.execute(() -> push(type, runId, payload));
            } catch (RuntimeException error) {
                metrics.recordNotificationFailure();
                log.warn("Failed to schedule LAREX Action realtime event {} for run {}", type, runId, error);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    schedule.run();
                }
            });
        } else {
            schedule.run();
        }
    }

    private void push(String type, String runId, Map<String, Object> payload) {
        try {
            notificationBridgeClient.pushActionEvent(type, payload, "action-run-service");
        } catch (RuntimeException error) {
            metrics.recordNotificationFailure();
            log.warn("Failed to publish LAREX Action realtime event {} for run {}", type, runId, error);
        }
    }
}
