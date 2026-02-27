package de.uniwue.zpd.dachs.larex.backend.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class TaskReminderDto {

    public record Response(
            String id,
            String taskId,
            String userId,
            LocalDateTime reminderTime,
            boolean sent,
            LocalDateTime created
    ) {}

    public record CreateRequest(
            @NotNull(message = "Reminder time is required")
            LocalDateTime reminderTime
    ) {}
}
