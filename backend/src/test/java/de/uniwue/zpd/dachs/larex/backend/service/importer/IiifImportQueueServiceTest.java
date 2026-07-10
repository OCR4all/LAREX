package de.uniwue.zpd.dachs.larex.backend.service.importer;

import de.uniwue.zpd.dachs.larex.backend.config.IiifProperties;
import de.uniwue.zpd.dachs.larex.backend.entity.IiifImportJob;
import de.uniwue.zpd.dachs.larex.backend.repository.importing.IiifImportJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IiifImportQueueServiceTest {

    private static final String WORKER_ID = "worker-1";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-09T10:00:00Z"), ZoneOffset.UTC);

    private IiifImportJobRepository jobRepository;
    private AsyncIiifImportProcessor importProcessor;
    private TaskExecutor taskExecutor;
    private IiifImportQueueService queueService;

    @BeforeEach
    void setUp() {
        jobRepository = mock(IiifImportJobRepository.class);
        importProcessor = mock(AsyncIiifImportProcessor.class);
        taskExecutor = mock(TaskExecutor.class);
        queueService = new IiifImportQueueService(
                jobRepository,
                importProcessor,
                taskExecutor,
                new IiifProperties(),
                CLOCK,
                WORKER_ID
        );
    }

    @Test
    void submitsEachPendingJobOnlyOnceUntilItsTaskFinishes() {
        queueService.enqueue("job-1");
        queueService.enqueue("job-1");

        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskExecutor).execute(taskCaptor.capture());
        taskCaptor.getValue().run();
        verify(importProcessor).processImportJob("job-1", WORKER_ID);

        queueService.enqueue("job-1");
        verify(taskExecutor, times(2)).execute(org.mockito.ArgumentMatchers.any(Runnable.class));
    }

    @Test
    void recoversPersistedPendingJobsInCreationOrder() {
        IiifImportJob first = pendingJob("job-1");
        IiifImportJob second = pendingJob("job-2");
        when(jobRepository.findByStatusOrderByCreatedAsc(IiifImportJob.Status.PENDING))
                .thenReturn(List.of(first, second));

        queueService.enqueuePendingJobs();

        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskExecutor, times(2)).execute(taskCaptor.capture());
        taskCaptor.getAllValues().forEach(Runnable::run);
        var inOrder = org.mockito.Mockito.inOrder(importProcessor);
        inOrder.verify(importProcessor).processImportJob("job-1", WORKER_ID);
        inOrder.verify(importProcessor).processImportJob("job-2", WORKER_ID);
    }

    @Test
    void rejectedSubmissionRemainsEligibleForPeriodicRetry() {
        doThrow(new TaskRejectedException("queue full"))
                .doNothing()
                .when(taskExecutor).execute(org.mockito.ArgumentMatchers.any(Runnable.class));

        assertThatCode(() -> queueService.enqueue("job-1")).doesNotThrowAnyException();
        queueService.enqueue("job-1");

        verify(taskExecutor, times(2)).execute(org.mockito.ArgumentMatchers.any(Runnable.class));
    }

    @Test
    void requeuesJobsWhoseWorkerLeaseExpired() {
        LocalDateTime now = LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC);
        when(jobRepository.requeueExpiredLeases(now)).thenReturn(2);

        queueService.recoverExpiredLeases();

        verify(jobRepository).requeueExpiredLeases(now);
    }

    @Test
    void renewsLeasesForSubmittedJobs() {
        queueService.enqueue("job-1");
        LocalDateTime heartbeatAt = LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC);

        queueService.renewSubmittedJobLeases();

        verify(jobRepository).renewLeases(
                WORKER_ID,
                List.of("job-1"),
                heartbeatAt.plusSeconds(120),
                heartbeatAt
        );
    }

    private IiifImportJob pendingJob(String id) {
        IiifImportJob job = new IiifImportJob();
        job.setId(id);
        job.setStatus(IiifImportJob.Status.PENDING);
        return job;
    }
}
