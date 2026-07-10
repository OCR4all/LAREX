package de.uniwue.zpd.dachs.larex.backend.service.importer;

import de.uniwue.zpd.dachs.larex.backend.config.IiifProperties;
import de.uniwue.zpd.dachs.larex.backend.entity.IiifImportJob;
import de.uniwue.zpd.dachs.larex.backend.repository.importing.IiifImportJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IiifImportQueueService {

    private static final Logger log = LoggerFactory.getLogger(IiifImportQueueService.class);

    private final IiifImportJobRepository jobRepository;
    private final AsyncIiifImportProcessor importProcessor;
    private final TaskExecutor downloadTaskExecutor;
    private final IiifProperties properties;
    private final Clock clock;
    private final String workerId;
    private final Set<String> submittedJobIds = ConcurrentHashMap.newKeySet();

    @Autowired
    public IiifImportQueueService(IiifImportJobRepository jobRepository,
                                  AsyncIiifImportProcessor importProcessor,
                                  @Qualifier("iiifDownloadTaskExecutor") TaskExecutor downloadTaskExecutor,
                                  IiifProperties properties) {
        this(jobRepository, importProcessor, downloadTaskExecutor, properties, Clock.systemDefaultZone(), UUID.randomUUID().toString());
    }

    IiifImportQueueService(IiifImportJobRepository jobRepository,
                           AsyncIiifImportProcessor importProcessor,
                           TaskExecutor downloadTaskExecutor,
                           IiifProperties properties,
                           Clock clock,
                           String workerId) {
        this.jobRepository = jobRepository;
        this.importProcessor = importProcessor;
        this.downloadTaskExecutor = downloadTaskExecutor;
        this.properties = properties;
        this.clock = clock;
        this.workerId = workerId;
    }

    public void enqueue(String jobId) {
        if (!submittedJobIds.add(jobId)) {
            return;
        }

        try {
            downloadTaskExecutor.execute(() -> {
                try {
                    importProcessor.processImportJob(jobId, workerId);
                } finally {
                    submittedJobIds.remove(jobId);
                }
            });
        } catch (TaskRejectedException e) {
            submittedJobIds.remove(jobId);
            log.warn("IIIF download queue is full; pending job {} will be retried", jobId);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverOnStartup() {
        recoverExpiredLeases();
        enqueuePendingJobs();
    }

    @Scheduled(fixedDelayString = "${larex.iiif.worker-recovery-interval-ms:5000}")
    @Transactional(readOnly = true)
    public void enqueuePendingJobs() {
        jobRepository.findByStatusOrderByCreatedAsc(IiifImportJob.Status.PENDING)
                .forEach(job -> enqueue(job.getId()));
    }

    @Scheduled(fixedDelayString = "${larex.iiif.worker-recovery-interval-ms:5000}")
    @Transactional
    public void recoverExpiredLeases() {
        int recovered = jobRepository.requeueExpiredLeases(LocalDateTime.now(clock));
        if (recovered > 0) {
            log.warn("Recovered {} IIIF import job(s) with expired worker leases", recovered);
        }
    }

    @Scheduled(fixedDelayString = "${larex.iiif.worker-heartbeat-interval-ms:30000}")
    @Transactional
    public void renewSubmittedJobLeases() {
        List<String> jobIds = List.copyOf(submittedJobIds);
        if (jobIds.isEmpty()) {
            return;
        }
        LocalDateTime heartbeatAt = LocalDateTime.now(clock);
        jobRepository.renewLeases(
                workerId,
                jobIds,
                heartbeatAt.plusNanos(properties.getWorkerLeaseDurationMs() * 1_000_000),
                heartbeatAt
        );
    }
}
