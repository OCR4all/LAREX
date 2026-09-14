package de.uniwue.zpd.dachs.larex.backend.dto;

import java.util.List;

public class EditorQueueDto {

    public record Response(
            String workspaceId,
            String workspaceName,
            long openAssignedSubtaskCount,
            long openAssignedPageCount,
            long blockedAssignedPageCount,
            List<PageResponse> pages
    ) {}

    public record PageResponse(
            String pageId,
            String pageName,
            String projectId,
            String projectName,
            Integer sortOrder,
            boolean blocked,
            String blockedReason,
            List<SubtaskDto.Response> subtasks
    ) {}
}
