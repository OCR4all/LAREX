package de.uniwue.zpd.dachs.larex.backend.controller;

import de.uniwue.zpd.dachs.larex.backend.dto.SearchResultDto;
import de.uniwue.zpd.dachs.larex.backend.service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/search")
public class SearchController {

    private static final Logger log = LoggerFactory.getLogger(SearchController.class);
    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * Global search across all user's workspaces
     */
    @GetMapping
    public ResponseEntity<SearchResultDto.GlobalResponse> globalSearch(
            @RequestParam String q,
            @RequestParam(required = false, defaultValue = "10") int limit,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        log.info("Global search request: query='{}', limit={}, userId={}", q, limit, userId);

        if (q == null || q.trim().isEmpty()) {
            log.warn("Empty search query provided");
            return ResponseEntity.badRequest().build();
        }

        try {
            SearchResultDto.GlobalResponse results = searchService.globalSearch(q.trim(), limit, userId);
            log.info("Search completed: found {} projects for query '{}'", results.projects().size(), q);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Search failed for query '{}' and user {}: {}", q, userId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Search within a specific workspace
     */
    @GetMapping("/workspace/{workspaceId}")
    public ResponseEntity<List<SearchResultDto.ProjectResult>> searchWorkspace(
            @PathVariable String workspaceId,
            @RequestParam String q,
            @RequestParam(required = false, defaultValue = "20") int limit,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        if (q == null || q.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        List<SearchResultDto.ProjectResult> results = searchService.searchWorkspaceProjects(
                workspaceId, q.trim(), limit, userId);
        return ResponseEntity.ok(results);
    }
}
