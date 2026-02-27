package de.uniwue.zpd.dachs.larex.backend.dto;

import de.uniwue.zpd.dachs.larex.backend.entity.UploadSession.UploadSessionStatus;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSessionFile.UploadFileStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;
import java.util.List;

public class UploadSessionDto {

    public record CreateSessionRequest(
            @NotEmpty(message = "Files list is required")
            @Valid
            List<FileMetadata> files
    ) {}

    public record FileMetadata(
            @NotBlank(message = "File name is required")
            String fileName,

            @Positive(message = "File size must be positive")
            long fileSize,

            String mimeType,

            String baseName,

            String variant
    ) {}

    public record SessionResponse(
            String id,
            String projectId,
            String workspaceId,
            UploadSessionStatus status,
            int totalFiles,
            int processedFiles,
            int failedFiles,
            long totalBytes,
            long processedBytes,
            int progressPercent,
            List<FileResponse> files,
            String errorMessage,
            LocalDateTime created,
            LocalDateTime updated,
            LocalDateTime completedAt
    ) {}

    public record SessionSummaryResponse(
            String id,
            String projectId,
            UploadSessionStatus status,
            int totalFiles,
            int processedFiles,
            int failedFiles,
            int progressPercent,
            LocalDateTime created,
            LocalDateTime updated,
            LocalDateTime completedAt
    ) {}

    public record FileResponse(
            String id,
            String originalFileName,
            long fileSize,
            String mimeType,
            String baseName,
            String variant,
            UploadFileStatus status,
            int chunkCount,
            int chunksReceived,
            String errorMessage,
            String createdPageId,
            String createdPageImageId,
            String conflictType,
            String conflictResolution
    ) {}

    public record UploadChunkRequest(
            @NotNull(message = "Chunk index is required")
            Integer chunkIndex,

            @NotNull(message = "Total chunks is required")
            Integer totalChunks
    ) {}

    public record UploadChunkResponse(
            String fileId,
            int chunksReceived,
            int totalChunks,
            boolean complete
    ) {}

    public record FinalizeSessionRequest(
            List<ConflictResolution> conflictResolutions
    ) {}

    public record ConflictResolution(
            @NotBlank(message = "File ID is required")
            String fileId,

            @NotBlank(message = "Resolution is required")
            String resolution
    ) {}

    public record ProgressUpdate(
            String sessionId,
            UploadSessionStatus status,
            int totalFiles,
            int processedFiles,
            int failedFiles,
            long totalBytes,
            long processedBytes,
            int progressPercent,
            String currentFileName,
            String message
    ) {}

    public record FileProgressUpdate(
            String sessionId,
            String fileId,
            String fileName,
            UploadFileStatus status,
            String createdPageId,
            String createdPageImageId,
            String errorMessage
    ) {}
}
