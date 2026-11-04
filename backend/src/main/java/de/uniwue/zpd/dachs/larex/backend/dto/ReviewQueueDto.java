package de.uniwue.zpd.dachs.larex.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

public class ReviewQueueDto {

    public record Response(
            String workspaceId,
            String workspaceName,
            long totalPageCount,
            long blockedPageCount,
            List<PageResponse> pages
    ) {}

    public record PageResponse(
            String pageId,
            String pageName,
            String projectId,
            String projectName,
            Integer sortOrder,
            String submittedByUserId,
            UserDto submittedBy,
            LocalDateTime submittedAt,
            LocalDateTime dueDate,
            List<AssignmentSummary> assignments,
            boolean blocked,
            String blockedReason
    ) {}

    public record AssignmentSummary(
            String id,
            String title
    ) {}
}
