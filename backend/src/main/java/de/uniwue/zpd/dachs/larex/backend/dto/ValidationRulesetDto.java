package de.uniwue.zpd.dachs.larex.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public class ValidationRulesetDto {

    public enum Severity {
        INFO,
        WARNING,
        ERROR
    }

    public enum VariantScope {
        ALL,
        PRIMARY
    }

    public record Rule(
            String id,
            @NotBlank(message = "Rule name is required")
            @Size(max = 255, message = "Rule name must not exceed 255 characters")
            String name,
            @Size(max = 1000, message = "Rule description must not exceed 1000 characters")
            String description,
            Severity severity,
            @NotBlank(message = "Rule pattern is required")
            @Size(max = 4000, message = "Rule pattern must not exceed 4000 characters")
            String pattern,
            @Size(max = 32, message = "Rule flags must not exceed 32 characters")
            String flags,
            @Size(max = 1000, message = "Rule message must not exceed 1000 characters")
            String message
    ) {}

    public record CreateOrUpdateRequest(
            @NotBlank(message = "Name is required")
            @Size(max = 255, message = "Name must not exceed 255 characters")
            String name,
            @Size(max = 10_000, message = "Description must not exceed 10000 characters")
            String description,
            @Size(max = 50, message = "Cannot have more than 50 tags")
            List<String> tags,
            @NotEmpty(message = "At least one rule is required")
            List<@Valid Rule> rules
    ) {}

    public record Response(
            String id,
            String name,
            String description,
            List<String> tags,
            List<Rule> rules,
            LocalDateTime created,
            LocalDateTime updated,
            AuthorizationCapabilitiesDto.ResourceCapabilities capabilities
    ) {}

    public record SummaryResponse(
            String id,
            String name,
            String description,
            List<String> tags,
            int ruleCount,
            LocalDateTime created,
            LocalDateTime updated,
            AuthorizationCapabilitiesDto.ResourceCapabilities capabilities
    ) {}

    public record ProjectScope(
            @NotBlank(message = "Project ID is required")
            String projectId,
            List<String> pageIds
    ) {}

    public record ValidateAgainstSourcesRequest(
            @NotEmpty(message = "At least one source is required")
            List<@Valid ProjectScope> sources,
            VariantScope variantScope,
            Integer variantIndex,
            Boolean unindexedOnly
    ) {}

    public record RulePageRef(
            String projectId,
            String projectName,
            String pageId,
            String pageName
    ) {}

    public record RuleResult(
            String ruleId,
            String ruleName,
            Severity severity,
            String message,
            int occurrenceCount,
            List<String> matchedSamples,
            List<RulePageRef> pages
    ) {}

    public record ValidateAgainstSourcesResponse(
            boolean valid,
            int analyzedProjectCount,
            int analyzedPageCount,
            int totalOccurrenceCount,
            List<RuleResult> ruleResults,
            String message
    ) {}
}
