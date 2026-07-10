package de.uniwue.zpd.dachs.larex.backend.repository.importing;

import de.uniwue.zpd.dachs.larex.backend.entity.IiifImportJob;
import de.uniwue.zpd.dachs.larex.backend.entity.IiifImportJob.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

@Repository
public interface IiifImportJobRepository extends JpaRepository<IiifImportJob, String> {

    Optional<IiifImportJob> findByIdAndWorkspaceIdAndProjectId(String id, String workspaceId, String projectId);

    Optional<IiifImportJob> findByIdAndWorkspaceIdAndCreatedByUserId(
            String id,
            String workspaceId,
            String createdByUserId
    );

    List<IiifImportJob> findTop100ByWorkspaceIdAndCreatedByUserIdAndDismissedFalseOrderByCreatedDesc(
            String workspaceId,
            String createdByUserId
    );

    List<IiifImportJob> findByWorkspaceIdAndCreatedByUserIdAndDismissedFalseAndStatusIn(
            String workspaceId,
            String createdByUserId,
            List<Status> statuses
    );

    @Query("SELECT j FROM IiifImportJob j WHERE j.projectId = :projectId AND j.status IN :statuses ORDER BY j.created DESC")
    List<IiifImportJob> findActiveJobsForProject(@Param("projectId") String projectId, @Param("statuses") List<Status> statuses);

    List<IiifImportJob> findByStatusOrderByCreatedAsc(Status status);

    @Query("""
            SELECT j.id
            FROM IiifImportJob j
            WHERE j.status = de.uniwue.zpd.dachs.larex.backend.entity.IiifImportJob$Status.PENDING
            ORDER BY j.created ASC, j.id ASC
            """)
    List<String> findPendingJobIdsInQueueOrder();

    @Query("""
            SELECT COUNT(j)
            FROM IiifImportJob j
            WHERE j.status = de.uniwue.zpd.dachs.larex.backend.entity.IiifImportJob$Status.PENDING
              AND (
                  j.created < :created
                  OR (j.created = :created AND j.id < :jobId)
              )
            """)
    long countPendingBefore(@Param("created") LocalDateTime created, @Param("jobId") String jobId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE IiifImportJob j
            SET j.status = de.uniwue.zpd.dachs.larex.backend.entity.IiifImportJob$Status.IMPORTING,
                j.leaseOwner = :leaseOwner,
                j.leaseExpiresAt = :leaseExpiresAt,
                j.lastHeartbeatAt = :heartbeatAt,
                j.updated = CURRENT_TIMESTAMP
            WHERE j.id = :jobId
              AND j.status = de.uniwue.zpd.dachs.larex.backend.entity.IiifImportJob$Status.PENDING
            """)
    int claimPendingJob(@Param("jobId") String jobId,
                        @Param("leaseOwner") String leaseOwner,
                        @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt,
                        @Param("heartbeatAt") LocalDateTime heartbeatAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE IiifImportJob j
            SET j.leaseExpiresAt = :leaseExpiresAt,
                j.lastHeartbeatAt = :heartbeatAt
            WHERE j.status = de.uniwue.zpd.dachs.larex.backend.entity.IiifImportJob$Status.IMPORTING
              AND j.leaseOwner = :leaseOwner
              AND j.id IN :jobIds
            """)
    int renewLeases(@Param("leaseOwner") String leaseOwner,
                    @Param("jobIds") List<String> jobIds,
                    @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt,
                    @Param("heartbeatAt") LocalDateTime heartbeatAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE IiifImportJob j
            SET j.status = de.uniwue.zpd.dachs.larex.backend.entity.IiifImportJob$Status.PENDING,
                j.leaseOwner = NULL,
                j.leaseExpiresAt = NULL,
                j.lastHeartbeatAt = NULL,
                j.updated = CURRENT_TIMESTAMP
            WHERE j.status = de.uniwue.zpd.dachs.larex.backend.entity.IiifImportJob$Status.IMPORTING
              AND (j.leaseExpiresAt IS NULL OR j.leaseExpiresAt < :now)
            """)
    int requeueExpiredLeases(@Param("now") LocalDateTime now);
}
