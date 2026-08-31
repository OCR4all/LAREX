package de.uniwue.zpd.dachs.larex.backend.service.project;

import de.uniwue.zpd.dachs.larex.backend.config.ProjectExportProperties;
import de.uniwue.zpd.dachs.larex.backend.entity.ProjectExportJob;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectExportJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProjectExportQueueService {
    private static final Logger log = LoggerFactory.getLogger(ProjectExportQueueService.class);
    private final ProjectExportJobRepository repository;
    private final ProjectExportJobProcessor processor;
    private final ProjectExportJobStateService stateService;
    private final TaskExecutor executor;
    private final String workerId = UUID.randomUUID().toString();
    private final Set<String> submitted = ConcurrentHashMap.newKeySet();

    public ProjectExportQueueService(ProjectExportJobRepository repository,
                                     ProjectExportJobProcessor processor,
                                     ProjectExportJobStateService stateService,
                                     @Qualifier("projectExportTaskExecutor") TaskExecutor executor) {
        this.repository = repository;
        this.processor = processor;
        this.stateService = stateService;
        this.executor = executor;
    }

    public void enqueue(String jobId) {
        if (!submitted.add(jobId)) return;
        try {
            executor.execute(() -> {
                try { processor.process(jobId, workerId); }
                finally { submitted.remove(jobId); }
            });
        } catch (TaskRejectedException error) {
            submitted.remove(jobId);
            log.warn("Project export queue is full; job {} remains queued", jobId);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        recover();
        enqueuePending();
    }

    @Scheduled(
            fixedDelayString = "${larex.project-export.worker-recovery-interval-ms:5000}",
            scheduler = "coordinationTaskScheduler"
    )
    public void enqueuePending() {
        repository.findByStatusOrderByCreatedAsc(ProjectExportJob.Status.QUEUED)
                .forEach(job -> enqueue(job.getId()));
    }

    @Scheduled(
            fixedDelayString = "${larex.project-export.worker-recovery-interval-ms:5000}",
            scheduler = "coordinationTaskScheduler"
    )
    public void recover() {
        int recovered = stateService.recoverExpiredLeases();
        if (recovered > 0) log.warn("Recovered {} expired project export lease(s)", recovered);
    }

    @Scheduled(
            fixedDelayString = "${larex.project-export.worker-heartbeat-interval-ms:30000}",
            scheduler = "coordinationTaskScheduler"
    )
    public void heartbeat() {
        stateService.renewLeases(workerId, List.copyOf(submitted));
    }
}
