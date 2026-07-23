package de.uniwue.zpd.dachs.larex.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

import static de.uniwue.zpd.dachs.larex.backend.dto.PageDto.ResolvedTag;

public class ProjectDto {

    public record CreateOrUpdateRequest(
            @NotBlank(message = "Name is required")
            @Size(max = 255, message = "Name must not exceed 255 characters")
            String name,
            @Size(max = 1000, message = "Description must not exceed 1000 characters")
            String description,
            @Size(max = 50, message = "Cannot have more than 50 tags")
            List<String> tags,
            String codecId,
            String labelSetId,
            String dictionaryId,
            String tagSetId,
            String normalizationProfileId,
            String validationRulesetId,
            String virtualKeyboardId,
            Boolean allowCodecOverride,
            Boolean allowDictionaryOverride,
            Boolean allowVirtualKeyboardOverride,
            Boolean allowLabelSetOverride,
            Boolean allowTagSetOverride,
            Boolean allowNormalizationProfileOverride,
            Boolean allowValidationRulesetOverride,
            Integer defaultGtIndex,
            List<Integer> defaultRecognitionIndices,
            @Positive(message = "Output retention days must be positive")
            Integer outputRetentionDays
    ) {}

    public record ToolkitPresetsRequest(
            String codecId,
            String labelSetId,
            String dictionaryId,
            String tagSetId,
            String normalizationProfileId,
            String validationRulesetId,
            String virtualKeyboardId,
            Boolean allowCodecOverride,
            Boolean allowDictionaryOverride,
            Boolean allowVirtualKeyboardOverride,
            Boolean allowLabelSetOverride,
            Boolean allowTagSetOverride,
            Boolean allowNormalizationProfileOverride,
            Boolean allowValidationRulesetOverride
    ) {}

    public record Response(
            String id,
            String name,
            String description,
            List<String> tags,
            List<ResolvedTag> resolvedTags,
            LocalDateTime created,
            LocalDateTime updated,
            int pageCount,
            int completedPageCount,
            int completionPercentage,
            boolean isStarred,
            Long storageUsedBytes,
            String storageUsedFormatted,
            boolean locked,
            String lockedReason,
            String codecId,
            String labelSetId,
            String dictionaryId,
            String tagSetId,
            String normalizationProfileId,
            String validationRulesetId,
            String virtualKeyboardId,
            boolean allowCodecOverride,
            boolean allowDictionaryOverride,
            boolean allowVirtualKeyboardOverride,
            boolean allowLabelSetOverride,
            boolean allowTagSetOverride,
            boolean allowNormalizationProfileOverride,
            boolean allowValidationRulesetOverride,
            Integer defaultGtIndex,
            List<Integer> defaultRecognitionIndices,
            Integer outputRetentionDays,
            AuthorizationCapabilitiesDto.ProjectCapabilities capabilities
    ) {
        private static String formatBytes(Long bytes) {
            if (bytes == null || bytes == 0) return "0 B";
            String[] units = {"B", "KB", "MB", "GB", "TB"};
            int unitIndex = 0;
            double size = bytes.doubleValue();
            while (size >= 1024 && unitIndex < units.length - 1) {
                size /= 1024;
                unitIndex++;
            }
            return String.format("%.1f %s", size, units[unitIndex]);
        }

        public static Response of(String id, String name, String description, List<String> tags,
                                  List<ResolvedTag> resolvedTags,
                                  LocalDateTime created, LocalDateTime updated, int pageCount,
                                  int completedPageCount, int completionPercentage,
                                  boolean isStarred, Long storageUsedBytes, boolean locked, String lockedReason,
                                  String codecId, String labelSetId, String dictionaryId, String tagSetId,
                                  String normalizationProfileId, String validationRulesetId,
                                  String virtualKeyboardId,
                                  boolean allowCodecOverride, boolean allowDictionaryOverride,
                                  boolean allowVirtualKeyboardOverride, boolean allowLabelSetOverride,
                                  boolean allowTagSetOverride, boolean allowNormalizationProfileOverride,
                                  boolean allowValidationRulesetOverride,
                                  Integer defaultGtIndex, List<Integer> defaultRecognitionIndices,
                                  Integer outputRetentionDays,
                                  AuthorizationCapabilitiesDto.ProjectCapabilities capabilities) {
            return new Response(id, name, description, tags, resolvedTags, created, updated, pageCount,
                    completedPageCount, completionPercentage, isStarred,
                    storageUsedBytes, formatBytes(storageUsedBytes), locked, lockedReason, codecId, labelSetId, dictionaryId, tagSetId,
                    normalizationProfileId, validationRulesetId, virtualKeyboardId,
                    allowCodecOverride, allowDictionaryOverride, allowVirtualKeyboardOverride, allowLabelSetOverride,
                    allowTagSetOverride, allowNormalizationProfileOverride, allowValidationRulesetOverride,
                    defaultGtIndex, defaultRecognitionIndices, outputRetentionDays, capabilities);
        }
    }
}
