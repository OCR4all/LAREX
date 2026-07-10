package de.uniwue.zpd.dachs.larex.backend.exception;

import java.util.Locale;

/**
 * Exception thrown when a file operation would exceed the workspace storage quota.
 */
public class StorageQuotaExceededException extends RuntimeException {

    public static final String ERROR_CODE = "STORAGE_QUOTA_EXCEEDED";
    
    private final String workspaceId;
    private final String blockedOperation;
    private final Long requiredBytes;
    private final Long availableBytes;
    private final Long quotaLimitBytes;
    private final Long currentUsageBytes;
    private final Long reservedBytes;
    private final Double usagePercentage;
    
    public StorageQuotaExceededException(
            String workspaceId,
            String blockedOperation,
            Long requiredBytes,
            Long availableBytes,
            Long quotaLimitBytes,
            Long currentUsageBytes,
            Long reservedBytes,
            Double usagePercentage
    ) {
        super(buildMessage(blockedOperation, requiredBytes, availableBytes));
        this.workspaceId = workspaceId;
        this.blockedOperation = blockedOperation;
        this.requiredBytes = requiredBytes;
        this.availableBytes = availableBytes;
        this.quotaLimitBytes = quotaLimitBytes;
        this.currentUsageBytes = currentUsageBytes;
        this.reservedBytes = reservedBytes;
        this.usagePercentage = usagePercentage;
    }

    private static String buildMessage(String blockedOperation, Long requiredBytes, Long availableBytes) {
        String operation = "iiif-import-job".equals(blockedOperation) ? "This IIIF import" : "This operation";
        return String.format(
                "Not enough workspace storage. %s needs %s, but only %s is available. Free up storage or ask an administrator to increase the workspace quota.",
                operation,
                formatBytes(requiredBytes),
                formatBytes(availableBytes)
        );
    }

    private static String formatBytes(Long bytes) {
        if (bytes == null || bytes <= 0) {
            return "0 B";
        }
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return unit == 0
                ? String.format(Locale.ROOT, "%.0f %s", value, units[unit])
                : String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }
    
    public String getWorkspaceId() {
        return workspaceId;
    }

    public String getBlockedOperation() {
        return blockedOperation;
    }
    
    public Long getRequiredBytes() {
        return requiredBytes;
    }
    
    public Long getAvailableBytes() {
        return availableBytes;
    }
    
    public Long getQuotaLimitBytes() {
        return quotaLimitBytes;
    }

    public Long getCurrentUsageBytes() {
        return currentUsageBytes;
    }

    public Long getReservedBytes() {
        return reservedBytes;
    }

    public Double getUsagePercentage() {
        return usagePercentage;
    }
}
