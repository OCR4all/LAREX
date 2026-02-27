package de.uniwue.zpd.dachs.larex.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public class SubtaskDto {

    public record Response(
            String id,
            String taskId,
            String title,
            String description,
            String taskDescription,
            boolean completed,
            int sortOrder,
            LocalDateTime completedAt,
            String completedByUserId,
            UserProfileDto completedBy,
            LocalDateTime created,
            String pageId,
            String pageName,
            String projectId,
            String projectName,
            String assignedUserId,
            UserProfileDto assignedTo
    ) {}

    public record CreateRequest(
            @NotBlank(message = "Title is required")
            String title,
            String description
    ) {}

    public record UpdateRequest(
            String title,
            String description
    ) {}

    public record ReorderRequest(
            @NotNull(message = "Subtask IDs are required")
            List<String> subtaskIds
    ) {}

    public record ProgressResponse(
            long total,
            long completed,
            int percentage
    ) {}

    public record BulkRequest(
            @NotNull(message = "Subtask IDs are required")
            List<String> subtaskIds
    ) {}

    public record BulkResponse(
            int affected
    ) {}

    public record BulkDescriptionRequest(
            @NotNull(message = "Subtask IDs are required")
            List<String> subtaskIds,
            String description
    ) {}

    public record AssignRequest(
            String assignedUserId
    ) {}

    public record BulkAssignRequest(
            @NotNull(message = "Subtask IDs are required")
            List<String> subtaskIds,
            String assignedUserId
    ) {}

    public record CreateWithPageRequest(
            @NotBlank(message = "Title is required")
            String title,
            String pageId,
            String assignedUserId,
            String description
    ) {}

    public record PageSubtaskSummary(
            String pageId,
            long openSubtaskCount
    ) {}
}
