package de.uniwue.zpd.dachs.larex.backend.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "larex.import")
public class ImportProperties {

    private boolean enabled = true;
    private List<String> allowedPaths = new ArrayList<>(List.of("/data/imports", "/mnt/shared/datasets"));

    @Min(1)
    private int maxScanDepth = 10;

    @Min(1)
    private int maxFilesPerJob = 100000;

    @Valid
    private ExecutorPoolProperties async = new ExecutorPoolProperties(1, 2, 10);

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

    public int getMaxScanDepth() {
        return maxScanDepth;
    }

    public void setMaxScanDepth(int maxScanDepth) {
        this.maxScanDepth = maxScanDepth;
    }

    public int getMaxFilesPerJob() {
        return maxFilesPerJob;
    }

    public void setMaxFilesPerJob(int maxFilesPerJob) {
        this.maxFilesPerJob = maxFilesPerJob;
    }

    public ExecutorPoolProperties getAsync() {
        return async;
    }

    public void setAsync(ExecutorPoolProperties async) {
        this.async = async;
    }
}
