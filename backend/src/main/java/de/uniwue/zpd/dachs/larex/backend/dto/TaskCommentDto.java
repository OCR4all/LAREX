package de.uniwue.zpd.dachs.larex.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public class TaskCommentDto {

    public record Response(
            String id,
            String taskId,
            String userId,
            UserDto user,
            String content,
            List<String> mentionedUserIds,
            List<UserDto> mentionedUsers,
            LocalDateTime created,
            LocalDateTime updated
    ) {}

    public record CreateRequest(
            @NotBlank(message = "Content is required")
            @Size(max = 10000, message = "Comment is too long")
            String content
    ) {}

    public record UpdateRequest(
            @NotBlank(message = "Content is required")
            @Size(max = 10000, message = "Comment is too long")
            String content
    ) {}
}
