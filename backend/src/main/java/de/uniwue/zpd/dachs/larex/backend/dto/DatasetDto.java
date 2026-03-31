package de.uniwue.zpd.dachs.larex.backend.dto;

import de.uniwue.zpd.dachs.larex.backend.entity.Dataset;
import de.uniwue.zpd.dachs.larex.backend.entity.DatasetItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class DatasetDto {

    public record CreateOrUpdateRequest(
            @NotBlank(message = "Name is required")
            @Size(max = 255, message = "Name must not exceed 255 characters")
            String name,
            @Size(max = 2000, message = "Description must not exceed 2000 characters")
            String description,
            @Size(max = 50, message = "Cannot have more than 50 tags")
            List<String> tags,
            Dataset.SplitTemplate splitTemplate,
            Dataset.SplitAlgorithm splitAlgorithm,
            Long splitSeed,
            Integer trainPercentage,
            Integer valPercentage,
            Integer testPercentage,
            List<String> stratifyTagIds
    ) {}

    public record AddItemsRequest(
            @NotEmpty(message = "At least one item is required")
            List<@Valid AddItemRequest> items
    ) {}

    public record AddItemRequest(
            @NotBlank(message = "sourceProjectId is required")
            String sourceProjectId,
            @NotBlank(message = "sourcePageId is required")
            String sourcePageId,
            DatasetItem.Mode mode,
            @NotBlank(message = "sourceXmlId is required")
            String sourceXmlId,
            @NotEmpty(message = "At least one source image is required")
            List<String> sourceImageIds
    ) {}

    public record UpdateItemRequest(
            DatasetItem.Split assignedSplit,
            Boolean pinned
    ) {}

    public record GenerateSplitRequest(
            Dataset.SplitTemplate splitTemplate,
            Dataset.SplitAlgorithm splitAlgorithm,
            Long splitSeed,
            Integer trainPercentage,
            Integer valPercentage,
            Integer testPercentage,
            List<String> stratifyTagIds
    ) {}

    public record CreateReleaseRequest(
            @Size(max = 128, message = "Release tag must not exceed 128 characters")
            String versionTag,
            @Size(max = 4000, message = "Release notes must not exceed 4000 characters")
            String notes
    ) {}

    public record SummaryResponse(
            String id,
            String workspaceId,
            String name,
            String description,
            List<String> tags,
            LocalDateTime created,
            LocalDateTime updated,
            long itemCount,
            StatsResponse stats,
            Dataset.ValidationStatus lastValidationStatus,
            Dataset.ExportStatus lastExportStatus,
            LocalDateTime lastValidationAt,
            LocalDateTime lastExportedAt,
            AuthorizationCapabilitiesDto.DatasetCapabilities capabilities
    ) {}

    public record DetailResponse(
            String id,
            String workspaceId,
            String name,
            String description,
            List<String> tags,
            LocalDateTime created,
            LocalDateTime updated,
            Dataset.SplitTemplate splitTemplate,
            Dataset.SplitAlgorithm splitAlgorithm,
            Long splitSeed,
            Integer trainPercentage,
            Integer valPercentage,
            Integer testPercentage,
            List<String> stratifyTagIds,
            Dataset.ValidationStatus lastValidationStatus,
            Dataset.ExportStatus lastExportStatus,
            LocalDateTime lastValidationAt,
            LocalDateTime lastExportedAt,
            List<String> lastValidationWarnings,
            StatsResponse stats,
            List<ItemResponse> items,
            List<ReleaseSummaryResponse> releases,
            AuthorizationCapabilitiesDto.DatasetCapabilities capabilities
    ) {}

    public record ItemResponse(
            String id,
            String sourceProjectId,
            String sourceProjectName,
            String sourcePageId,
            String sourcePageName,
            List<String> sourcePageTags,
            DatasetItem.Mode mode,
            String selectedSourceXmlId,
            String selectedSourceXmlFileName,
            List<String> selectedSourceImageIds,
            DatasetItem.Split assignedSplit,
            boolean manualSplit,
            boolean pinned,
            DatasetItem.Status status,
            String brokenReason,
            LocalDateTime copiedAt,
            LocalDateTime created,
            LocalDateTime updated
    ) {}

    public record ValidationIssue(
            String itemId,
            String sourcePageName,
            String reason
    ) {}

    public record ValidationResponse(
            Dataset.ValidationStatus status,
            StatsResponse stats,
            List<String> warnings,
            List<ValidationIssue> issues
    ) {}

    public record ReleaseSummaryResponse(
            String id,
            Integer versionNumber,
            String versionTag,
            String notes,
            DatasetReleaseStatus status,
            Dataset.ValidationStatus validationStatus,
            String failureReason,
            long itemCount,
            String packageFileName,
            Long packageFileSize,
            String packageChecksumSha256,
            String manifestChecksumSha256,
            String createdByUserId,
            LocalDateTime sourceDatasetUpdatedAt,
            LocalDateTime created,
            LocalDateTime updated
    ) {}

    public enum DatasetReleaseStatus {
        CREATING,
        READY,
        FAILED
    }

    public record StatsResponse(
            long totalItems,
            long linkedItems,
            long copiedItems,
            long brokenItems,
            Map<String, Long> countsBySplit,
            Map<String, Long> countsBySourceProject,
            Map<String, Long> countsByMode,
            Map<String, Long> countsByTag
    ) {}
}
