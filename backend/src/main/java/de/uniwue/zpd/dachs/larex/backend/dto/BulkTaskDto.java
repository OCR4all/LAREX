package de.uniwue.zpd.dachs.larex.backend.dto;

import de.uniwue.zpd.dachs.larex.backend.entity.Task;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class BulkTaskDto {

    public record BulkStatusRequest(
            @NotEmpty(message = "Task IDs are required")
            @Size(max = 200, message = "Cannot bulk operate on more than 200 tasks")
            List<String> taskIds,
            @NotNull(message = "Status is required")
            Task.TaskStatus status
    ) {}

    public record BulkPriorityRequest(
            @NotEmpty(message = "Task IDs are required")
            @Size(max = 200, message = "Cannot bulk operate on more than 200 tasks")
            List<String> taskIds,
            @NotNull(message = "Priority is required")
            Task.TaskPriority priority
    ) {}

    public record BulkAssigneesRequest(
            @NotEmpty(message = "Task IDs are required")
            @Size(max = 200, message = "Cannot bulk operate on more than 200 tasks")
            List<String> taskIds,
            @Size(max = 50, message = "Cannot add more than 50 users")
            List<String> addUserIds,
            @Size(max = 50, message = "Cannot remove more than 50 users")
            List<String> removeUserIds
    ) {}

    public record BulkDeleteRequest(
            @NotEmpty(message = "Task IDs are required")
            @Size(max = 200, message = "Cannot bulk operate on more than 200 tasks")
            List<String> taskIds
    ) {}

    public record BulkOperationResponse(
            int successCount,
            int failedCount,
            List<String> failedTaskIds,
            List<String> errors
    ) {}
}
