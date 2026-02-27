package de.uniwue.zpd.dachs.larex.backend.dto;

import de.uniwue.zpd.dachs.larex.backend.entity.ImportJob.CopyMode;
import de.uniwue.zpd.dachs.larex.backend.entity.ImportJob.ImportJobStatus;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;

public class ImportJobDto {

    public record ValidatePathRequest(
            @NotBlank(message = "Path is required")
            String path
    ) {}

    public record ValidatePathResponse(
            boolean valid,
            String normalizedPath,
            String errorMessage
    ) {}

    public record ScanRequest(
            @NotBlank(message = "Path is required")
            String path
    ) {}

    public record ScanResponse(
            String path,
            int imageCount,
            int xmlCount,
            long totalSizeBytes,
            List<ScanFileInfo> files,
            List<String> conflicts,
            List<String> warnings,
            boolean quotaExceeded,
            long availableQuotaBytes
    ) {}

    public record ScanFileInfo(
            String fileName,
            String relativePath,
            long fileSize,
            String fileType,
            String baseName,
            String variant,
            boolean hasConflict,
            String conflictType
    ) {}

    public record CreateJobRequest(
            @NotBlank(message = "Path is required")
            String sourcePath,

            @NotBlank(message = "Project ID is required")
            String projectId,

            boolean overwriteExisting,

            CopyMode copyMode
    ) {}

    public record JobResponse(
            String id,
            String projectId,
            String workspaceId,
            String createdByUserId,
            String sourcePath,
            ImportJobStatus status,
            int totalFiles,
            int processedFiles,
            int skippedFiles,
            int failedFiles,
            long totalBytes,
            int progressPercent,
            CopyMode copyMode,
            boolean overwriteExisting,
            String errorMessage,
            LocalDateTime created,
            LocalDateTime updated,
            LocalDateTime completedAt
    ) {}

    public record JobSummaryResponse(
            String id,
            String projectId,
            String sourcePath,
            ImportJobStatus status,
            int totalFiles,
            int processedFiles,
            int failedFiles,
            int progressPercent,
            LocalDateTime created,
            LocalDateTime completedAt
    ) {}

    public record ProgressUpdate(
            String jobId,
            ImportJobStatus status,
            int totalFiles,
            int processedFiles,
            int skippedFiles,
            int failedFiles,
            int progressPercent,
            String currentFileName,
            String message
    ) {}

    public record JobLogResponse(
            String jobId,
            String importLog
    ) {}
}
