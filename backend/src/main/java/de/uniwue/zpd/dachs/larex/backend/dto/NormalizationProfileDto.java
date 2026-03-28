package de.uniwue.zpd.dachs.larex.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public class NormalizationProfileDto {

    public enum VariantScope {
        ALL,
        PRIMARY
    }

    public record ReplacementRule(
            @NotBlank(message = "Search value is required")
            @Size(max = 10_000, message = "Search value must not exceed 10000 characters")
            String search,
            @Size(max = 10_000, message = "Replacement value must not exceed 10000 characters")
            String replacement,
            Boolean regex
    ) {}

    public record CreateOrUpdateRequest(
            @NotBlank(message = "Name is required")
            @Size(max = 255, message = "Name must not exceed 255 characters")
            String name,
            @Size(max = 10_000, message = "Description must not exceed 10000 characters")
            String description,
            @Size(max = 50, message = "Cannot have more than 50 tags")
            List<String> tags,
            String unicodeNormalization,
            Boolean collapseWhitespace,
            Boolean trimText,
            Boolean dehyphenateLineBreaks,
            Boolean mapLongSToS,
            Boolean expandCommonLigatures,
            Boolean normalizeQuotes,
            Boolean normalizeDashes,
            Boolean normalizeEllipsis,
            List<@Valid ReplacementRule> replacementRules
    ) {}

    public record Response(
            String id,
            String name,
            String description,
            List<String> tags,
            String unicodeNormalization,
            boolean collapseWhitespace,
            boolean trimText,
            boolean dehyphenateLineBreaks,
            boolean mapLongSToS,
            boolean expandCommonLigatures,
            boolean normalizeQuotes,
            boolean normalizeDashes,
            boolean normalizeEllipsis,
            List<ReplacementRule> replacementRules,
            LocalDateTime created,
            LocalDateTime updated,
            AuthorizationCapabilitiesDto.ResourceCapabilities capabilities
    ) {}

    public record SummaryResponse(
            String id,
            String name,
            String description,
            List<String> tags,
            String unicodeNormalization,
            LocalDateTime created,
            LocalDateTime updated,
            AuthorizationCapabilitiesDto.ResourceCapabilities capabilities
    ) {}

    public record ProjectScope(
            @NotBlank(message = "Project ID is required")
            String projectId,
            List<String> pageIds
    ) {}

    public record NormalizeTarget(
            @NotBlank(message = "Page ID is required")
            String pageId,
            String textLineId,
            String regionId,
            Integer variantIndex
    ) {}

    public record NormalizeSourcesRequest(
            @NotEmpty(message = "At least one source is required")
            List<@Valid ProjectScope> sources,
            VariantScope variantScope,
            Integer variantIndex,
            Boolean unindexedOnly,
            List<@Valid NormalizeTarget> targets
    ) {}

    public record NormalizeMatch(
            String key,
            String label,
            String description,
            boolean manual,
            boolean regex
    ) {}

    public record NormalizePreview(
            String projectId,
            String projectName,
            String pageId,
            String pageName,
            String textLineId,
            String regionId,
            Integer variantIndex,
            String originalText,
            String normalizedText,
            List<NormalizeMatch> matchedRules
    ) {}

    public record NormalizeSourcesResponse(
            int analyzedProjectCount,
            int analyzedPageCount,
            int analyzedRowCount,
            int changedRowCount,
            int changedPageCount,
            List<NormalizePreview> previews,
            String message
    ) {}

    public record ApplySourcesResponse(
            int analyzedProjectCount,
            int analyzedPageCount,
            int targetedRowCount,
            int changedRowCount,
            int changedPageCount,
            String message
    ) {}
}
