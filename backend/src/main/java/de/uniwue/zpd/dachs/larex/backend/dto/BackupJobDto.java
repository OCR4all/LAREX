package de.uniwue.zpd.dachs.larex.backend.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class BackupJobDto {

    public enum JobType {
        DUMP,
        RESEED
    }

    public enum JobStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public enum PathRole {
        SOURCE,
        OUTPUT
    }

    public record ValidatePathRequest(
            @NotBlank(message = "Path is required")
            String path,
            PathRole role
    ) {
    }

    public record ValidatePathResponse(
            boolean valid,
            String normalizedPath,
            String errorMessage
    ) {
    }

    public record CreateJobRequest(
            JobType type,
            String sourcePath,
            @NotBlank(message = "outputPath is required")
            String outputPath,
            Map<String, String> workspaceMapping
    ) {
    }

    public record JobResponse(
            String id,
            JobType type,
            JobStatus status,
            String sourcePath,
            String outputPath,
            int progressPercent,
            long processedItems,
            long totalItems,
            String currentStep,
            String errorMessage,
            String resultPath,
            List<String> warnings,
            LocalDateTime created,
            LocalDateTime updated,
            LocalDateTime completedAt
    ) {
    }

    public record JobSummary(
            String id,
            JobType type,
            JobStatus status,
            int progressPercent,
            String currentStep,
            LocalDateTime created,
            LocalDateTime completedAt
    ) {
    }
}
