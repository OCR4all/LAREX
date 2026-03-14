package de.uniwue.zpd.dachs.larex.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record StorageQuotaErrorResponseDto(
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        String code,
        String blockedOperation,
        String workspaceId,
        Long requiredBytes,
        Long quotaLimitBytes,
        Long currentUsageBytes,
        Long reservedBytes,
        Long availableBytes,
        Double usagePercentage
) {
    public StorageQuotaErrorResponseDto(
            int status,
            String error,
            String message,
            String path,
            String code,
            String blockedOperation,
            String workspaceId,
            Long requiredBytes,
            Long quotaLimitBytes,
            Long currentUsageBytes,
            Long reservedBytes,
            Long availableBytes,
            Double usagePercentage
    ) {
        this(
                LocalDateTime.now(),
                status,
                error,
                message,
                path,
                code,
                blockedOperation,
                workspaceId,
                requiredBytes,
                quotaLimitBytes,
                currentUsageBytes,
                reservedBytes,
                availableBytes,
                usagePercentage
        );
    }
}
