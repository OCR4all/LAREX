package de.uniwue.zpd.dachs.larex.backend.service.project;

import de.uniwue.zpd.dachs.larex.backend.config.ProjectExportProperties;
import de.uniwue.zpd.dachs.larex.backend.dto.ProjectBatchExportDto;
import de.uniwue.zpd.dachs.larex.backend.entity.ProjectExportJob;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectExportJobRepository;
import de.uniwue.zpd.dachs.larex.backend.service.notification.JobRealtimePublisher;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProjectExportJobService {

    private static final List<ProjectExportJob.Status> ACTIVE = List.of(
            ProjectExportJob.Status.QUEUED, ProjectExportJob.Status.RUNNING);

    private final ProjectExportJobRepository repository;
    private final ProjectBatchExportService batchExportService;
    private final ProjectExportQueueService queueService;
    private final ProjectExportArtifactStore artifactStore;
    private final ProjectExportProperties properties;
    private final WorkspaceAccessService workspaceAccessService;
    private final JobRealtimePublisher realtimePublisher;
    private final ObjectMapper objectMapper;

    public ProjectExportJobService(ProjectExportJobRepository repository,
                                   ProjectBatchExportService batchExportService,
                                   ProjectExportQueueService queueService,
                                   ProjectExportArtifactStore artifactStore,
                                   ProjectExportProperties properties,
                                   WorkspaceAccessService workspaceAccessService,
                                   JobRealtimePublisher realtimePublisher,
                                   ObjectMapper objectMapper) {
        this.repository = repository;
        this.batchExportService = batchExportService;
        this.queueService = queueService;
        this.artifactStore = artifactStore;
        this.properties = properties;
        this.workspaceAccessService = workspaceAccessService;
        this.realtimePublisher = realtimePublisher;
        this.objectMapper = objectMapper;
    }

    public synchronized ProjectBatchExportDto.JobResponse create(String workspaceId, String userId,
                                                                 ProjectBatchExportDto.ExportRequest request) {
        batchExportService.prepareBatchExport(workspaceId, userId, request);
        if (repository.countByStatus(ProjectExportJob.Status.QUEUED) >= properties.getMaxQueuedJobs()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The project export queue is full. Try again later.");
        }
        long active = repository.countByWorkspaceIdAndCreatedByUserIdAndStatusIn(workspaceId, userId, ACTIVE);
        if (active >= properties.getMaxActiveJobsPerUser()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many active project exports. Wait for an existing export to finish.");
        }

        ProjectExportJob job = new ProjectExportJob();
        job.setWorkspaceId(workspaceId);
        job.setCreatedByUserId(userId);
        job.setRequestJson(objectMapper.writeValueAsString(request));
        job = repository.saveAndFlush(job);
        queueService.enqueue(job.getId());
        realtimePublisher.publish("project-export", job.getId(), workspaceId, null, "QUEUED", userId);
        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public ProjectBatchExportDto.JobResponse get(String workspaceId, String jobId, String userId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        return toResponse(requireOwned(workspaceId, jobId, userId));
    }

    @Transactional(readOnly = true)
    public List<ProjectBatchExportDto.JobResponse> list(String workspaceId, String userId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        return repository.findTop100ByWorkspaceIdAndCreatedByUserIdOrderByCreatedDesc(workspaceId, userId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public ProjectBatchExportDto.JobResponse cancel(String workspaceId, String jobId, String userId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        ProjectExportJob job = requireOwned(workspaceId, jobId, userId);
        if (job.getStatus() == ProjectExportJob.Status.QUEUED) {
            job.setCancelRequested(true);
            job.setStatus(ProjectExportJob.Status.CANCELLED);
            job.setCompletedAt(LocalDateTime.now());
        } else if (job.getStatus() == ProjectExportJob.Status.RUNNING) {
            job.setCancelRequested(true);
        }
        return toResponse(repository.save(job));
    }

    @Transactional(readOnly = true)
    public ArtifactDownload download(String workspaceId, String jobId, String userId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        ProjectExportJob job = requireOwned(workspaceId, jobId, userId);
        if (job.getStatus() == ProjectExportJob.Status.EXPIRED
                || job.getExpiresAt() != null && job.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Export artifact has expired");
        }
        if (job.getStatus() != ProjectExportJob.Status.COMPLETED || job.getArtifactPath() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Export artifact is not ready");
        }
        Path path = artifactStore.resolveStoredPath(job.getArtifactPath());
        if (!Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.GONE, "Export artifact is no longer available");
        }
        return new ArtifactDownload(path, job.getArtifactFileName(), job.getArtifactSize(),
                job.getArtifactChecksumSha256());
    }

    @Transactional
    public void expireArtifacts() {
        List<ProjectExportJob> expired = repository.findByStatusInAndExpiresAtBefore(
                List.of(ProjectExportJob.Status.COMPLETED), LocalDateTime.now());
        for (ProjectExportJob job : expired) {
            try {
                artifactStore.deleteJobArtifacts(job.getId());
                job.setStatus(ProjectExportJob.Status.EXPIRED);
                job.setArtifactPath(null);
                repository.save(job);
            } catch (IOException ignored) {
                // Retry on the next cleanup pass; never mark an undeleted artifact expired.
            }
        }
    }

    private ProjectExportJob requireOwned(String workspaceId, String jobId, String userId) {
        return repository.findByIdAndWorkspaceIdAndCreatedByUserId(jobId, workspaceId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Export job not found"));
    }

    private ProjectBatchExportDto.JobResponse toResponse(ProjectExportJob job) {
        ProjectBatchExportDto.ExportMode mode = objectMapper.readValue(
                job.getRequestJson(), ProjectBatchExportDto.ExportRequest.class).mode();
        return new ProjectBatchExportDto.JobResponse(job.getId(), job.getWorkspaceId(), mode,
                job.getStatus().name(), job.getArtifactFileName(), job.getArtifactSize(),
                job.getArtifactChecksumSha256(), job.getErrorMessage(), job.getCreated(), job.getStartedAt(),
                job.getCompletedAt(), job.getExpiresAt());
    }

    public record ArtifactDownload(Path path, String fileName, Long contentLength, String checksumSha256) {}
}
