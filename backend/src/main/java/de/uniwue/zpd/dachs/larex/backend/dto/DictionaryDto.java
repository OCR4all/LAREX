package de.uniwue.zpd.dachs.larex.backend.dto;

import tools.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public class DictionaryDto {

    public enum VariantScope {
        ALL,
        PRIMARY
    }

    public enum ImportFormat {
        AUTO,
        TXT,
        CSV,
        TSV,
        JSON,
        TEI
    }

    public enum ImportMode {
        APPEND,
        REPLACE
    }

    public record CreateOrUpdateRequest(
            @NotBlank(message = "Name is required")
            @Size(max = 255, message = "Name must not exceed 255 characters")
            String name,

            @Size(max = 10_000, message = "Description must not exceed 10000 characters")
            String description,

            @Size(max = 50, message = "Cannot have more than 50 tags")
            List<String> tags,

            Boolean caseSensitive,
            String unicodeNormalization,
            Boolean locked
    ) {
    }

    public record SummaryResponse(
            String id,
            String name,
            String description,
            List<String> tags,
            boolean caseSensitive,
            String unicodeNormalization,
            boolean locked,
            long entryCount,
            LocalDateTime created,
            LocalDateTime updated,
            AuthorizationCapabilitiesDto.ResourceCapabilities capabilities
    ) {
    }

    public record Response(
            String id,
            String name,
            String description,
            List<String> tags,
            boolean caseSensitive,
            String unicodeNormalization,
            boolean locked,
            long entryCount,
            LocalDateTime created,
            LocalDateTime updated,
            AuthorizationCapabilitiesDto.ResourceCapabilities capabilities
    ) {
    }

    public record EntryCreateOrUpdateRequest(
            @NotBlank(message = "Form is required")
            String form,
            String sourceEntryKey,
            JsonNode metadata,
            Boolean fromEditor
    ) {
    }

    public record EntryResponse(
            String id,
            String form,
            String normalizedValue,
            String sourceEntryKey,
            JsonNode metadata,
            LocalDateTime created,
            LocalDateTime updated
    ) {
    }

    public record EntryPageResponse(
            List<EntryResponse> entries,
            long totalEntries,
            int totalPages,
            int page,
            int size
    ) {
    }

    public record PackageEntry(
            String form,
            String sourceEntryKey,
            JsonNode metadata
    ) {
    }

    public record PackagePayload(
            String name,
            String description,
            List<String> tags,
            Boolean caseSensitive,
            String unicodeNormalization,
            Boolean locked,
            List<PackageEntry> entries
    ) {
    }

    public record ImportResult(
            String dictionaryId,
            String dictionaryName,
            int importedEntryCount,
            int skippedEntryCount,
            boolean replacedExistingEntries,
            List<String> warnings,
            String message
    ) {
    }

    public record ProjectScope(
            @NotBlank(message = "Project ID is required")
            String projectId,
            List<String> pageIds
    ) {
    }

    public record ValidateAgainstSourcesRequest(
            @NotEmpty(message = "At least one source is required")
            List<@Valid ProjectScope> sources,
            VariantScope variantScope,
            Integer variantIndex,
            Boolean unindexedOnly
    ) {
    }

    public record CheckTokensRequest(
            @NotEmpty(message = "At least one token is required")
            List<@NotBlank(message = "Token must not be blank") String> tokens,
            Boolean includeSuggestions,
            Integer limit
    ) {
    }

    public record Suggestion(
            String display,
            String normalized,
            int distance
    ) {
    }

    public record TokenCheckResult(
            String token,
            String normalizedToken,
            boolean known,
            List<Suggestion> suggestions
    ) {
    }

    public record CheckTokensResponse(
            String dictionaryId,
            List<TokenCheckResult> results
    ) {
    }

    public record ValidateTokenPageRef(
            String projectId,
            String projectName,
            String pageId,
            String pageName
    ) {
    }

    public record ValidateTokenResult(
            String token,
            String normalizedToken,
            int occurrenceCount,
            List<ValidateTokenPageRef> pages,
            List<Suggestion> suggestions
    ) {
    }

    public record ValidateProjectResult(
            String projectId,
            String projectName,
            int analyzedPageCount,
            List<String> unknownTokens,
            int unknownTokenCount,
            List<String> unknownPageIds,
            int unknownPageCount,
            boolean valid
    ) {
    }

    public record ValidateAgainstSourcesResponse(
            boolean valid,
            int analyzedProjectCount,
            int analyzedPageCount,
            int analyzedTokenCount,
            int knownTokenCount,
            int unknownTokenCount,
            List<String> unknownTokens,
            List<ValidateProjectResult> projectResults,
            List<ValidateTokenResult> unknownTokenResults,
            String message
    ) {
    }
}
