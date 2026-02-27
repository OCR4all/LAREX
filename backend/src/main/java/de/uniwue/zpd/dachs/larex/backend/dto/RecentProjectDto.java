package de.uniwue.zpd.dachs.larex.backend.dto;

import de.uniwue.zpd.dachs.larex.backend.entity.RecentProject;

import java.time.LocalDateTime;
import java.util.List;

public class RecentProjectDto {

    public record RecentProjectResponse(
            String id,
            String projectId,
            String projectName,
            String projectDescription,
            List<String> projectTags,
            String workspaceId,
            String workspaceName,
            RecentProject.AccessType accessType,
            LocalDateTime lastAccessed,
            LocalDateTime created
    ) {}

    public record RecentProjectSimpleResponse(
            String id,
            String projectId,
            String projectName,
            String projectDescription,
            List<String> projectTags,
            RecentProject.AccessType accessType,
            LocalDateTime lastAccessed,
            LocalDateTime created
    ) {}

    public record RecordAccessRequest(
            String projectId,
            RecentProject.AccessType accessType
    ) {}

    public record RecentProjectsListResponse(
            List<RecentProjectResponse> projects,
            long totalCount,
            int limit,
            String workspaceId // null if showing all workspaces
    ) {}
}