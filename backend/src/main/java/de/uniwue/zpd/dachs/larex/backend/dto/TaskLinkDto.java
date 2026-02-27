package de.uniwue.zpd.dachs.larex.backend.dto;

import de.uniwue.zpd.dachs.larex.backend.entity.TaskPageLink;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public class TaskLinkDto {

    public record ProjectLinkResponse(
            String id,
            String taskId,
            String projectId,
            String projectName,
            String createdByUserId,
            LocalDateTime created
    ) {}

    public record PageLinkResponse(
            String id,
            String taskId,
            String pageId,
            String pageName,
            String projectId,
            String projectName,
            TaskPageLink.LinkType linkType,
            String tagFilter,
            String createdByUserId,
            LocalDateTime created
    ) {}

    public record TaskLinksResponse(
            List<ProjectLinkResponse> projectLinks,
            List<PageLinkResponse> pageLinks
    ) {}

    public record LinkProjectRequest(
            @NotNull(message = "Project ID is required")
            String projectId
    ) {}

    public record LinkPagesRequest(
            List<String> pageIds,
            String byTag
    ) {}

    public record LinkedTaskResponse(
            String id,
            String title,
            String status,
            String priority,
            LocalDateTime dueDate,
            List<UserDto> assignedUsers
    ) {}
}
