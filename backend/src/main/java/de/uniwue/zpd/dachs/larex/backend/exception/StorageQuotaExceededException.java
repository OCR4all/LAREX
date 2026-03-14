package de.uniwue.zpd.dachs.larex.backend.exception;

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
        super(String.format(
                "Workspace storage quota exceeded. Uploads and imports are blocked until storage is freed or the quota is increased. Requested: %d bytes, Available: %d bytes.",
                requiredBytes,
                availableBytes
        ));
        this.workspaceId = workspaceId;
        this.blockedOperation = blockedOperation;
        this.requiredBytes = requiredBytes;
        this.availableBytes = availableBytes;
        this.quotaLimitBytes = quotaLimitBytes;
        this.currentUsageBytes = currentUsageBytes;
        this.reservedBytes = reservedBytes;
        this.usagePercentage = usagePercentage;
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
