package de.uniwue.zpd.dachs.larex.backend.dto;

import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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

    public record ImageVariantPreview(
            String id,
            String fileName,
            String variant
    ) {}

    public record TextConfidenceStats(
            double min,
            double max,
            double mean,
            double median,
            int count
    ) {}

    public record Response(
            String id,
            String name,
            String description,
            List<String> tags,
            List<ResolvedTag> resolvedTags,
            LocalDateTime created,
            LocalDateTime updated,
            Integer sortOrder,
            TextConfidenceStats textConfidence,
            int xmlFileCount,
            int imageCount,
            Page.WorkflowState workflowState,
            boolean locked,
            String lockedReason,
            String thumbnailUrl,
            PageIndexingStatus indexingStatus,
            List<ImageVariantPreview> imageVariants
    ) {}

    public record SortOrderRequest(
            List<String> pageIds
    ) {}

    public record WorkflowStateRequest(
            @NotNull(message = "Workflow state is required")
            Page.WorkflowState workflowState
    ) {}

    public record BulkWorkflowStateRequest(
            @NotEmpty(message = "Page IDs are required")
            List<String> pageIds,
            @NotNull(message = "Workflow state is required")
            Page.WorkflowState workflowState
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
            LocalDateTime created,
            LocalDateTime updated
    ) {}

    // ============================================================================
    // Page Filter DTOs
    // ============================================================================

    /**
     * Request for filtering pages with multiple criteria.
     */
    public record XmlAttributeFilter(
            String elementName,
            String attributeName,
            String operator,
            String value
    ) {}

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
            /** Filter only pages that have at least one indexed metadata or PAGE XML comment */
            Boolean hasComments,
            /** Optional case-insensitive substring within dedicated metadata or PAGE XML comments */
            String commentText,
            /** Workflow states included by the page-state criterion */
            List<String> workflowStates,
            /** "with_xml", "without_xml", or null */
            String annotationPresence,
            /** Filter to pages with an incomplete subtask assigned to the authenticated user */
            Boolean onlyWithOpenSubtasks,
            /** Independent PAGE XML source-attribute predicates */
            List<XmlAttributeFilter> xmlAttributeFilters,
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
            long indexedXmlAttributePages,
            long pagesNeedingIndex
    ) {}

    public record XmlAttributeWithCount(
            String elementName,
            String attributeName,
            long pageCount
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

    /**
     * Response for matching text regions within a page.
     */
    public record MatchingTextRegionsResponse(
            String pageId,
            List<String> regionIds
    ) {}
}
