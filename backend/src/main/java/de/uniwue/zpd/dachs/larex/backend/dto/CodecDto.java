package de.uniwue.zpd.dachs.larex.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public class CodecDto {

    public enum VariantScope {
        ALL,
        PRIMARY
    }

    public record CreateOrUpdateRequest(
            @NotBlank(message = "Name is required")
            @Size(max = 255, message = "Name must not exceed 255 characters")
            String name,

            @Size(max = 10_000, message = "Description must not exceed 10000 characters")
            String description,

            @Size(max = 50, message = "Cannot have more than 50 tags")
            List<String> tags,

            @Size(max = 10000, message = "Codec cannot exceed 10000 characters")
            List<String> codec
    ) {}

    public record Response(
            String id,
            String name,
            String description,
            List<String> tags,
            List<String> codec,
            int characterCount,
            LocalDateTime created,
            LocalDateTime updated,
            AuthorizationCapabilitiesDto.ResourceCapabilities capabilities
    ) {}

    public record SummaryResponse(
            String id,
            String name,
            String description,
            List<String> tags,
            int characterCount,
            LocalDateTime created,
            LocalDateTime updated,
            AuthorizationCapabilitiesDto.ResourceCapabilities capabilities
    ) {}

    public record AddCharacterRequest(
            @NotBlank(message = "Character is required")
            String character
    ) {}

    public record ProjectScope(
            @NotBlank(message = "Project ID is required")
            String projectId,
            List<String> pageIds
    ) {}

    public record GenerateFromSourcesRequest(
            @NotEmpty(message = "At least one source is required")
            List<@Valid ProjectScope> sources,
            String targetCodecId,
            String newCodecName,
            String newCodecDescription,
            @Size(max = 50, message = "Cannot have more than 50 tags")
            List<String> newCodecTags,
            VariantScope variantScope,
            Integer variantIndex,
            Boolean unindexedOnly,
            Boolean includeWhitespace
    ) {}

    public record GenerateFromSourcesResponse(
            Response codec,
            boolean createdNewCodec,
            int analyzedProjectCount,
            int analyzedPageCount,
            int extractedCharacterCount,
            int addedCharacterCount,
            String message
    ) {}

    public record ValidateAgainstSourcesRequest(
            @NotEmpty(message = "At least one source is required")
            List<@Valid ProjectScope> sources,
            VariantScope variantScope,
            Integer variantIndex,
            Boolean unindexedOnly,
            Boolean includeWhitespace
    ) {}

    public record ValidateProjectResult(
            String projectId,
            String projectName,
            int analyzedPageCount,
            List<String> missingCharacters,
            int missingCharacterCount,
            List<String> missingPageIds,
            int missingPageCount,
            boolean valid
    ) {}

    public record ValidateCharacterPageRef(
            String projectId,
            String projectName,
            String pageId,
            String pageName
    ) {}

    public record ValidateCharacterResult(
            String character,
            List<ValidateCharacterPageRef> pages
    ) {}

    public record ValidateAgainstSourcesResponse(
            boolean valid,
            List<String> missingCharacters,
            int missingCharacterCount,
            int analyzedProjectCount,
            int analyzedPageCount,
            List<ValidateProjectResult> projectResults,
            List<ValidateCharacterResult> missingCharacterResults,
            String message
    ) {}
}
