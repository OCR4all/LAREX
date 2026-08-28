package de.uniwue.zpd.dachs.larex.backend.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "larex.project-export")
public class ProjectExportProperties {

    @NotBlank
    private String artifactDirectory = "/mnt/data/export-artifacts";
    @Min(1)
    private long artifactTtlHours = 24;
    @Min(1)
    private long maxArtifactBytes = 107_374_182_400L;
    @Min(0)
    private long minimumFreeBytes = 5_368_709_120L;
    @Min(1)
    private int maxActiveJobsPerUser = 2;
    @Min(1)
    private int maxQueuedJobs = 100;
    @Min(10_000)
    private long workerLeaseDurationMs = 120_000;
    @Min(1_000)
    private long workerHeartbeatIntervalMs = 30_000;
    @Min(1_000)
    private long workerRecoveryIntervalMs = 5_000;
    @Valid
    private ExecutorPoolProperties async = new ExecutorPoolProperties(1, 1, 50);

    public String getArtifactDirectory() { return artifactDirectory; }
    public void setArtifactDirectory(String artifactDirectory) { this.artifactDirectory = artifactDirectory; }
    public long getArtifactTtlHours() { return artifactTtlHours; }
    public void setArtifactTtlHours(long artifactTtlHours) { this.artifactTtlHours = artifactTtlHours; }
    public long getMaxArtifactBytes() { return maxArtifactBytes; }
    public void setMaxArtifactBytes(long maxArtifactBytes) { this.maxArtifactBytes = maxArtifactBytes; }
    public long getMinimumFreeBytes() { return minimumFreeBytes; }
    public void setMinimumFreeBytes(long minimumFreeBytes) { this.minimumFreeBytes = minimumFreeBytes; }
    public int getMaxActiveJobsPerUser() { return maxActiveJobsPerUser; }
    public void setMaxActiveJobsPerUser(int maxActiveJobsPerUser) { this.maxActiveJobsPerUser = maxActiveJobsPerUser; }
    public int getMaxQueuedJobs() { return maxQueuedJobs; }
    public void setMaxQueuedJobs(int maxQueuedJobs) { this.maxQueuedJobs = maxQueuedJobs; }
    public long getWorkerLeaseDurationMs() { return workerLeaseDurationMs; }
    public void setWorkerLeaseDurationMs(long workerLeaseDurationMs) { this.workerLeaseDurationMs = workerLeaseDurationMs; }
    public long getWorkerHeartbeatIntervalMs() { return workerHeartbeatIntervalMs; }
    public void setWorkerHeartbeatIntervalMs(long workerHeartbeatIntervalMs) { this.workerHeartbeatIntervalMs = workerHeartbeatIntervalMs; }
    public long getWorkerRecoveryIntervalMs() { return workerRecoveryIntervalMs; }
    public void setWorkerRecoveryIntervalMs(long workerRecoveryIntervalMs) { this.workerRecoveryIntervalMs = workerRecoveryIntervalMs; }
    public ExecutorPoolProperties getAsync() { return async; }
    public void setAsync(ExecutorPoolProperties async) { this.async = async; }
}
