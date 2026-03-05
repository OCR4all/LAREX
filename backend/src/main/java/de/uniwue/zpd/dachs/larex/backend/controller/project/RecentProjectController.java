package de.uniwue.zpd.dachs.larex.backend.controller.project;

import de.uniwue.zpd.dachs.larex.backend.dto.RecentProjectDto;
import de.uniwue.zpd.dachs.larex.backend.entity.RecentProject;
import de.uniwue.zpd.dachs.larex.backend.service.project.RecentProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/projects/{projectId}/recent")
public class RecentProjectController {

    private final RecentProjectService recentProjectService;

    public RecentProjectController(RecentProjectService recentProjectService) {
        this.recentProjectService = recentProjectService;
    }

    /**
     * Record project access (view or edit)
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> recordProjectAccess(
            @PathVariable String projectId,
            @Valid @RequestBody RecentProjectDto.RecordAccessRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        RecentProject recentProject = recentProjectService.recordProjectAccess(
                userId, projectId, request.accessType());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Project access recorded",
                "accessType", request.accessType(),
                "recordId", recentProject.getId()
        ));
    }

    /**
     * Remove a project from recent history
     */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> removeFromRecent(
            @PathVariable String projectId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        boolean removed = recentProjectService.removeRecentProject(userId, projectId);

        if (removed) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Project removed from recent history"
            ));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Check if a project is in recent history
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getRecentStatus(
            @PathVariable String projectId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        RecentProject recentProject = recentProjectService.getLastAccess(userId, projectId);
        boolean hasRecent = recentProject != null;

        if (hasRecent) {
            return ResponseEntity.ok(Map.of(
                    "hasRecent", true,
                    "projectId", projectId,
                    "accessType", recentProject.getAccessType(),
                    "lastAccessed", recentProject.getLastAccessed()
            ));
        } else {
            return ResponseEntity.ok(Map.of(
                    "hasRecent", false,
                    "projectId", projectId
            ));
        }
    }
}

/**
 * Controller for user recent projects operations
 */
@RestController
@RequestMapping("/recent-projects")
class UserRecentProjectsController {

    private final RecentProjectService recentProjectService;

    public UserRecentProjectsController(RecentProjectService recentProjectService) {
        this.recentProjectService = recentProjectService;
    }

    /**
     * Get recent projects for the current user
     * Can be filtered by workspace or show all workspaces
     */
    @GetMapping
    public ResponseEntity<RecentProjectDto.RecentProjectsListResponse> getRecentProjects(
            @RequestParam(value = "workspaceId", required = false) String workspaceId,
            @RequestParam(value = "limit", defaultValue = "10") int limit,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        RecentProjectDto.RecentProjectsListResponse response =
                recentProjectService.getRecentProjectsListResponse(userId, workspaceId, limit);

        return ResponseEntity.ok(response);
    }

    /**
     * Get recent projects for a specific workspace
     */
    @GetMapping("/workspace/{workspaceId}")
    public ResponseEntity<RecentProjectDto.RecentProjectsListResponse> getRecentProjectsInWorkspace(
            @PathVariable String workspaceId,
            @RequestParam(value = "limit", defaultValue = "10") int limit,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        RecentProjectDto.RecentProjectsListResponse response =
                recentProjectService.getRecentProjectsListResponse(userId, workspaceId, limit);

        return ResponseEntity.ok(response);
    }

    /**
     * Clear all recent projects for the current user
     */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> clearAllRecentProjects(
            @AuthenticationPrincipal(expression = "subject") String userId) {

        long clearedCount = recentProjectService.clearAllRecentProjects(userId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "All recent projects cleared",
                "clearedCount", clearedCount
        ));
    }

    /**
     * Get count of recent projects
     */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Object>> getRecentProjectsCount(
            @RequestParam(value = "workspaceId", required = false) String workspaceId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        long count;
        if (workspaceId != null) {
            count = recentProjectService.countRecentProjectsInWorkspace(userId, workspaceId);
        } else {
            count = recentProjectService.countRecentProjects(userId);
        }

        return ResponseEntity.ok(Map.of(
                "count", count,
                "workspaceId", workspaceId
        ));
    }
}