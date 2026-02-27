package de.uniwue.zpd.dachs.larex.backend.controller;

import de.uniwue.zpd.dachs.larex.backend.dto.ProjectStarDto;
import de.uniwue.zpd.dachs.larex.backend.entity.ProjectStar;
import de.uniwue.zpd.dachs.larex.backend.service.ProjectStarService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/projects/{projectId}/star")
public class ProjectStarController {

    private final ProjectStarService projectStarService;

    public ProjectStarController(ProjectStarService projectStarService) {
        this.projectStarService = projectStarService;
    }

    /**
     * Star a project
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> starProject(
            @PathVariable String projectId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        ProjectStar star = projectStarService.starProject(userId, projectId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Project starred",
                "starred", true,
                "starId", star.getId()
        ));
    }

    /**
     * Unstar a project
     */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> unstarProject(
            @PathVariable String projectId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        boolean removed = projectStarService.unstarProject(userId, projectId);

        if (removed) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Project unstarred",
                    "starred", false
            ));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Toggle star status for a project
     */
    @PutMapping("/toggle")
    public ResponseEntity<Map<String, Object>> toggleStar(
            @PathVariable String projectId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        boolean isStarred = projectStarService.toggleStar(userId, projectId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", isStarred ? "Project starred" : "Project unstarred",
                "starred", isStarred
        ));
    }

    /**
     * Check if a project is starred
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getStarStatus(
            @PathVariable String projectId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        boolean isStarred = projectStarService.isStarred(userId, projectId);

        return ResponseEntity.ok(Map.of(
                "starred", isStarred,
                "projectId", projectId
        ));
    }
}

/**
 * Controller for user starred projects operations
 */
@RestController
@RequestMapping("/stars")
class UserStarController {

    private final ProjectStarService projectStarService;

    public UserStarController(ProjectStarService projectStarService) {
        this.projectStarService = projectStarService;
    }

    /**
     * Get all starred projects for the current user across all workspaces
     */
    @GetMapping
    public ResponseEntity<List<ProjectStarDto.StarWithWorkspaceResponse>> getAllUserStars(
            @AuthenticationPrincipal(expression = "subject") String userId) {
        List<ProjectStarDto.StarWithWorkspaceResponse> stars = projectStarService.getStarsWithWorkspace(userId);

        return ResponseEntity.ok(stars);
    }

    /**
     * Get starred projects for a user in a specific workspace
     */
    @GetMapping("/workspace/{workspaceId}")
    public ResponseEntity<List<ProjectStarDto.StarResponse>> getStarsInWorkspace(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        List<ProjectStarDto.StarResponse> stars = projectStarService.getStarsInWorkspaceAsDto(userId, workspaceId);

        return ResponseEntity.ok(stars);
    }

    /**
     * Get starred project IDs for a user in a workspace
     */
    @GetMapping("/workspace/{workspaceId}/ids")
    public ResponseEntity<Set<String>> getStarredProjectIdsInWorkspace(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        Set<String> starredIds = projectStarService.getStarredProjectIdsInWorkspace(userId, workspaceId);

        return ResponseEntity.ok(starredIds);
    }

    /**
     * Get all starred project IDs for a user across all workspaces
     */
    @GetMapping("/ids")
    public ResponseEntity<Set<String>> getAllStarredProjectIds(
            @AuthenticationPrincipal(expression = "subject") String userId) {
        Set<String> starredIds = projectStarService.getAllStarredProjectIds(userId);

        return ResponseEntity.ok(starredIds);
    }
}
