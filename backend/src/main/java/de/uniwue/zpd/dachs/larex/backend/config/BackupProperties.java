package de.uniwue.zpd.dachs.larex.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "larex.backup")
public class BackupProperties {

    private boolean enabled = false;
    private List<String> allowedPaths = new ArrayList<>(List.of("/mnt/data", "/uploads"));
    private String outputDir = "/mnt/data/backups";
    private int maxFilesPerJob = 500000;

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
}
