package de.uniwue.zpd.dachs.larex.backend.service.notification;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class TaskQueueRealtimePublisher {

    private final NotificationBridgeClient notificationBridgeClient;
    private final TaskExecutor taskExecutor;

    public TaskQueueRealtimePublisher(
            NotificationBridgeClient notificationBridgeClient,
            @Qualifier("actionNotificationTaskExecutor") TaskExecutor taskExecutor
    ) {
        this.notificationBridgeClient = notificationBridgeClient;
        this.taskExecutor = taskExecutor;
    }

    public void publishAfterCommit(Collection<String> userIds, String workspaceId) {
        Set<String> recipients = userIds == null
                ? Set.of()
                : userIds.stream()
                        .filter(userId -> userId != null && !userId.isBlank())
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (recipients.isEmpty()) return;

        Runnable publish = () -> taskExecutor.execute(() -> {
            for (String userId : recipients) {
                notificationBridgeClient.pushTaskQueueChanged(userId, workspaceId, "task-queue-service");
            }
        });

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }
}
