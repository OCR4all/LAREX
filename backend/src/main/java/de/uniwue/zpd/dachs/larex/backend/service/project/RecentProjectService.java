package de.uniwue.zpd.dachs.larex.backend.service.project;

import de.uniwue.zpd.dachs.larex.backend.dto.RecentProjectDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.RecentProject;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.RecentProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class RecentProjectService {

    private static final Logger log = LoggerFactory.getLogger(RecentProjectService.class);
    private static final int MAX_RECENT_PROJECTS_PER_USER = 50;

    private final RecentProjectRepository recentProjectRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceQueryService workspaceQueryService;

    public RecentProjectService(RecentProjectRepository recentProjectRepository,
                              ProjectRepository projectRepository,
                              WorkspaceQueryService workspaceQueryService) {
        this.recentProjectRepository = recentProjectRepository;
        this.projectRepository = projectRepository;
        this.workspaceQueryService = workspaceQueryService;
    }

    /**
     * Record that a user has accessed a project (viewed or edited)
     */
    public RecentProject recordProjectAccess(String userId, String projectId, RecentProject.AccessType accessType) {
        log.debug("Recording {} access for project {} by user {}", accessType, projectId, userId);

        // Get the project
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found with id: " + projectId));

        // Check if there's already a recent access record
        RecentProject recentProject = recentProjectRepository.findByUserIdAndProjectId(userId, projectId)
                .map(existing -> {
                    // Update existing record with new access time and type
                    existing.setAccessType(accessType);
                    existing.setLastAccessed(LocalDateTime.now());
                    return existing;
                })
                .orElseGet(() -> {
                    // Create new record
                    return new RecentProject(userId, project, accessType);
                });

        RecentProject savedRecentProject = recentProjectRepository.save(recentProject);

        // Clean up old records to maintain limit
        cleanupOldRecords(userId);

        log.info("Successfully recorded {} access for project {} by user {}", accessType, projectId, userId);
        return savedRecentProject;
    }

    /**
     * Get recent projects for a user across all workspaces
     */
    @Transactional(readOnly = true)
    public List<RecentProject> getRecentProjects(String userId, int limit) {
        log.debug("Getting recent projects for user {} with limit {}", userId, limit);
        Pageable pageable = PageRequest.of(0, limit);
        return recentProjectRepository.findRecentProjectsByUserIdOrderByLastAccessedDesc(userId, pageable);
    }

    /**
     * Get recent projects for a user within a specific workspace
     */
    @Transactional(readOnly = true)
    public List<RecentProject> getRecentProjectsInWorkspace(String userId, String workspaceId, int limit) {
        log.debug("Getting recent projects for user {} in workspace {} with limit {}", userId, workspaceId, limit);
        Pageable pageable = PageRequest.of(0, limit);
        return recentProjectRepository.findRecentProjectsByUserIdAndWorkspaceIdOrderByLastAccessedDesc(
                userId, workspaceId, pageable);
    }

    /**
     * Get recent projects with default limit
     */
    @Transactional(readOnly = true)
    public List<RecentProject> getRecentProjects(String userId) {
        return getRecentProjects(userId, 10);
    }

    /**
     * Get recent projects in workspace with default limit
     */
    @Transactional(readOnly = true)
    public List<RecentProject> getRecentProjectsInWorkspace(String userId, String workspaceId) {
        return getRecentProjectsInWorkspace(userId, workspaceId, 10);
    }

    /**
     * Check if a project has been recently accessed by a user
     */
    @Transactional(readOnly = true)
    public boolean hasRecentAccess(String userId, String projectId) {
        return recentProjectRepository.findByUserIdAndProjectId(userId, projectId).isPresent();
    }

    /**
     * Get the last access information for a project by a user
     */
    @Transactional(readOnly = true)
    public RecentProject getLastAccess(String userId, String projectId) {
        return recentProjectRepository.findByUserIdAndProjectId(userId, projectId)
                .orElse(null);
    }

    /**
     * Count total recent projects for a user across all workspaces
     */
    @Transactional(readOnly = true)
    public long countRecentProjects(String userId) {
        return recentProjectRepository.countByUserId(userId);
    }

    /**
     * Count recent projects for a user within a specific workspace
     */
    @Transactional(readOnly = true)
    public long countRecentProjectsInWorkspace(String userId, String workspaceId) {
        return recentProjectRepository.countByUserIdAndWorkspaceId(userId, workspaceId);
    }

    /**
     * Clean up old recent project records to maintain the limit per user
     */
    private void cleanupOldRecords(String userId) {
        long totalCount = recentProjectRepository.countByUserId(userId);

        if (totalCount > MAX_RECENT_PROJECTS_PER_USER) {
            log.debug("Cleaning up old recent project records for user {} (current count: {})", userId, totalCount);
            recentProjectRepository.deleteOldRecentProjects(userId, MAX_RECENT_PROJECTS_PER_USER);
            log.info("Cleaned up old recent project records for user {}", userId);
        }
    }

    /**
     * Clean up recent project records when a project is deleted
     */
    public void cleanupProjectRecords(String projectId) {
        log.debug("Cleaning up recent project records for deleted project {}", projectId);
        List<RecentProject> records = recentProjectRepository.findAll().stream()
                .filter(rp -> rp.getProject().getId().equals(projectId))
                .toList();

        recentProjectRepository.deleteAll(records);
        log.info("Cleaned up {} recent project records for deleted project {}", records.size(), projectId);
    }

    /**
     * Clean up recent project records when a workspace is deleted
     */
    public void cleanupWorkspaceRecords(String workspaceId) {
        log.debug("Cleaning up recent project records for workspace {}", workspaceId);

        // Find all recent projects in the workspace and delete them
        List<RecentProject> records = recentProjectRepository.findAll().stream()
                .filter(rp -> rp.getProject().getLibrary().getWorkspaceId().equals(workspaceId))
                .toList();

        recentProjectRepository.deleteAll(records);
        log.info("Cleaned up {} recent project records for workspace {}", records.size(), workspaceId);
    }

    /**
     * Remove a specific recent project record
     */
    public boolean removeRecentProject(String userId, String projectId) {
        log.debug("Removing recent project record for user {} and project {}", userId, projectId);

        return recentProjectRepository.findByUserIdAndProjectId(userId, projectId)
                .map(record -> {
                    recentProjectRepository.delete(record);
                    log.info("Successfully removed recent project record for user {} and project {}", userId, projectId);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Clear all recent projects for a user
     */
    public long clearAllRecentProjects(String userId) {
        log.debug("Clearing all recent projects for user {}", userId);

        List<RecentProject> records = recentProjectRepository.findRecentProjectsByUserIdOrderByLastAccessedDesc(
                userId, Pageable.unpaged());

        long count = records.size();
        recentProjectRepository.deleteAll(records);

        log.info("Cleared {} recent project records for user {}", count, userId);
        return count;
    }

    /**
     * Get recent projects as DTOs across all workspaces
     */
    @Transactional(readOnly = true)
    public List<RecentProjectDto.RecentProjectResponse> getRecentProjectsAsDto(String userId, int limit) {
        List<RecentProject> recentProjects = getRecentProjects(userId, limit);
        return mapToRecentProjectResponses(recentProjects);
    }

    /**
     * Get recent projects as DTOs within a specific workspace
     */
    @Transactional(readOnly = true)
    public List<RecentProjectDto.RecentProjectResponse> getRecentProjectsInWorkspaceAsDto(String userId, String workspaceId, int limit) {
        List<RecentProject> recentProjects = getRecentProjectsInWorkspace(userId, workspaceId, limit);
        return mapToRecentProjectResponses(recentProjects);
    }

    /**
     * Get recent projects list response with metadata
     */
    @Transactional(readOnly = true)
    public RecentProjectDto.RecentProjectsListResponse getRecentProjectsListResponse(String userId, String workspaceId, int limit) {
        List<RecentProjectDto.RecentProjectResponse> projects;
        long totalCount;

        if (workspaceId != null) {
            projects = getRecentProjectsInWorkspaceAsDto(userId, workspaceId, limit);
            totalCount = countRecentProjectsInWorkspace(userId, workspaceId);
        } else {
            projects = getRecentProjectsAsDto(userId, limit);
            totalCount = countRecentProjects(userId);
        }

        return new RecentProjectDto.RecentProjectsListResponse(projects, totalCount, limit, workspaceId);
    }

    private List<RecentProjectDto.RecentProjectResponse> mapToRecentProjectResponses(List<RecentProject> recentProjects) {
        if (recentProjects == null || recentProjects.isEmpty()) {
            return List.of();
        }

        Set<String> workspaceIds = new HashSet<>();
        for (RecentProject recentProject : recentProjects) {
            workspaceIds.add(recentProject.getProject().getLibrary().getWorkspaceId());
        }
        Map<String, String> workspaceNames = workspaceQueryService.findWorkspaceNamesByIds(workspaceIds);

        return recentProjects.stream()
                .map(recentProject -> mapToRecentProjectResponse(recentProject, workspaceNames))
                .toList();
    }

    private RecentProjectDto.RecentProjectResponse mapToRecentProjectResponse(RecentProject recentProject, Map<String, String> workspaceNames) {
        Project project = recentProject.getProject();
        String workspaceId = project.getLibrary().getWorkspaceId();
        String workspaceName = workspaceNames.getOrDefault(workspaceId, "Unknown Workspace");

        return new RecentProjectDto.RecentProjectResponse(
                recentProject.getId(),
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getTags(),
                workspaceId,
                workspaceName,
                recentProject.getAccessType(),
                recentProject.getLastAccessed(),
                recentProject.getCreated()
        );
    }
}
