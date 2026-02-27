package de.uniwue.zpd.dachs.larex.backend.dto;

import de.uniwue.zpd.dachs.larex.backend.entity.TaskActivityLog;

import java.time.LocalDateTime;

public class TaskActivityLogDto {

    public record Response(
            String id,
            String taskId,
            String userId,
            UserDto user,
            TaskActivityLog.ActivityType activityType,
            String oldValue,
            String newValue,
            String details,
            LocalDateTime created
    ) {}
}
