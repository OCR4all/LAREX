package de.uniwue.zpd.dachs.larex.backend.repository.project;

import de.uniwue.zpd.dachs.larex.backend.entity.ProjectExportJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProjectExportJobRepository extends JpaRepository<ProjectExportJob, String> {

    Optional<ProjectExportJob> findByIdAndWorkspaceIdAndCreatedByUserId(
            String id, String workspaceId, String createdByUserId);

    List<ProjectExportJob> findByStatusOrderByCreatedAsc(ProjectExportJob.Status status);

    long countByStatus(ProjectExportJob.Status status);

    List<ProjectExportJob> findTop100ByWorkspaceIdAndCreatedByUserIdOrderByCreatedDesc(
            String workspaceId, String createdByUserId);

    long countByWorkspaceIdAndCreatedByUserIdAndStatusIn(
            String workspaceId, String createdByUserId, List<ProjectExportJob.Status> statuses);

    List<ProjectExportJob> findByStatusInAndExpiresAtBefore(
            List<ProjectExportJob.Status> statuses, LocalDateTime expiresAt);

    boolean existsByIdAndCancelRequestedTrue(String id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ProjectExportJob j
            SET j.status = de.uniwue.zpd.dachs.larex.backend.entity.ProjectExportJob$Status.RUNNING,
                j.leaseOwner = :leaseOwner,
                j.leaseExpiresAt = :leaseExpiresAt,
                j.lastHeartbeatAt = :now,
                j.startedAt = COALESCE(j.startedAt, :now),
                j.errorMessage = NULL
            WHERE j.id = :jobId
              AND j.status = de.uniwue.zpd.dachs.larex.backend.entity.ProjectExportJob$Status.QUEUED
              AND j.cancelRequested = FALSE
            """)
    int claim(@Param("jobId") String jobId,
              @Param("leaseOwner") String leaseOwner,
              @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt,
              @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ProjectExportJob j
            SET j.leaseExpiresAt = :leaseExpiresAt, j.lastHeartbeatAt = :now
            WHERE j.id IN :jobIds
              AND j.status = de.uniwue.zpd.dachs.larex.backend.entity.ProjectExportJob$Status.RUNNING
              AND j.leaseOwner = :leaseOwner
            """)
    int renewLeases(@Param("jobIds") List<String> jobIds,
                    @Param("leaseOwner") String leaseOwner,
                    @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt,
                    @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ProjectExportJob j
            SET j.status = de.uniwue.zpd.dachs.larex.backend.entity.ProjectExportJob$Status.QUEUED,
                j.leaseOwner = NULL, j.leaseExpiresAt = NULL, j.lastHeartbeatAt = NULL
            WHERE j.status = de.uniwue.zpd.dachs.larex.backend.entity.ProjectExportJob$Status.RUNNING
              AND (j.leaseExpiresAt IS NULL OR j.leaseExpiresAt < :now)
            """)
    int requeueExpiredLeases(@Param("now") LocalDateTime now);
}
