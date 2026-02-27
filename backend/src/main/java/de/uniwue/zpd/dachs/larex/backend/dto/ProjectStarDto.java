package de.uniwue.zpd.dachs.larex.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

public class ProjectStarDto {

    public record StarWithWorkspaceResponse(
            String id,
            String projectId,
            String projectName,
            String projectDescription,
            List<String> projectTags,
            String workspaceId,
            LocalDateTime created
    ) {}

    public record StarResponse(
            String id,
            String projectId,
            String projectName,
            String projectDescription,
            List<String> projectTags,
            LocalDateTime created
    ) {}
}