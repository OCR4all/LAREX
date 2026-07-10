package de.uniwue.zpd.dachs.larex.backend.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "larex.iiif")
public class IiifProperties {

    @Min(0)
    @Max(60_000)
    private int downloadMinIntervalMs = 100;

    @Min(1_048_576)
    @Max(10_737_418_240L)
    private long maxImageDownloadBytes = 536_870_912L;

    @Valid
    private ExecutorPoolProperties previewAsync = new ExecutorPoolProperties(2, 4, 100);

    @Valid
    private ExecutorPoolProperties downloadAsync = new ExecutorPoolProperties(1, 2, 50);

    @Min(30_000)
    @Max(3_600_000)
    private long workerLeaseDurationMs = 120_000;

    @Min(5_000)
    @Max(300_000)
    private long workerHeartbeatIntervalMs = 30_000;

    @Min(1_000)
    @Max(300_000)
    private long workerRecoveryIntervalMs = 5_000;

    public int getDownloadMinIntervalMs() {
        return downloadMinIntervalMs;
    }

    public void setDownloadMinIntervalMs(int downloadMinIntervalMs) {
        this.downloadMinIntervalMs = downloadMinIntervalMs;
    }

    public long getMaxImageDownloadBytes() {
        return maxImageDownloadBytes;
    }

    public void setMaxImageDownloadBytes(long maxImageDownloadBytes) {
        this.maxImageDownloadBytes = maxImageDownloadBytes;
    }

    public ExecutorPoolProperties getPreviewAsync() {
        return previewAsync;
    }

    public void setPreviewAsync(ExecutorPoolProperties previewAsync) {
        this.previewAsync = previewAsync;
    }

    public ExecutorPoolProperties getDownloadAsync() {
        return downloadAsync;
    }

    public void setDownloadAsync(ExecutorPoolProperties downloadAsync) {
        this.downloadAsync = downloadAsync;
    }

    public long getWorkerLeaseDurationMs() {
        return workerLeaseDurationMs;
    }

    public void setWorkerLeaseDurationMs(long workerLeaseDurationMs) {
        this.workerLeaseDurationMs = workerLeaseDurationMs;
    }

    public long getWorkerHeartbeatIntervalMs() {
        return workerHeartbeatIntervalMs;
    }

    public void setWorkerHeartbeatIntervalMs(long workerHeartbeatIntervalMs) {
        this.workerHeartbeatIntervalMs = workerHeartbeatIntervalMs;
    }

    public long getWorkerRecoveryIntervalMs() {
        return workerRecoveryIntervalMs;
    }

    public void setWorkerRecoveryIntervalMs(long workerRecoveryIntervalMs) {
        this.workerRecoveryIntervalMs = workerRecoveryIntervalMs;
    }

    @AssertTrue(message = "worker heartbeat interval must be shorter than the worker lease duration")
    public boolean isWorkerHeartbeatShorterThanLease() {
        return workerHeartbeatIntervalMs < workerLeaseDurationMs;
    }
}
