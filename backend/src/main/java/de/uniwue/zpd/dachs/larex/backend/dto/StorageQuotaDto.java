package de.uniwue.zpd.dachs.larex.backend.dto;

import java.time.LocalDateTime;

/**
 * DTO for workspace storage quota information
 */
public record StorageQuotaDto(
    String id,
    String workspaceId,
    Long quotaLimitBytes,
    Long currentUsageBytes,
    Boolean isCustom,
    LocalDateTime created,
    LocalDateTime updated,
    
    // Computed fields
    Long availableBytes,
    Double usagePercentage,
    Boolean isQuotaExceeded,
    String quotaLimitFormatted,
    String currentUsageFormatted,
    String availableBytesFormatted
) {
    
    /**
     * Factory method to create DTO from entity
     */
    public static StorageQuotaDto fromEntity(de.uniwue.zpd.dachs.larex.backend.entity.WorkspaceStorageQuota entity) {
        return new StorageQuotaDto(
            entity.getId(),
            entity.getWorkspaceId(),
            entity.getQuotaLimitBytes(),
            entity.getCurrentUsageBytes(),
            entity.getIsCustom(),
            entity.getCreated(),
            entity.getUpdated(),
            entity.getAvailableBytes(),
            entity.getUsagePercentage(),
            entity.isQuotaExceeded(),
            formatBytes(entity.getQuotaLimitBytes()),
            formatBytes(entity.getCurrentUsageBytes()),
            formatBytes(entity.getAvailableBytes())
        );
    }
    
    /**
     * Format bytes in human-readable format
     */
    private static String formatBytes(Long bytes) {
        if (bytes == null || bytes == 0) return "0 B";
        
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double size = bytes.doubleValue();
        
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        
        return String.format("%.1f %s", size, units[unitIndex]);
    }
    
    /**
     * Create DTO for quota update requests
     */
    public static record UpdateQuotaRequest(
        Long quotaLimitBytes,
        Boolean isCustom
    ) {}
    
    /**
     * Create DTO for admin quota management
     */
    public static record AdminQuotaDto(
        String workspaceId,
        String workspaceName,
        Long quotaLimitBytes,
        Long currentUsageBytes,
        Double usagePercentage,
        Boolean isCustom,
        Boolean isQuotaExceeded,
        String quotaLimitFormatted,
        String currentUsageFormatted
    ) {}
}