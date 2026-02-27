package de.uniwue.zpd.dachs.larex.backend.repository;

import de.uniwue.zpd.dachs.larex.backend.entity.ProjectStar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface ProjectStarRepository extends JpaRepository<ProjectStar, String> {

    /**
     * Find all starred projects for a specific user
     */
    List<ProjectStar> findByUserId(String userId);

    /**
     * Find all starred projects for a user in projects belonging to a specific workspace
     */
    @Query("SELECT ps FROM ProjectStar ps JOIN ps.project p JOIN p.library l WHERE ps.userId = :userId AND l.workspaceId = :workspaceId")
    List<ProjectStar> findByUserIdInWorkspace(@Param("userId") String userId, @Param("workspaceId") String workspaceId);

    /**
     * Find a specific star by user and project
     */
    Optional<ProjectStar> findByUserIdAndProjectId(String userId, String projectId);

    /**
     * Check if a project is starred by a user
     */
    boolean existsByUserIdAndProjectId(String userId, String projectId);

    /**
     * Get project IDs that are starred by a user in a specific workspace
     */
    @Query("SELECT ps.project.id FROM ProjectStar ps JOIN ps.project p JOIN p.library l WHERE ps.userId = :userId AND l.workspaceId = :workspaceId")
    Set<String> findStarredProjectIdsByUserIdInWorkspace(@Param("userId") String userId, @Param("workspaceId") String workspaceId);

    /**
     * Get all starred project IDs for a user across all workspaces
     */
    @Query("SELECT ps.project.id FROM ProjectStar ps WHERE ps.userId = :userId")
    Set<String> findAllStarredProjectIdsByUserId(@Param("userId") String userId);

    /**
     * Get starred projects with workspace information for a user
     */
    @Query("SELECT ps, l.workspaceId FROM ProjectStar ps JOIN ps.project p JOIN p.library l WHERE ps.userId = :userId")
    List<Object[]> findStarsWithWorkspaceByUserId(@Param("userId") String userId);

    /**
     * Delete all stars for a specific project (used when project is deleted)
     */
    @Modifying
    @Query("DELETE FROM ProjectStar ps WHERE ps.project.id = :projectId")
    void deleteByProjectId(@Param("projectId") String projectId);

    /**
     * Delete all stars for projects in a specific workspace (used when workspace is deleted)
     */
    @Modifying
    @Query("DELETE FROM ProjectStar ps WHERE ps.project.id IN (SELECT p.id FROM Project p JOIN p.library l WHERE l.workspaceId = :workspaceId)")
    void deleteByWorkspaceId(@Param("workspaceId") String workspaceId);

    /**
     * Delete all stars for a specific user (used for user cleanup)
     */
    @Modifying
    void deleteByUserId(String userId);

    /**
     * Count stars for a specific project
     */
    long countByProjectId(String projectId);

    /**
     * Count total stars for a user
     */
    long countByUserId(String userId);
}