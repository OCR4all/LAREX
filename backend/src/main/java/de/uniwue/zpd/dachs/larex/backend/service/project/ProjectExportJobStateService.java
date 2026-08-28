package de.uniwue.zpd.dachs.larex.backend.service.project;

import de.uniwue.zpd.dachs.larex.backend.config.ProjectExportProperties;
import de.uniwue.zpd.dachs.larex.backend.entity.ProjectExportJob;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectExportJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProjectExportJobStateService {

    private final ProjectExportJobRepository repository;
    private final ProjectExportProperties properties;

    public ProjectExportJobStateService(ProjectExportJobRepository repository,
                                        ProjectExportProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional
    public boolean claim(String jobId, String workerId) {
        LocalDateTime now = LocalDateTime.now();
        return repository.claim(jobId, workerId,
                now.plusNanos(properties.getWorkerLeaseDurationMs() * 1_000_000), now) == 1;
    }

    @Transactional(readOnly = true)
    public ProjectExportJob requireRunning(String jobId, String workerId) {
        ProjectExportJob job = repository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Export job disappeared: " + jobId));
        if (job.getStatus() != ProjectExportJob.Status.RUNNING || !workerId.equals(job.getLeaseOwner())) {
            throw new IllegalStateException("Export job lease was lost: " + jobId);
        }
        return job;
    }

    @Transactional(readOnly = true)
    public boolean isCancellationRequested(String jobId) {
        return repository.existsByIdAndCancelRequestedTrue(jobId);
    }

    @Transactional
    public void complete(String jobId, String workerId, String fileName, String path,
                         long size, String checksum) {
        ProjectExportJob job = requireRunning(jobId, workerId);
        LocalDateTime now = LocalDateTime.now();
        job.setStatus(ProjectExportJob.Status.COMPLETED);
        job.setArtifactFileName(fileName);
        job.setArtifactPath(path);
        job.setArtifactSize(size);
        job.setArtifactChecksumSha256(checksum);
        job.setCompletedAt(now);
        job.setExpiresAt(now.plusHours(properties.getArtifactTtlHours()));
        clearLease(job);
        repository.save(job);
    }

    @Transactional
    public void fail(String jobId, String workerId, String message, boolean cancelled) {
        repository.findById(jobId).ifPresent(job -> {
            if (job.getStatus() != ProjectExportJob.Status.RUNNING || !workerId.equals(job.getLeaseOwner())) return;
            job.setStatus(cancelled ? ProjectExportJob.Status.CANCELLED : ProjectExportJob.Status.FAILED);
            job.setErrorMessage(cancelled ? null : abbreviate(message));
            job.setCompletedAt(LocalDateTime.now());
            clearLease(job);
            repository.save(job);
        });
    }

    @Transactional
    public int recoverExpiredLeases() {
        return repository.requeueExpiredLeases(LocalDateTime.now());
    }

    @Transactional
    public void renewLeases(String workerId, List<String> jobIds) {
        if (jobIds.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        repository.renewLeases(jobIds, workerId,
                now.plusNanos(properties.getWorkerLeaseDurationMs() * 1_000_000), now);
    }

    private void clearLease(ProjectExportJob job) {
        job.setLeaseOwner(null);
        job.setLeaseExpiresAt(null);
        job.setLastHeartbeatAt(null);
    }

    private String abbreviate(String message) {
        String value = message == null || message.isBlank() ? "Export failed" : message;
        return value.length() <= 4000 ? value : value.substring(0, 4000);
    }
}
