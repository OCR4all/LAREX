package de.uniwue.zpd.dachs.larex.backend.service.storage;

import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceStorageQuotaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
public class WorkspaceQuotaRefreshService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceQuotaRefreshService.class);

    private final WorkspaceStorageQuotaService quotaService;
    private final TaskScheduler quotaRefreshTaskScheduler;
    private final Map<String, PendingRefresh> pendingRefreshes = new ConcurrentHashMap<>();

    @Value("${larex.storage.quota-refresh-debounce-ms:1500}")
    private long refreshDebounceMs;

    public WorkspaceQuotaRefreshService(
            WorkspaceStorageQuotaService quotaService,
            @Qualifier("quotaRefreshTaskScheduler") TaskScheduler quotaRefreshTaskScheduler) {
        this.quotaService = quotaService;
        this.quotaRefreshTaskScheduler = quotaRefreshTaskScheduler;
    }

    public void scheduleUsageRefresh(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            return;
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    scheduleDebouncedRefresh(workspaceId);
                }
            });
            return;
        }

        scheduleDebouncedRefresh(workspaceId);
    }

    private void scheduleDebouncedRefresh(String workspaceId) {
        PendingRefresh nextRefresh = new PendingRefresh();
        PendingRefresh previous = pendingRefreshes.put(workspaceId, nextRefresh);
        if (previous != null && previous.future != null) {
            previous.future.cancel(false);
        }

        nextRefresh.future = quotaRefreshTaskScheduler.schedule(
                () -> runRefresh(workspaceId, nextRefresh),
                Instant.now().plusMillis(Math.max(refreshDebounceMs, 1L))
        );
    }

    private void runRefresh(String workspaceId, PendingRefresh expectedRefresh) {
        try {
            quotaService.recalculateUsage(workspaceId);
            log.debug("Refreshed storage quota usage for workspace {}", workspaceId);
        } catch (Exception e) {
            log.warn("Failed to refresh storage quota usage for workspace {}: {}", workspaceId, e.getMessage(), e);
        } finally {
            pendingRefreshes.remove(workspaceId, expectedRefresh);
        }
    }

    private static final class PendingRefresh {
        private volatile ScheduledFuture<?> future;
    }
}
