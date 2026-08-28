package de.uniwue.zpd.dachs.larex.backend.service.project;

import de.uniwue.zpd.dachs.larex.backend.config.ProjectExportProperties;
import de.uniwue.zpd.dachs.larex.backend.dto.ProjectBatchExportDto;
import de.uniwue.zpd.dachs.larex.backend.entity.ProjectExportJob;
import de.uniwue.zpd.dachs.larex.backend.service.backup.ArchiveIoService;
import de.uniwue.zpd.dachs.larex.backend.service.notification.JobRealtimePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Timer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class ProjectExportJobProcessor {

    private static final Logger log = LoggerFactory.getLogger(ProjectExportJobProcessor.class);

    private final ProjectExportJobStateService stateService;
    private final ProjectBatchExportService batchExportService;
    private final ProjectExportArtifactStore artifactStore;
    private final ProjectExportProperties properties;
    private final ArchiveIoService archiveIoService;
    private final JobRealtimePublisher realtimePublisher;
    private final ObjectMapper objectMapper;
    private final ProjectExportMetrics metrics;

    public ProjectExportJobProcessor(ProjectExportJobStateService stateService,
                                     ProjectBatchExportService batchExportService,
                                     ProjectExportArtifactStore artifactStore,
                                     ProjectExportProperties properties,
                                     ArchiveIoService archiveIoService,
                                     JobRealtimePublisher realtimePublisher,
                                     ObjectMapper objectMapper,
                                     ProjectExportMetrics metrics) {
        this.stateService = stateService;
        this.batchExportService = batchExportService;
        this.artifactStore = artifactStore;
        this.properties = properties;
        this.archiveIoService = archiveIoService;
        this.realtimePublisher = realtimePublisher;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    public void process(String jobId, String workerId) {
        if (!stateService.claim(jobId, workerId)) return;
        ProjectExportJob job = stateService.requireRunning(jobId, workerId);
        Timer.Sample timer = metrics.start();
        publish(job, "RUNNING");
        ProjectExportArtifactStore.PendingArtifact pending = null;
        try {
            ProjectBatchExportDto.ExportRequest request = objectMapper.readValue(
                    job.getRequestJson(), ProjectBatchExportDto.ExportRequest.class);
            ProjectBatchExportService.PreparedBatchExport batch = batchExportService.prepareBatchExport(
                    job.getWorkspaceId(), job.getCreatedByUserId(), request);
            pending = artifactStore.createPending(jobId);
            try (OutputStream raw = pending.outputStream();
                 LimitedExportOutputStream limited = new LimitedExportOutputStream(raw,
                         properties.getMaxArtifactBytes(),
                         () -> stateService.isCancellationRequested(jobId),
                         artifactStore::requireSpace)) {
                batchExportService.writeBatchExport(batch, limited);
            }
            if (stateService.isCancellationRequested(jobId)) {
                throw new ExportCancelledException();
            }
            Path completed = artifactStore.complete(pending);
            long size = Files.size(completed);
            String checksum = archiveIoService.sha256(completed);
            stateService.complete(jobId, workerId, "larex-projects-batch-export.zip",
                    artifactStore.relativePath(completed), size, checksum);
            metrics.finish(timer, "completed", size);
            publish(job, "COMPLETED");
        } catch (Exception error) {
            boolean cancelled = error instanceof ExportCancelledException
                    || stateService.isCancellationRequested(jobId);
            stateService.fail(jobId, workerId, error.getMessage(), cancelled);
            metrics.finish(timer, cancelled ? "cancelled" : "failed", -1);
            try {
                artifactStore.deleteJobArtifacts(jobId);
            } catch (IOException cleanupError) {
                log.warn("Failed to clean artifacts for export job {}", jobId, cleanupError);
            }
            publish(job, cancelled ? "CANCELLED" : "FAILED");
            if (!cancelled) log.error("Project export job {} failed", jobId, error);
        }
    }

    private void publish(ProjectExportJob job, String status) {
        realtimePublisher.publish("project-export", job.getId(), job.getWorkspaceId(), null,
                status, job.getCreatedByUserId());
    }

    private static final class ExportCancelledException extends IOException {
        private ExportCancelledException() { super("Export cancelled"); }
    }
}
