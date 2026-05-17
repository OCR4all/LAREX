package de.uniwue.zpd.dachs.larex.backend.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;

@Validated
@ConfigurationProperties(prefix = "larex.upload")
public class UploadProperties {

    @Min(1)
    private long chunkSizeBytes = 5242880L;

    private Path tempDirectory = Path.of("/uploads/temp");

    @Min(1)
    private int sessionTimeoutHours = 24;

    @Min(1)
    private int batchSize = 10;

    @Min(1)
    private int maxConcurrentSessions = 50;

    @Valid
    private ExecutorPoolProperties async = new ExecutorPoolProperties(2, 5, 100);

    @Valid
    private ExecutorPoolProperties indexAsync = new ExecutorPoolProperties(1, 2, 200);

    @Valid
    private IndexingProperties indexing = new IndexingProperties();

    public long getChunkSizeBytes() {
        return chunkSizeBytes;
    }

    public void setChunkSizeBytes(long chunkSizeBytes) {
        this.chunkSizeBytes = chunkSizeBytes;
    }

    public Path getTempDirectory() {
        return tempDirectory;
    }

    public void setTempDirectory(Path tempDirectory) {
        this.tempDirectory = tempDirectory;
    }

    public int getSessionTimeoutHours() {
        return sessionTimeoutHours;
    }

    public void setSessionTimeoutHours(int sessionTimeoutHours) {
        this.sessionTimeoutHours = sessionTimeoutHours;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxConcurrentSessions() {
        return maxConcurrentSessions;
    }

    public void setMaxConcurrentSessions(int maxConcurrentSessions) {
        this.maxConcurrentSessions = maxConcurrentSessions;
    }

    public ExecutorPoolProperties getAsync() {
        return async;
    }

    public void setAsync(ExecutorPoolProperties async) {
        this.async = async;
    }

    public ExecutorPoolProperties getIndexAsync() {
        return indexAsync;
    }

    public void setIndexAsync(ExecutorPoolProperties indexAsync) {
        this.indexAsync = indexAsync;
    }

    public IndexingProperties getIndexing() {
        return indexing;
    }

    public void setIndexing(IndexingProperties indexing) {
        this.indexing = indexing;
    }

    public static class IndexingProperties {

        @Min(1)
        private long staleThresholdMs = 60000;

        public long getStaleThresholdMs() {
            return staleThresholdMs;
        }

        public void setStaleThresholdMs(long staleThresholdMs) {
            this.staleThresholdMs = staleThresholdMs;
        }
    }
}
