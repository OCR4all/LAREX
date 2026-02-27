package de.uniwue.zpd.dachs.larex.backend.exception;

/**
 * Exception thrown when a file operation would exceed the workspace storage quota.
 */
public class StorageQuotaExceededException extends RuntimeException {
    
    private final String workspaceId;
    private final Long requiredBytes;
    private final Long availableBytes;
    private final Long quotaLimitBytes;
    
    public StorageQuotaExceededException(String workspaceId, Long requiredBytes, Long availableBytes, Long quotaLimitBytes) {
        super(String.format("Storage quota exceeded for workspace %s. Required: %d bytes, Available: %d bytes, Limit: %d bytes", 
              workspaceId, requiredBytes, availableBytes, quotaLimitBytes));
        this.workspaceId = workspaceId;
        this.requiredBytes = requiredBytes;
        this.availableBytes = availableBytes;
        this.quotaLimitBytes = quotaLimitBytes;
    }
    
    public StorageQuotaExceededException(String workspaceId, Long requiredBytes, Long availableBytes, Long quotaLimitBytes, String message) {
        super(message);
        this.workspaceId = workspaceId;
        this.requiredBytes = requiredBytes;
        this.availableBytes = availableBytes;
        this.quotaLimitBytes = quotaLimitBytes;
    }
    
    public String getWorkspaceId() {
        return workspaceId;
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
}