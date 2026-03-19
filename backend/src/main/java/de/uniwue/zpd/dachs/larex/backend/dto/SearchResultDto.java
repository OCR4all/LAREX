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

    public record WorkspaceTextResponse(
            String workspaceId,
            String query,
            String matchMode,
            String view,
            int limit,
            int offset,
            long totalHits,
            long totalProjectCount,
            boolean fuzzyExpanded,
            String suggestedQuery,
            List<TextHit> hits,
            List<ProjectHitGroup> projects
    ) {}

    public record TextHit(
            String workspaceId,
            String projectId,
            String projectName,
            String pageId,
            String pageName,
            String textLineId,
            String regionId,
            String snippetHtml,
            String fullText,
            double score,
            String matchKind,
            String previewUrl
    ) {}

    public record ProjectHitGroup(
            String workspaceId,
            String projectId,
            String projectName,
            long hitCount,
            double topScore,
            List<TextHit> hits
    ) {}
}
