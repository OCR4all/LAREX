package de.uniwue.zpd.dachs.larex.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public class IiifImportDto {

    public record PreviewRequest(
            @NotBlank(message = "Manifest URL is required")
            @Size(max = 2000, message = "Manifest URL must not exceed 2000 characters")
            String manifestUrl
    ) {}

    public record ManifestSummary(
            String id,
            String sourceUrl,
            String sourceType,
            String sourceName,
            String label,
            String provider,
            String thumbnailUrl,
            String presentationVersion
    ) {}

    public record Conflict(
            String canvasId,
            String conflictType,
            String derivedPageName,
            String existingPageId,
            String existingPageName,
            boolean existingIiifImage,
            String message
    ) {}

    public record CanvasPreview(
            String canvasId,
            String canvasLabel,
            int index,
            String pageName,
            boolean importable,
            String imageUrl,
            Long estimatedBytes,
            List<String> warnings,
            Conflict conflict
    ) {}

    public record PreviewResponse(
            String previewToken,
            ManifestSummary manifest,
            int totalCanvases,
            int importableCanvasCount,
            long estimatedStorageBytes,
            int unknownSizeCanvasCount,
            List<String> warnings,
            List<CanvasPreview> canvases
    ) {}

    public record Resolution(
            @NotBlank(message = "Canvas id is required")
            String canvasId,
            @NotBlank(message = "Action is required")
            String action,
            String pageName
    ) {}

    public record StartJobRequest(
            @NotBlank(message = "Preview token is required")
            String previewToken,
            List<Resolution> resolutions
    ) {}

    public record ItemResult(
            String canvasId,
            String canvasLabel,
            int index,
            String requestedPageName,
            String finalPageName,
            String action,
            String status,
            String pageId,
            String message
    ) {}

    public record JobResponse(
            String id,
            String projectId,
            String workspaceId,
            String sourceType,
            String sourceReference,
            String status,
            int totalCanvases,
            int processedCanvases,
            int skippedCanvases,
            int failedCanvases,
            int progressPercent,
            long estimatedStorageBytes,
            ManifestSummary manifest,
            List<String> warnings,
            List<ItemResult> results,
            String errorMessage,
            LocalDateTime created,
            LocalDateTime updated,
            LocalDateTime completedAt
    ) {}
}
