package de.uniwue.zpd.dachs.larex.backend.service.search;

import de.uniwue.zpd.dachs.larex.backend.dto.SearchResultDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.AbstractWorkspace;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import de.uniwue.zpd.dachs.larex.backend.service.project.ProjectStarService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

@Service
@Transactional(readOnly = true)
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+(?:['’-][\\p{L}\\p{N}]+)*");
    private static final String MARKER_START = "__LAREX_MARK_START__";
    private static final String MARKER_END = "__LAREX_MARK_END__";
    private static final double FUZZY_SIMILARITY_THRESHOLD = 0.35d;
    private static final int FUZZY_TOKEN_MIN_LENGTH = 4;
    private static final int FUZZY_TOTAL_HIT_THRESHOLD = 3;
    private static final int MAX_TEXT_SEARCH_LIMIT = 50;

    private final ProjectRepository projectRepository;
    private final PageRepository pageRepository;
    private final WorkspaceQueryService workspaceQueryService;
    private final WorkspaceAccessService workspaceAccessService;
    private final ProjectStarService projectStarService;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final SearchLexiconService searchLexiconService;

    public SearchService(ProjectRepository projectRepository,
                         PageRepository pageRepository,
                         WorkspaceQueryService workspaceQueryService,
                         WorkspaceAccessService workspaceAccessService,
                         ProjectStarService projectStarService,
                         NamedParameterJdbcTemplate jdbcTemplate,
                         SearchLexiconService searchLexiconService) {
        this.projectRepository = projectRepository;
        this.pageRepository = pageRepository;
        this.workspaceQueryService = workspaceQueryService;
        this.workspaceAccessService = workspaceAccessService;
        this.projectStarService = projectStarService;
        this.jdbcTemplate = jdbcTemplate;
        this.searchLexiconService = searchLexiconService;
    }

    public SearchResultDto.GlobalResponse globalSearch(String query, int limit, String userId) {
        log.info("Starting global search for user {} with query '{}', limit {}", userId, query, limit);

        List<String> accessibleWorkspaceIds = getAccessibleWorkspaces(userId);
        if (accessibleWorkspaceIds.isEmpty()) {
            return new SearchResultDto.GlobalResponse(query, 0, List.of());
        }

        List<Project> projects = projectRepository.findProjectsInWorkspacesBySearch(
                accessibleWorkspaceIds,
                query.toLowerCase(Locale.ROOT)
        );
        List<SearchResultDto.ProjectResult> allResults = buildSearchResults(projects, query, userId);
        List<SearchResultDto.ProjectResult> limitedResults = limitResults(allResults, limit);
        return new SearchResultDto.GlobalResponse(query, limitedResults.size(), limitedResults);
    }

    public List<SearchResultDto.ProjectResult> searchWorkspaceProjects(String workspaceId, String query, int limit, String userId) {
        if (!workspaceAccessService.hasWorkspaceAccess(workspaceId, userId)) {
            return List.of();
        }

        List<Project> projects = projectRepository.findProjectsInWorkspaceBySearch(workspaceId, query.toLowerCase(Locale.ROOT));
        List<SearchResultDto.ProjectResult> results = buildSearchResults(projects, query, userId);
        return limitResults(results, limit);
    }

    public SearchResultDto.WorkspaceTextResponse searchWorkspaceText(String workspaceId,
                                                                     String query,
                                                                     int limit,
                                                                     int offset,
                                                                     String view,
                                                                     String match,
                                                                     String userId) {
        String trimmedQuery = query == null ? "" : query.trim();
        String normalizedView = normalizeView(view);
        int safeLimit = Math.max(1, Math.min(MAX_TEXT_SEARCH_LIMIT, limit));
        int safeOffset = Math.max(0, offset);

        if (trimmedQuery.isBlank() || !workspaceAccessService.hasWorkspaceAccess(workspaceId, userId)) {
            return new SearchResultDto.WorkspaceTextResponse(
                    workspaceId,
                    trimmedQuery,
                    "exact",
                    normalizedView,
                    safeLimit,
                    safeOffset,
                    0,
                    0,
                    false,
                    null,
                    List.of(),
                    List.of()
            );
        }

        SearchQueryPlan exactPlan = SearchQueryPlan.exact(trimmedQuery);
        SearchExecutionResult result = executeWorkspaceTextSearch(
                workspaceId,
                exactPlan,
                safeLimit,
                safeOffset,
                isPhraseQuery(trimmedQuery) ? "phrase" : "exact"
        );

        boolean fuzzyExpanded = false;
        String suggestedQuery = null;
        String resolvedMatchMode = result.matchKind();

        String normalizedMatch = normalizeMatch(match);
        boolean shouldAttemptFuzzy = switch (normalizedMatch) {
            case "fuzzy" -> true;
            case "auto" -> result.totalHits() < FUZZY_TOTAL_HIT_THRESHOLD;
            default -> false;
        };

        if (shouldAttemptFuzzy && !isPhraseQuery(trimmedQuery) && hasFuzzyEligibleToken(trimmedQuery)) {
            searchLexiconService.ensureWorkspaceLexicon(workspaceId);
            FuzzyExpansion fuzzyExpansion = buildFuzzyExpansion(
                    workspaceId,
                    trimmedQuery,
                    !"fuzzy".equals(normalizedMatch)
            );
            if (fuzzyExpansion.expanded()) {
                SearchExecutionResult fuzzyResult = executeWorkspaceTextSearch(
                        workspaceId,
                        fuzzyExpansion.plan(),
                        safeLimit,
                        safeOffset,
                        "fuzzy"
                );
                if ("fuzzy".equals(normalizedMatch) || fuzzyResult.totalHits() > result.totalHits()) {
                    result = fuzzyResult;
                    fuzzyExpanded = true;
                    suggestedQuery = fuzzyExpansion.suggestedQuery();
                    resolvedMatchMode = "fuzzy";
                }
            }
        }

        return new SearchResultDto.WorkspaceTextResponse(
                workspaceId,
                trimmedQuery,
                resolvedMatchMode,
                normalizedView,
                safeLimit,
                safeOffset,
                result.totalHits(),
                result.totalProjectCount(),
                fuzzyExpanded,
                suggestedQuery,
                result.hits(),
                result.projects()
        );
    }

    private SearchExecutionResult executeWorkspaceTextSearch(String workspaceId,
                                                             SearchQueryPlan plan,
                                                             int limit,
                                                             int offset,
                                                             String matchKind) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("workspaceId", workspaceId)
                .addValue("limit", limit)
                .addValue("offset", offset);
        plan.bind(params);

        String queryExpression = plan.queryExpression();
        String snippetOptions = "StartSel=" + MARKER_START
                + ",StopSel=" + MARKER_END
                + ",MaxFragments=2,MinWords=3,MaxWords=18,ShortWord=0,FragmentDelimiter= ... ";

        String matchedCte = """
                WITH raw_hits AS (
                    SELECT ptc.id AS row_id,
                           l.workspace_id AS workspace_id,
                           pr.id AS project_id,
                           pr.name AS project_name,
                           p.id AS page_id,
                           p.name AS page_name,
                           ptc.text_line_id AS text_line_id,
                           ptc.region_id AS region_id,
                           ptc.text_content AS full_text,
                           COALESCE(ts_headline('simple', ptc.text_content, %1$s, '%2$s'), ptc.text_content) AS snippet_marker,
                           ts_rank_cd(ptc.search_vector, %1$s) AS score
                    FROM page_text_content ptc
                    JOIN pages p ON p.id = ptc.page_id
                    JOIN projects pr ON pr.id = p.project_id
                    JOIN libraries l ON l.id = pr.library_id
                    WHERE l.workspace_id = :workspaceId
                      AND ptc.text_content IS NOT NULL
                      AND ptc.search_vector IS NOT NULL
                      AND COALESCE(ptc.variant_index, 0) = COALESCE(pr.default_gt_index, 0)
                      AND ptc.search_vector @@ %1$s
                ),
                matched AS (
                    SELECT *
                    FROM (
                        SELECT raw_hits.*,
                               ROW_NUMBER() OVER (
                                   PARTITION BY page_id, COALESCE(text_line_id, region_id, row_id)
                                   ORDER BY CHAR_LENGTH(full_text) DESC, score DESC, row_id ASC
                               ) AS anchor_rank
                        FROM raw_hits
                    ) ranked_hits
                    WHERE anchor_rank = 1
                )
                """.formatted(queryExpression, snippetOptions);

        String hitsSql = """
                %1$s
                SELECT workspace_id,
                       project_id,
                       project_name,
                       page_id,
                       page_name,
                       text_line_id,
                       region_id,
                       full_text,
                       snippet_marker,
                       score
                FROM matched
                ORDER BY score DESC, pr.name ASC, p.name ASC, ptc.id ASC
                LIMIT :limit OFFSET :offset
                """;
        hitsSql = hitsSql.replace("ORDER BY score DESC, pr.name ASC, p.name ASC, ptc.id ASC",
                "ORDER BY score DESC, project_name ASC, page_name ASC, page_id ASC");
        hitsSql = hitsSql.formatted(matchedCte);

        List<SearchResultDto.TextHit> hits = jdbcTemplate.query(hitsSql, params, (rs, rowNum) ->
                mapTextHit(
                        rs.getString("workspace_id"),
                        rs.getString("project_id"),
                        rs.getString("project_name"),
                        rs.getString("page_id"),
                        rs.getString("page_name"),
                        rs.getString("text_line_id"),
                        rs.getString("region_id"),
                        rs.getString("snippet_marker"),
                        rs.getString("full_text"),
                        rs.getDouble("score"),
                        matchKind
                ));

        String totalHitsSql = matchedCte + "SELECT COUNT(*) FROM matched";
        long totalHits = queryForLong(totalHitsSql, params);

        String totalProjectCountSql = matchedCte + "SELECT COUNT(DISTINCT project_id) FROM matched";
        long totalProjectCount = queryForLong(totalProjectCountSql, params);

        String projectGroupsSql = """
                %1$s
                SELECT workspace_id,
                       project_id,
                       project_name,
                       COUNT(*) AS hit_count,
                       MAX(score) AS top_score
                FROM matched
                GROUP BY workspace_id, project_id, project_name
                ORDER BY top_score DESC, project_name ASC
                LIMIT :limit OFFSET :offset
                """.formatted(matchedCte);

        List<ProjectGroupRow> groupRows = jdbcTemplate.query(projectGroupsSql, params, (rs, rowNum) ->
                new ProjectGroupRow(
                        rs.getString("workspace_id"),
                        rs.getString("project_id"),
                        rs.getString("project_name"),
                        rs.getLong("hit_count"),
                        rs.getDouble("top_score")
                ));

        Map<String, List<SearchResultDto.TextHit>> hitsByProjectId = fetchTopProjectHits(matchedCte, params, groupRows, matchKind);
        List<SearchResultDto.ProjectHitGroup> projectGroups = groupRows.stream()
                .map(row -> new SearchResultDto.ProjectHitGroup(
                        row.workspaceId(),
                        row.projectId(),
                        row.projectName(),
                        row.hitCount(),
                        row.topScore(),
                        hitsByProjectId.getOrDefault(row.projectId(), List.of())
                ))
                .toList();

        return new SearchExecutionResult(hits, projectGroups, totalHits, totalProjectCount, matchKind);
    }

    private boolean hasFuzzyEligibleToken(String query) {
        Matcher matcher = TOKEN_PATTERN.matcher(query == null ? "" : query);
        while (matcher.find()) {
            if (matcher.group().length() >= FUZZY_TOKEN_MIN_LENGTH) {
                return true;
            }
        }
        return false;
    }

    private Map<String, List<SearchResultDto.TextHit>> fetchTopProjectHits(String matchedCte,
                                                                           MapSqlParameterSource params,
                                                                           List<ProjectGroupRow> groupRows,
                                                                           String matchKind) {
        if (groupRows.isEmpty()) {
            return Map.of();
        }

        List<String> projectIds = groupRows.stream().map(ProjectGroupRow::projectId).toList();
        MapSqlParameterSource topHitParams = new MapSqlParameterSource()
                .addValues(params.getValues())
                .addValue("projectIds", projectIds);

        String scopedMatchedCte = matchedCte + "SELECT * FROM matched WHERE project_id IN (:projectIds)";
        String topHitsSql = """
                WITH scoped_hits AS (
                    %1$s
                ),
                ranked AS (
                    SELECT *,
                           ROW_NUMBER() OVER (PARTITION BY project_id ORDER BY score DESC, page_name ASC, page_id ASC) AS rn
                    FROM scoped_hits
                )
                SELECT *
                FROM ranked
                WHERE rn <= 3
                ORDER BY project_name ASC, rn ASC
                """.formatted(scopedMatchedCte);

        Map<String, List<SearchResultDto.TextHit>> hitsByProjectId = new LinkedHashMap<>();
        jdbcTemplate.query(topHitsSql, topHitParams, rs -> {
            SearchResultDto.TextHit hit = mapTextHit(
                    rs.getString("workspace_id"),
                    rs.getString("project_id"),
                    rs.getString("project_name"),
                    rs.getString("page_id"),
                    rs.getString("page_name"),
                    rs.getString("text_line_id"),
                    rs.getString("region_id"),
                    rs.getString("snippet_marker"),
                    rs.getString("full_text"),
                    rs.getDouble("score"),
                    matchKind
            );
            hitsByProjectId.computeIfAbsent(hit.projectId(), ignored -> new ArrayList<>()).add(hit);
        });
        return hitsByProjectId;
    }

    private FuzzyExpansion buildFuzzyExpansion(String workspaceId, String query, boolean includeOriginalWhenExpanded) {
        LinkedHashMap<String, List<String>> expansions = new LinkedHashMap<>();
        Matcher matcher = TOKEN_PATTERN.matcher(query);
        while (matcher.find()) {
            String originalToken = matcher.group();
            String normalizedToken = normalizeToken(originalToken);
            if (normalizedToken.length() < FUZZY_TOKEN_MIN_LENGTH) {
                continue;
            }

            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("workspaceId", workspaceId)
                    .addValue("term", normalizedToken)
                    .addValue("similarityThreshold", FUZZY_SIMILARITY_THRESHOLD);

            List<String> suggestions = jdbcTemplate.queryForList("""
                    SELECT normalized_token
                    FROM search_lexicon_entries
                    WHERE workspace_id = :workspaceId
                      AND similarity(normalized_token, :term) >= :similarityThreshold
                    ORDER BY similarity(normalized_token, :term) DESC,
                             occurrence_count DESC,
                             normalized_token ASC
                    LIMIT 3
                    """, params, String.class);

            List<String> distinct = new ArrayList<>();
            for (String candidate : suggestions) {
                if (candidate == null || candidate.isBlank() || normalizedToken.equals(candidate) || distinct.contains(candidate)) {
                    continue;
                }
                distinct.add(candidate);
            }
            if (!distinct.isEmpty()) {
                expansions.put(normalizedToken, distinct);
            }
        }

        if (expansions.isEmpty()) {
            return FuzzyExpansion.none();
        }

        List<String> queryGroups = new ArrayList<>();
        List<String> suggestedTerms = new ArrayList<>();
        Matcher matcherForTerms = TOKEN_PATTERN.matcher(query);
        while (matcherForTerms.find()) {
            String token = normalizeToken(matcherForTerms.group());
            List<String> suggestions = expansions.get(token);
            if (suggestions == null || suggestions.isEmpty()) {
                queryGroups.add(quoteTsqueryLexeme(token));
                suggestedTerms.add(token);
                continue;
            }

            LinkedHashSet<String> alternatives = new LinkedHashSet<>();
            if (includeOriginalWhenExpanded) {
                alternatives.add(token);
            }
            alternatives.addAll(suggestions);
            queryGroups.add("(" + alternatives.stream()
                    .map(this::quoteTsqueryLexeme)
                    .collect(Collectors.joining(" | ")) + ")");
            suggestedTerms.add(suggestions.getFirst());
        }

        if (queryGroups.isEmpty()) {
            return FuzzyExpansion.none();
        }

        String suggestedQuery = String.join(" ", suggestedTerms);
        return new FuzzyExpansion(
                true,
                suggestedQuery.equalsIgnoreCase(query.trim()) ? null : suggestedQuery,
                SearchQueryPlan.fuzzy(String.join(" & ", queryGroups))
        );
    }

    private SearchResultDto.TextHit mapTextHit(String workspaceId,
                                               String projectId,
                                               String projectName,
                                               String pageId,
                                               String pageName,
                                               String textLineId,
                                               String regionId,
                                               String snippetMarker,
                                               String fullText,
                                               double score,
                                               String matchKind) {
        String snippetHtml = buildSnippetHtml(snippetMarker, fullText);
        String previewUrl = null;
        if (textLineId != null && !textLineId.isBlank()) {
            previewUrl = "/api/projects/" + projectId + "/pages/" + pageId + "/text-preview?textLineId=" + textLineId;
        } else if (regionId != null && !regionId.isBlank()) {
            previewUrl = "/api/projects/" + projectId + "/pages/" + pageId + "/text-preview?regionId=" + regionId;
        }

        return new SearchResultDto.TextHit(
                workspaceId,
                projectId,
                projectName,
                pageId,
                pageName,
                textLineId,
                regionId,
                snippetHtml,
                fullText,
                score,
                matchKind,
                previewUrl
        );
    }

    private String buildSnippetHtml(String snippetMarker, String fullText) {
        String source = (snippetMarker == null || snippetMarker.isBlank()) ? fullText : snippetMarker;
        if (source == null) {
            return "";
        }
        return HtmlUtils.htmlEscape(source)
                .replace(MARKER_START, "<mark>")
                .replace(MARKER_END, "</mark>");
    }

    private long queryForLong(String sql, MapSqlParameterSource params) {
        Number number = jdbcTemplate.queryForObject(sql, params, Number.class);
        return number == null ? 0L : number.longValue();
    }

    private String normalizeView(String view) {
        return "projects".equalsIgnoreCase(view) ? "projects" : "hits";
    }

    private String normalizeMatch(String match) {
        if ("fuzzy".equalsIgnoreCase(match)) {
            return "fuzzy";
        }
        if ("exact".equalsIgnoreCase(match)) {
            return "exact";
        }
        return "auto";
    }

    private boolean isPhraseQuery(String query) {
        return query != null && query.indexOf('"') >= 0;
    }

    private String normalizeToken(String token) {
        return Normalizer.normalize(token == null ? "" : token, Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private String quoteTsqueryLexeme(String token) {
        return "'" + token.replace("'", "''") + "'";
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
        Map<String, List<Page>> pagesByProjectId = pageRepository.findPagesInProjectsBySearch(projectIds, query.toLowerCase(Locale.ROOT)).stream()
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
        Map<String, Long> out = new LinkedHashMap<>();
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
        return text != null && text.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT));
    }

    private String truncateText(String text, String query, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }

        int queryIndex = text.toLowerCase(Locale.ROOT).indexOf(query.toLowerCase(Locale.ROOT));
        if (queryIndex >= 0) {
            int start = Math.max(0, queryIndex - 30);
            int end = Math.min(text.length(), start + maxLength);
            String truncated = text.substring(start, end);
            return (start > 0 ? "..." : "") + truncated + (end < text.length() ? "..." : "");
        }

        return text.substring(0, maxLength) + "...";
    }

    private record SearchQueryPlan(String queryExpression, String query, String tsQuery) {
        static SearchQueryPlan exact(String query) {
            return new SearchQueryPlan("websearch_to_tsquery('simple', :query)", query, null);
        }

        static SearchQueryPlan fuzzy(String tsQuery) {
            return new SearchQueryPlan("to_tsquery('simple', :tsQuery)", null, tsQuery);
        }

        void bind(MapSqlParameterSource params) {
            if (query != null) {
                params.addValue("query", query);
            }
            if (tsQuery != null) {
                params.addValue("tsQuery", tsQuery);
            }
        }
    }

    private record SearchExecutionResult(
            List<SearchResultDto.TextHit> hits,
            List<SearchResultDto.ProjectHitGroup> projects,
            long totalHits,
            long totalProjectCount,
            String matchKind
    ) {
    }

    private record FuzzyExpansion(boolean expanded, String suggestedQuery, SearchQueryPlan plan) {
        static FuzzyExpansion none() {
            return new FuzzyExpansion(false, null, null);
        }
    }

    private record ProjectGroupRow(
            String workspaceId,
            String projectId,
            String projectName,
            long hitCount,
            double topScore
    ) {
    }
}
