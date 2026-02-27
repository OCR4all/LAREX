package de.uniwue.zpd.dachs.larex.backend.repository;

import de.uniwue.zpd.dachs.larex.backend.entity.RecentProject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecentProjectRepository extends JpaRepository<RecentProject, String> {

    /**
     * Find existing recent project record for a user and project
     */
    Optional<RecentProject> findByUserIdAndProjectId(String userId, String projectId);

    /**
     * Find recent projects for a user across all workspaces, ordered by last accessed
     */
    @Query("""
        SELECT rp FROM RecentProject rp
        JOIN FETCH rp.project p
        JOIN FETCH p.library l
        WHERE rp.userId = :userId
        ORDER BY rp.lastAccessed DESC
        """)
    List<RecentProject> findRecentProjectsByUserIdOrderByLastAccessedDesc(
        @Param("userId") String userId,
        Pageable pageable
    );

    /**
     * Find recent projects for a user within a specific workspace, ordered by last accessed
     */
    @Query("""
        SELECT rp FROM RecentProject rp
        JOIN FETCH rp.project p
        JOIN FETCH p.library l
        WHERE rp.userId = :userId
        AND l.workspaceId = :workspaceId
        ORDER BY rp.lastAccessed DESC
        """)
    List<RecentProject> findRecentProjectsByUserIdAndWorkspaceIdOrderByLastAccessedDesc(
        @Param("userId") String userId,
        @Param("workspaceId") String workspaceId,
        Pageable pageable
    );

    /**
     * Count recent projects for a user across all workspaces
     */
    long countByUserId(String userId);

    /**
     * Count recent projects for a user within a specific workspace
     */
    @Query("""
        SELECT COUNT(rp) FROM RecentProject rp
        JOIN rp.project p
        JOIN p.library l
        WHERE rp.userId = :userId
        AND l.workspaceId = :workspaceId
        """)
    long countByUserIdAndWorkspaceId(@Param("userId") String userId, @Param("workspaceId") String workspaceId);

    /**
     * Delete recent projects for a user that are older than a certain limit (e.g., keep only last 50)
     */
    @Query("""
        DELETE FROM RecentProject rp
        WHERE rp.userId = :userId
        AND rp.id NOT IN (
            SELECT rp2.id FROM RecentProject rp2
            WHERE rp2.userId = :userId
            ORDER BY rp2.lastAccessed DESC
            LIMIT :limit
        )
        """)
    void deleteOldRecentProjects(@Param("userId") String userId, @Param("limit") int limit);

    /**
     * Delete all recent project records for a specific project (used when deleting a project).
     */
    @Modifying
    void deleteByProjectId(String projectId);
}