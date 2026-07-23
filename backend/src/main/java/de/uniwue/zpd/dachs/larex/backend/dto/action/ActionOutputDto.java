package de.uniwue.zpd.dachs.larex.backend.dto.action;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public final class ActionOutputDto {
    private ActionOutputDto() {}

    public record FileResponse(
            String id,
            String pageId,
            String fileName,
            String mimeType,
            long sizeBytes,
            String checksumSha256,
            LocalDateTime created
    ) {}

    public record OutputResponse(
            String id,
            String sourceRunId,
            String processorDefinitionId,
            String processorKey,
            String processorName,
            String createdByUserId,
            int fileCount,
            long totalSizeBytes,
            Integer retentionDays,
            LocalDateTime expiresAt,
            LocalDateTime completedAt,
            boolean shareEnabled,
            String shareSecretPrefix,
            LocalDateTime shareCreatedAt,
            LocalDateTime shareExpiresAt,
            LocalDateTime shareRevokedAt,
            LocalDateTime shareLastUsedAt,
            long shareDownloadCount,
            List<FileResponse> files,
            LocalDateTime created,
            LocalDateTime updated
    ) {}

    public record ShareRequest(
            @NotNull(message = "Share expiry is required")
            @Future(message = "Share expiry must be in the future")
            LocalDateTime expiresAt
    ) {}

    public record ShareResponse(
            String downloadUrl,
            String secret,
            LocalDateTime expiresAt,
            LocalDateTime createdAt
    ) {}
}
