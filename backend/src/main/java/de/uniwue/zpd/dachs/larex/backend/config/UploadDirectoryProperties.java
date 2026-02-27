package de.uniwue.zpd.dachs.larex.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "larex.upload.directories")
public class UploadDirectoryProperties {

    private Path rootDirectory;
    private Path tempDirectory;
    private boolean createIfMissing = true;
    private boolean verifyWritable = true;
    private boolean failFast = true;

    public Path getRootDirectory() {
        return rootDirectory;
    }

    public void setRootDirectory(Path rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    public Path getTempDirectory() {
        return tempDirectory;
    }

    public void setTempDirectory(Path tempDirectory) {
        this.tempDirectory = tempDirectory;
    }

    public boolean isCreateIfMissing() {
        return createIfMissing;
    }

    public void setCreateIfMissing(boolean createIfMissing) {
        this.createIfMissing = createIfMissing;
    }

    public boolean isVerifyWritable() {
        return verifyWritable;
    }

    public void setVerifyWritable(boolean verifyWritable) {
        this.verifyWritable = verifyWritable;
    }

    public boolean isFailFast() {
        return failFast;
    }

    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }
}
