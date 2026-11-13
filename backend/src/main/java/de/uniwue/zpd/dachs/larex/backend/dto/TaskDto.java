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
            String workspaceId,
            Task.PageCompletion pageCompletion,
            LocalDateTime archivedAt,
            SubtaskDto.ProgressResponse progress,
            AuthorizationCapabilitiesDto.TaskCapabilities capabilities
    ) {}

    public record AssignedWorkspaceResponse(
            String workspaceId,
            String workspaceName,
            List<Response> tasks,
            long openAssignedSubtaskCount,
            long openAssignedPageCount,
            long blockedAssignedPageCount
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
            List<String> assignedUserIds,

            Task.PageCompletion pageCompletion,

            @Size(max = 10000, message = "Cannot select more than 10000 pages")
            List<String> pageIds
    ) {}

    public record UpdateRequest(
            @Size(max = 255, message = "Title must not exceed 255 characters")
            String title,

            @Size(max = 10000, message = "Description is too long")
            String description,

            Task.TaskPriority priority,

            LocalDateTime dueDate,

            Task.PageCompletion pageCompletion
    ) {}

    public record UpdatePausedRequest(
            boolean paused
    ) {}

    public record UpdateArchivedRequest(
            boolean archived
    ) {}

    public record UpdateDueDateRequest(
            LocalDateTime dueDate
    ) {}

    public record UpdateAssigneesRequest(
            @NotNull(message = "Assigned user IDs are required")
            @Size(max = 50, message = "Cannot assign more than 50 users")
            List<String> assignedUserIds
    ) {}
}
