package de.uniwue.zpd.dachs.larex.backend.dto.action;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

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

    public record AdminOutputResponse(
            String id,
            String workspaceId,
            String projectId,
            String projectName,
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
            LocalDateTime created,
            LocalDateTime updated
    ) {}

    public record AdminOutputPageResponse(
            List<AdminOutputResponse> outputs,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}

    public record AdminCleanupRequest(
            @NotNull(message = "Older-than days is required")
            @Positive(message = "Older-than days must be positive")
            Integer olderThanDays,
            @Size(max = 10_000, message = "Cannot clean up more than 10000 outputs at once")
            List<@NotBlank(message = "Output IDs must not be blank") String> outputIds
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
