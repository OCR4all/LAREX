package de.uniwue.zpd.dachs.larex.backend.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "larex.project-packages")
public class ProjectPackageProperties {

    @Valid
    private Archive archive = new Archive();

    public Archive getArchive() {
        return archive;
    }

    public void setArchive(Archive archive) {
        this.archive = archive;
    }

    public static class Archive {

        @Min(1)
        private long maxArchiveBytes = 134_217_728L;

        @Min(1)
        private int maxEntries = 100_000;

        @Min(1)
        private long maxEntryBytes = 536_870_912L;

        @Min(1)
        private long maxTotalBytes = 2_147_483_648L;

        @Min(1)
        private long maxDescriptorBytes = 16_777_216L;

        @DecimalMin("1.0")
        private double maxCompressionRatio = 200.0;

        public long getMaxArchiveBytes() {
            return maxArchiveBytes;
        }

        public void setMaxArchiveBytes(long maxArchiveBytes) {
            this.maxArchiveBytes = maxArchiveBytes;
        }

        public int getMaxEntries() {
            return maxEntries;
        }

        public void setMaxEntries(int maxEntries) {
            this.maxEntries = maxEntries;
        }

        public long getMaxEntryBytes() {
            return maxEntryBytes;
        }

        public void setMaxEntryBytes(long maxEntryBytes) {
            this.maxEntryBytes = maxEntryBytes;
        }

        public long getMaxTotalBytes() {
            return maxTotalBytes;
        }

        public void setMaxTotalBytes(long maxTotalBytes) {
            this.maxTotalBytes = maxTotalBytes;
        }

        public long getMaxDescriptorBytes() {
            return maxDescriptorBytes;
        }

        public void setMaxDescriptorBytes(long maxDescriptorBytes) {
            this.maxDescriptorBytes = maxDescriptorBytes;
        }

        public double getMaxCompressionRatio() {
            return maxCompressionRatio;
        }

        public void setMaxCompressionRatio(double maxCompressionRatio) {
            this.maxCompressionRatio = maxCompressionRatio;
        }
    }

    @Valid
    private Preview preview = new Preview();

    public Preview getPreview() {
        return preview;
    }

    public void setPreview(Preview preview) {
        this.preview = preview;
    }

    public static class Preview {

        @Min(1)
        private long maxCachedBytes = 4_294_967_296L;

        @Min(1)
        private int maxSessions = 64;

        @Min(1)
        private long expireAfterMinutes = 30;

        public long getMaxCachedBytes() {
            return maxCachedBytes;
        }

        public void setMaxCachedBytes(long maxCachedBytes) {
            this.maxCachedBytes = maxCachedBytes;
        }

        public int getMaxSessions() {
            return maxSessions;
        }

        public void setMaxSessions(int maxSessions) {
            this.maxSessions = maxSessions;
        }

        public long getExpireAfterMinutes() {
            return expireAfterMinutes;
        }

        public void setExpireAfterMinutes(long expireAfterMinutes) {
            this.expireAfterMinutes = expireAfterMinutes;
        }
    }
}
