package de.uniwue.zpd.dachs.larex.backend.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class UtilityPackageDto {

    public enum UtilityType {
        CODEC,
        DICTIONARY,
        LABEL_SET,
        TAG_SET,
        NORMALIZATION_PROFILE,
        VALIDATION_RULESET,
        VIRTUAL_KEYBOARD,
        BOARD_THEME
    }

    public record ResourceSelector(
            UtilityType type,
            List<String> ids
    ) {
    }

    public record ExportRequest(
            List<@Valid ResourceSelector> selectors,
            Boolean includeAll
    ) {
        public boolean includeAllResolved() {
            return Boolean.TRUE.equals(includeAll);
        }
    }

    public record PackageMeta(
            String schemaVersion,
            LocalDateTime exportedAt,
            String workspaceId,
            String workspaceName
    ) {
    }

    public record UtilityResource(
            UtilityType type,
            String sourceId,
            String name,
            LocalDateTime sourceCreated,
            LocalDateTime sourceUpdated,
            JsonNode payload
    ) {
    }

    public record UtilityPackage(
            PackageMeta meta,
            List<UtilityResource> resources
    ) {
    }

    public record ImportRequest(
            @NotBlank(message = "Package JSON is required")
            String content
    ) {
    }

    public record ImportedResource(
            UtilityType type,
            String sourceId,
            String targetId,
            String sourceName,
            String targetName,
            String action,
            String reason
    ) {
    }

    public record ImportResult(
            String workspaceId,
            int importedCount,
            int reusedCount,
            List<ImportedResource> resources,
            List<String> warnings,
            Map<String, String> sourceToTargetIds
    ) {
    }
}
