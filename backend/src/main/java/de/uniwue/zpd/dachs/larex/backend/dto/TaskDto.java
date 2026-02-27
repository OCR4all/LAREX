package de.uniwue.zpd.dachs.larex.backend.dto;

import de.uniwue.zpd.dachs.larex.backend.entity.Task;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public class TaskDto {

    public record Response(
            String id,
            String title,
            String description,
            String createdByUserId,
            UserDto createdBy,
            List<String> assignedUserIds,
            List<UserDto> assignedUsers,
            Task.TaskStatus status,
            Task.TaskPriority priority,
            LocalDateTime dueDate,
            LocalDateTime created,
            LocalDateTime updated,
            LocalDateTime completedAt,
            String completedByUserId,
            String workspaceId
    ) {}

    public record CreateRequest(
            @NotBlank(message = "Title is required")
            @Size(max = 255, message = "Title must not exceed 255 characters")
            String title,

            @Size(max = 10000, message = "Description is too long")
            String description,

            @NotNull(message = "Priority is required")
            Task.TaskPriority priority,

            LocalDateTime dueDate,

            @Size(max = 50, message = "Cannot assign more than 50 users")
            List<String> assignedUserIds
    ) {}

    public record UpdateRequest(
            @Size(max = 255, message = "Title must not exceed 255 characters")
            String title,

            @Size(max = 10000, message = "Description is too long")
            String description,

            Task.TaskPriority priority,

            LocalDateTime dueDate
    ) {}

    public record UpdateStatusRequest(
            @NotNull(message = "Status is required")
            Task.TaskStatus status
    ) {}

    public record UpdateAssigneesRequest(
            @NotNull(message = "Assigned user IDs are required")
            @Size(max = 50, message = "Cannot assign more than 50 users")
            List<String> assignedUserIds
    ) {}
}
