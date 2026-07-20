package de.uniwue.zpd.dachs.larex.backend.config;

import jakarta.validation.Valid;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "larex.backup")
public class BackupProperties {

    private boolean enabled = false;
    private List<String> allowedPaths = new ArrayList<>(List.of("/mnt/data", "/uploads"));
    private String outputDir = "/mnt/data/backups";
    private int maxFilesPerJob = 500000;
    private long maxArchiveBytes = 21_474_836_480L;
    private int maxArchiveEntries = 500_000;
    private long maxArchiveEntryBytes = 5_368_709_120L;
    private long maxArchiveTotalBytes = 107_374_182_400L;
    private double maxArchiveCompressionRatio = 200.0;
    @Valid
    private ExecutorPoolProperties async = new ExecutorPoolProperties(1, 1, 10);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getAllowedPaths() {
        return allowedPaths;
    }

    public void setAllowedPaths(List<String> allowedPaths) {
        this.allowedPaths = allowedPaths;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    public int getMaxFilesPerJob() {
        return maxFilesPerJob;
    }

    public void setMaxFilesPerJob(int maxFilesPerJob) {
        this.maxFilesPerJob = maxFilesPerJob;
    }

    public long getMaxArchiveBytes() {
        return maxArchiveBytes;
    }

    public void setMaxArchiveBytes(long maxArchiveBytes) {
        this.maxArchiveBytes = maxArchiveBytes;
    }

    public int getMaxArchiveEntries() {
        return maxArchiveEntries;
    }

    public void setMaxArchiveEntries(int maxArchiveEntries) {
        this.maxArchiveEntries = maxArchiveEntries;
    }

    public long getMaxArchiveEntryBytes() {
        return maxArchiveEntryBytes;
    }

    public void setMaxArchiveEntryBytes(long maxArchiveEntryBytes) {
        this.maxArchiveEntryBytes = maxArchiveEntryBytes;
    }

    public long getMaxArchiveTotalBytes() {
        return maxArchiveTotalBytes;
    }

    public void setMaxArchiveTotalBytes(long maxArchiveTotalBytes) {
        this.maxArchiveTotalBytes = maxArchiveTotalBytes;
    }

    public double getMaxArchiveCompressionRatio() {
        return maxArchiveCompressionRatio;
    }

    public void setMaxArchiveCompressionRatio(double maxArchiveCompressionRatio) {
        this.maxArchiveCompressionRatio = maxArchiveCompressionRatio;
    }

    public ExecutorPoolProperties getAsync() {
        return async;
    }

    public void setAsync(ExecutorPoolProperties async) {
        this.async = async;
    }
}
