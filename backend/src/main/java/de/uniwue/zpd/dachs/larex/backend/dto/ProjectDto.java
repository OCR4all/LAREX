package de.uniwue.zpd.dachs.larex.backend.dto;

import jakarta.validation.constraints.NotBlank;
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
            String tagSetId,
            Integer defaultGtIndex,
            List<Integer> defaultRecognitionIndices
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
            boolean isStarred,
            Long storageUsedBytes,
            String storageUsedFormatted,
            boolean locked,
            String lockedReason,
            String codecId,
            String labelSetId,
            String tagSetId,
            Integer defaultGtIndex,
            List<Integer> defaultRecognitionIndices
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
                                  boolean isStarred, Long storageUsedBytes, boolean locked, String lockedReason,
                                  String codecId, String labelSetId, String tagSetId,
                                  Integer defaultGtIndex, List<Integer> defaultRecognitionIndices) {
            return new Response(id, name, description, tags, resolvedTags, created, updated, pageCount, isStarred,
                    storageUsedBytes, formatBytes(storageUsedBytes), locked, lockedReason, codecId, labelSetId, tagSetId,
                    defaultGtIndex, defaultRecognitionIndices);
        }
    }
}
