package de.uniwue.zpd.dachs.larex.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PageDto {

    public enum PageIndexingStatus {
        NOT_APPLICABLE,
        UNINDEXED,
        INDEXING,
        INDEXED
    }

    public record CreateOrUpdateRequest(
            @NotBlank(message = "Name is required")
            @Size(max = 255, message = "Name must not exceed 255 characters")
            String name,
            @Size(max = 1000, message = "Description must not exceed 1000 characters")
            String description,
            List<String> tags
    ) {}

    public record ResolvedTag(String id, String label, String color) {}

    public record Response(
            String id,
            String name,
            String description,
            List<String> tags,
            List<ResolvedTag> resolvedTags,
            LocalDateTime created,
            LocalDateTime updated,
            int xmlFileCount,
            int imageCount,
            boolean locked,
            String lockedReason,
            String thumbnailUrl,
            PageIndexingStatus indexingStatus
    ) {}

    public record ImageResponse(
            String id,
            String fileName,
            String filePath,
            String mimeType,
            Long fileSize,
            String variant,
            String baseName,
            String thumbnailPath,
            LocalDateTime created
    ) {}

    public record XmlResponse(
            String id,
            String fileName,
            String filePath,
            String mimeType,
            Long fileSize,
            String variant,
            String baseName,
            String schema,
            String schemaVersion,
            LocalDateTime created
    ) {}

    // ============================================================================
    // Page Filter DTOs
    // ============================================================================

    /**
     * Request for filtering pages with multiple criteria.
     */
    public record FilterRequest(
            /** Text content substring to search for */
            String textContent,
            /** Canonical label filter tokens */
            List<String> labelIds,
            /** Tag IDs to filter by */
            List<String> tags,
            /** Minimum confidence bound (inclusive) */
            Double confidenceMin,
            /** Maximum confidence bound (inclusive) */
            Double confidenceMax,
            /** PAGE XML element types with @conf to include */
            List<String> confidenceElementTypes,
            /** Global operator for combining all filters: "and" or "or" (default: "or") */
            String filterOperator
    ) {}

    /**
     * Response containing filtered page IDs.
     */
    public record FilterResponse(
            /** Set of page IDs matching the filter criteria */
            Set<String> pageIds,
            /** Total count of matching pages */
            int count
    ) {}

    /**
     * Response containing index statistics for a project.
     */
    public record IndexStatsResponse(
            long totalPages,
            long indexedTextContentPages,
            long indexedLabelPages,
            long pagesNeedingIndex
    ) {}

    /**
     * Response containing available labels with page counts.
     */
    public record LabelWithCount(
            String labelId,
            long pageCount
    ) {}

    /**
     * Response for matching text lines within a page.
     */
    public record MatchingTextLinesResponse(
            String pageId,
            List<String> textLineIds
    ) {}
}
