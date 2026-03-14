package de.uniwue.zpd.dachs.larex.backend.service.storage;

import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceStorageQuotaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class WorkspaceQuotaRefreshServiceTest {

    @Mock
    private WorkspaceStorageQuotaService quotaService;

    private ThreadPoolTaskScheduler scheduler;
    private WorkspaceQuotaRefreshService service;

    @BeforeEach
    void setUp() {
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("quota-refresh-test-");
        scheduler.initialize();

        service = new WorkspaceQuotaRefreshService(quotaService, scheduler);
        ReflectionTestUtils.setField(service, "refreshDebounceMs", 50L);
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdown();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void scheduleUsageRefresh_debouncesRepeatedRequestsForSameWorkspace() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(quotaService).recalculateUsage("ws-1");

        service.scheduleUsageRefresh("ws-1");
        service.scheduleUsageRefresh("ws-1");
        service.scheduleUsageRefresh("ws-1");

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        verify(quotaService, times(1)).recalculateUsage("ws-1");
    }

    @Test
    void scheduleUsageRefresh_waitsUntilTransactionCommit() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(quotaService).recalculateUsage("ws-1");

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.scheduleUsageRefresh("ws-1");
            verifyNoInteractions(quotaService);

            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        verify(quotaService, times(1)).recalculateUsage("ws-1");
    }
}
