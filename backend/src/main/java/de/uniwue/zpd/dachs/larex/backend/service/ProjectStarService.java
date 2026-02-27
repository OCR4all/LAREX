package de.uniwue.zpd.dachs.larex.backend.service;

import de.uniwue.zpd.dachs.larex.backend.dto.ProjectStarDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.ProjectStar;
import de.uniwue.zpd.dachs.larex.backend.repository.ProjectStarRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@Transactional
public class ProjectStarService {

    private static final Logger log = LoggerFactory.getLogger(ProjectStarService.class);

    private final ProjectStarRepository projectStarRepository;
    private final ProjectRepository projectRepository;

    public ProjectStarService(ProjectStarRepository projectStarRepository, ProjectRepository projectRepository) {
        this.projectStarRepository = projectStarRepository;
        this.projectRepository = projectRepository;
    }

    /**
     * Star a project for a user
     */
    public ProjectStar starProject(String userId, String projectId) {
        log.debug("Starring project {} for user {}", projectId, userId);

        // Check if already starred
        if (projectStarRepository.existsByUserIdAndProjectId(userId, projectId)) {
            log.debug("Project {} is already starred by user {}", projectId, userId);
            return projectStarRepository.findByUserIdAndProjectId(userId, projectId)
                    .orElseThrow(() -> new IllegalStateException("Star should exist but was not found"));
        }

        // Get the project
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found with id: " + projectId));

        // Create and save the star
        ProjectStar star = new ProjectStar(userId, project);
        ProjectStar savedStar = projectStarRepository.save(star);

        log.info("Successfully starred project {} for user {}", projectId, userId);
        return savedStar;
    }

    /**
     * Unstar a project for a user
     */
    public boolean unstarProject(String userId, String projectId) {
        log.debug("Unstarring project {} for user {}", projectId, userId);

        return projectStarRepository.findByUserIdAndProjectId(userId, projectId)
                .map(star -> {
                    projectStarRepository.delete(star);
                    log.info("Successfully unstarred project {} for user {}", projectId, userId);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Toggle star status for a project
     */
    public boolean toggleStar(String userId, String projectId) {
        if (projectStarRepository.existsByUserIdAndProjectId(userId, projectId)) {
            unstarProject(userId, projectId);
            return false; // Unstarred
        } else {
            starProject(userId, projectId);
            return true; // Starred
        }
    }

    /**
     * Check if a project is starred by a user
     */
    @Transactional(readOnly = true)
    public boolean isStarred(String userId, String projectId) {
        return projectStarRepository.existsByUserIdAndProjectId(userId, projectId);
    }

    /**
     * Get all starred projects for a user
     */
    @Transactional(readOnly = true)
    public List<ProjectStar> getAllUserStars(String userId) {
        return projectStarRepository.findByUserId(userId);
    }

    /**
     * Get all starred projects for a user in a specific workspace
     */
    @Transactional(readOnly = true)
    public List<ProjectStar> getStarsInWorkspace(String userId, String workspaceId) {
        return projectStarRepository.findByUserIdInWorkspace(userId, workspaceId);
    }

    /**
     * Get all starred project IDs for a user in a workspace
     */
    @Transactional(readOnly = true)
    public Set<String> getStarredProjectIdsInWorkspace(String userId, String workspaceId) {
        return projectStarRepository.findStarredProjectIdsByUserIdInWorkspace(userId, workspaceId);
    }

    /**
     * Get all starred project IDs for a user across all workspaces
     */
    @Transactional(readOnly = true)
    public Set<String> getAllStarredProjectIds(String userId) {
        return projectStarRepository.findAllStarredProjectIdsByUserId(userId);
    }

    /**
     * Get starred projects with workspace information
     */
    @Transactional(readOnly = true)
    public List<ProjectStarDto.StarWithWorkspaceResponse> getStarsWithWorkspace(String userId) {
        List<Object[]> results = projectStarRepository.findStarsWithWorkspaceByUserId(userId);
        return results.stream()
                .map(row -> {
                    ProjectStar star = (ProjectStar) row[0];
                    String workspaceId = (String) row[1];
                    return mapToStarWithWorkspaceResponse(star, workspaceId);
                })
                .toList();
    }

    /**
     * Get starred projects in a specific workspace as DTOs
     */
    @Transactional(readOnly = true)
    public List<ProjectStarDto.StarResponse> getStarsInWorkspaceAsDto(String userId, String workspaceId) {
        List<ProjectStar> stars = getStarsInWorkspace(userId, workspaceId);
        return stars.stream()
                .map(this::mapToStarResponse)
                .toList();
    }

    /**
     * Get all starred projects for a user as DTOs
     */
    @Transactional(readOnly = true)
    public List<ProjectStarDto.StarResponse> getAllUserStarsAsDto(String userId) {
        List<ProjectStar> stars = getAllUserStars(userId);
        return stars.stream()
                .map(this::mapToStarResponse)
                .toList();
    }

    private ProjectStarDto.StarWithWorkspaceResponse mapToStarWithWorkspaceResponse(ProjectStar star, String workspaceId) {
        Project project = star.getProject();
        return new ProjectStarDto.StarWithWorkspaceResponse(
                star.getId(),
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getTags(),
                workspaceId,
                star.getCreated()
        );
    }

    private ProjectStarDto.StarResponse mapToStarResponse(ProjectStar star) {
        Project project = star.getProject();
        return new ProjectStarDto.StarResponse(
                star.getId(),
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getTags(),
                star.getCreated()
        );
    }

    /**
     * Clean up stars when a project is deleted
     */
    public void cleanupProjectStars(String projectId) {
        log.debug("Cleaning up stars for deleted project {}", projectId);
        long deletedCount = projectStarRepository.countByProjectId(projectId);
        projectStarRepository.deleteByProjectId(projectId);
        log.info("Cleaned up {} stars for deleted project {}", deletedCount, projectId);
    }

    /**
     * Clean up stars when a workspace is deleted
     */
    public void cleanupWorkspaceStars(String workspaceId) {
        log.debug("Cleaning up stars for workspace {}", workspaceId);
        projectStarRepository.deleteByWorkspaceId(workspaceId);
        log.info("Cleaned up stars for workspace {}", workspaceId);
    }

    /**
     * Clean up stars when a project is transferred to another workspace
     * Since stars are user-centric, they remain intact during transfers
     */
    public void handleProjectTransfer(String projectId, String oldWorkspaceId, String newWorkspaceId) {
        log.debug("Handling project transfer for project {} from workspace {} to workspace {}",
                  projectId, oldWorkspaceId, newWorkspaceId);

        // With user-centric stars, we don't need to do anything during transfer
        // The stars will continue to work as the user still has access to the same project
        log.info("Project transfer handled - stars remain intact for project {}", projectId);
    }

    /**
     * Get total number of stars for a project
     */
    @Transactional(readOnly = true)
    public long getStarCount(String projectId) {
        return projectStarRepository.countByProjectId(projectId);
    }
}