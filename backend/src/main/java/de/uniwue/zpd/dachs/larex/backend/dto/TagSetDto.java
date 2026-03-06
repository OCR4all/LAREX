package de.uniwue.zpd.dachs.larex.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public class TagSetDto {

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record Meta(
            @NotBlank(message = "Name is required")
            @Size(max = 255, message = "Name must not exceed 255 characters")
            String name,

            @Size(max = 10_000, message = "Description must not exceed 10000 characters")
            String description,

            @Size(max = 100, message = "Cannot have more than 100 tags")
            List<String> tags
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record TagNode(
            @NotBlank(message = "Tag ID is required")
            String id,

            @NotBlank(message = "Tag title is required")
            @Size(max = 255, message = "Tag title must not exceed 255 characters")
            String title,

            @Size(max = 10_000, message = "Tag description must not exceed 10000 characters")
            String description,

            @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Color must be a hex string like #RRGGBB")
            String color,

            @Valid
            List<TagNode> children
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record CreateOrUpdateRequest(
            @NotNull(message = "Meta is required")
            @Valid
            Meta meta,

            @NotNull(message = "Tags is required")
            @Valid
            List<TagNode> tags
    ) {}

    public record Response(
            String id,
            Meta meta,
            List<TagNode> tags,
            int totalTagCount,
            LocalDateTime created,
            LocalDateTime updated,
            AuthorizationCapabilitiesDto.ResourceCapabilities capabilities
    ) {}

    public record SummaryResponse(
            String id,
            Meta meta,
            int tagCount,
            LocalDateTime created,
            LocalDateTime updated,
            AuthorizationCapabilitiesDto.ResourceCapabilities capabilities
    ) {}

    public record FlattenedTag(
            String id,
            String title,
            String path,
            String color,
            List<String> ancestorIds,
            List<String> descendantIds
    ) {}

    public record ValidationResult(
            boolean valid,
            List<String> invalidTags,
            List<String> validTags,
            String message
    ) {}
}
