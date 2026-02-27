package de.uniwue.zpd.dachs.larex.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

public class SearchResultDto {

    public record GlobalResponse(
            String query,
            int totalResults,
            List<ProjectResult> projects
    ) {}

    public record ProjectResult(
            String id,
            String name,
            String description,
            List<String> tags,
            String workspaceId,
            String workspaceName,
            LocalDateTime created,
            LocalDateTime updated,
            int pageCount,
            boolean isStarred,
            List<String> matchFields,
            List<PageMatch> pageMatches
    ) {}

    public record PageMatch(
            String pageId,
            String pageName,
            String matchText,
            String matchType
    ) {}
}