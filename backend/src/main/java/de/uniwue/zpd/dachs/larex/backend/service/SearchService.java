package de.uniwue.zpd.dachs.larex.backend.service;

import de.uniwue.zpd.dachs.larex.backend.dto.SearchResultDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.AbstractWorkspace;
import de.uniwue.zpd.dachs.larex.backend.repository.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final ProjectRepository projectRepository;
    private final PageRepository pageRepository;
    private final WorkspaceQueryService workspaceQueryService;
    private final WorkspaceAccessService workspaceAccessService;
    private final ProjectStarService projectStarService;

    public SearchService(ProjectRepository projectRepository, PageRepository pageRepository,
                         WorkspaceQueryService workspaceQueryService, WorkspaceAccessService workspaceAccessService,
                         ProjectStarService projectStarService) {
        this.projectRepository = projectRepository;
        this.pageRepository = pageRepository;
        this.workspaceQueryService = workspaceQueryService;
        this.workspaceAccessService = workspaceAccessService;
        this.projectStarService = projectStarService;
    }

    /**
     * Global search across all user's accessible workspaces
     */
    public SearchResultDto.GlobalResponse globalSearch(String query, int limit, String userId) {
        log.info("🔍 Starting global search for user {} with query: '{}', limit: {}", userId, query, limit);

        // Get all workspaces accessible to the user
        List<String> accessibleWorkspaceIds = getAccessibleWorkspaces(userId);
        log.info("📁 User {} has access to {} workspaces: {}", userId, accessibleWorkspaceIds.size(), accessibleWorkspaceIds);

        if (accessibleWorkspaceIds.isEmpty()) {
            return new SearchResultDto.GlobalResponse(query, 0, List.of());
        }

        List<Project> projects = projectRepository.findProjectsInWorkspacesBySearch(
                accessibleWorkspaceIds,
                query.toLowerCase()
        );
        List<SearchResultDto.ProjectResult> allResults = buildSearchResults(projects, query, userId);

        List<SearchResultDto.ProjectResult> limitedResults = limitResults(allResults, limit);

        log.info("✅ Global search complete: returning {} results for query '{}'", limitedResults.size(), query);
        return new SearchResultDto.GlobalResponse(query, limitedResults.size(), limitedResults);
    }

    /**
     * Search projects within a specific workspace
     */
    public List<SearchResultDto.ProjectResult> searchWorkspaceProjects(String workspaceId, String query, int limit, String userId) {
        log.info("🔎 Searching workspace {} for user {} with query: '{}', limit: {}", workspaceId, userId, query, limit);

        if (!workspaceAccessService.hasWorkspaceAccess(workspaceId, userId)) {
            log.warn("🚫 User {} does not have access to workspace {}", userId, workspaceId);
            return List.of();
        }

        List<Project> projects = projectRepository.findProjectsInWorkspaceBySearch(workspaceId, query.toLowerCase());
        log.info("📂 Found {} projects in workspace {} matching query '{}'", projects.size(), workspaceId, query);

        List<SearchResultDto.ProjectResult> results = buildSearchResults(projects, query, userId);
        return limitResults(results, limit);
    }

    private List<String> getAccessibleWorkspaces(String userId) {
        List<AbstractWorkspace> allWorkspaces = workspaceQueryService.findAllWorkspacesForUser(userId);
        return allWorkspaces.stream().map(AbstractWorkspace::getId).toList();
    }

    private List<SearchResultDto.ProjectResult> buildSearchResults(List<Project> projects, String query, String userId) {
        if (projects == null || projects.isEmpty()) {
            return List.of();
        }

        List<String> projectIds = projects.stream().map(Project::getId).toList();
        Map<String, List<Page>> pagesByProjectId = pageRepository.findPagesInProjectsBySearch(projectIds, query.toLowerCase()).stream()
                .collect(Collectors.groupingBy(page -> page.getProject().getId()));
        Map<String, Long> pageCountByProjectId = toLongMap(pageRepository.countByProjectIds(projectIds));
        Set<String> starredProjectIds = projectStarService.getAllStarredProjectIds(userId);
        Map<String, String> workspaceNames = workspaceQueryService.findWorkspaceNamesByIds(
                projects.stream().map(project -> project.getLibrary().getWorkspaceId()).collect(Collectors.toSet())
        );

        List<SearchResultDto.ProjectResult> results = new ArrayList<>();
        for (Project project : projects) {
            List<String> matchFields = new ArrayList<>();
            List<SearchResultDto.PageMatch> pageMatches = new ArrayList<>();

            if (containsIgnoreCase(project.getName(), query)) {
                matchFields.add("name");
            }
            if (project.getDescription() != null && containsIgnoreCase(project.getDescription(), query)) {
                matchFields.add("description");
            }
            if (project.getTags() != null && project.getTags().stream().anyMatch(tag -> containsIgnoreCase(tag, query))) {
                matchFields.add("tags");
            }

            for (Page page : pagesByProjectId.getOrDefault(project.getId(), List.of())) {
                String matchType = "name";
                String matchText = page.getName();

                if (page.getDescription() != null && containsIgnoreCase(page.getDescription(), query)) {
                    matchType = "description";
                    matchText = truncateText(page.getDescription(), query, 100);
                } else if (containsIgnoreCase(page.getName(), query)) {
                    matchText = page.getName();
                }

                pageMatches.add(new SearchResultDto.PageMatch(
                        page.getId(),
                        page.getName(),
                        matchText,
                        matchType
                ));
            }

            if (!pageMatches.isEmpty()) {
                matchFields.add("pages");
            }

            if (!matchFields.isEmpty()) {
                String workspaceId = project.getLibrary().getWorkspaceId();
                results.add(new SearchResultDto.ProjectResult(
                        project.getId(),
                        project.getName(),
                        project.getDescription(),
                        project.getTags(),
                        workspaceId,
                        workspaceNames.getOrDefault(workspaceId, "Unknown"),
                        project.getCreated(),
                        project.getUpdated(),
                        pageCountByProjectId.getOrDefault(project.getId(), 0L).intValue(),
                        starredProjectIds.contains(project.getId()),
                        matchFields,
                        pageMatches.stream().limit(3).toList()
                ));
            }
        }
        return results;
    }

    private Map<String, Long> toLongMap(Collection<Object[]> rows) {
        Map<String, Long> out = new HashMap<>();
        for (Object[] row : rows) {
            if (row == null || row.length < 2 || row[0] == null || row[1] == null) {
                continue;
            }
            out.put((String) row[0], ((Number) row[1]).longValue());
        }
        return out;
    }

    private List<SearchResultDto.ProjectResult> limitResults(List<SearchResultDto.ProjectResult> results, int limit) {
        return results.stream()
                .sorted((a, b) -> Integer.compare(b.matchFields().size(), a.matchFields().size()))
                .limit(limit)
                .toList();
    }

    private boolean containsIgnoreCase(String text, String search) {
        return text != null && text.toLowerCase().contains(search.toLowerCase());
    }

    private String truncateText(String text, String query, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }

        // Try to find the query in the text and show context around it
        int queryIndex = text.toLowerCase().indexOf(query.toLowerCase());
        if (queryIndex >= 0) {
            int start = Math.max(0, queryIndex - 30);
            int end = Math.min(text.length(), start + maxLength);
            String truncated = text.substring(start, end);
            return (start > 0 ? "..." : "") + truncated + (end < text.length() ? "..." : "");
        }

        return text.substring(0, maxLength) + "...";
    }
}
