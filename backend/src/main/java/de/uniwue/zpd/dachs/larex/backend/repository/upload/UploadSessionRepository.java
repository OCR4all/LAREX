package de.uniwue.zpd.dachs.larex.backend.repository.upload;

import de.uniwue.zpd.dachs.larex.backend.entity.UploadSession;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSession.UploadSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UploadSessionRepository extends JpaRepository<UploadSession, String> {

    List<UploadSession> findByProjectId(String projectId);

    List<UploadSession> findByWorkspaceId(String workspaceId);

    List<UploadSession> findByUserId(String userId);

    Optional<UploadSession> findByIdAndWorkspaceId(String sessionId, String workspaceId);

    Optional<UploadSession> findByIdAndUserId(String sessionId, String userId);

    @Query("SELECT s.status FROM UploadSession s WHERE s.id = :sessionId")
    Optional<UploadSessionStatus> findStatusById(@Param("sessionId") String sessionId);

    List<UploadSession> findByProjectIdAndStatus(String projectId, UploadSessionStatus status);

    List<UploadSession> findByUserIdAndStatusIn(String userId, List<UploadSessionStatus> statuses);

    @Query("SELECT s FROM UploadSession s WHERE s.status IN :statuses AND s.created < :before")
    List<UploadSession> findStaleSessionsCreatedBefore(
            @Param("statuses") List<UploadSessionStatus> statuses,
            @Param("before") LocalDateTime before);

    @Query("SELECT COUNT(s) FROM UploadSession s WHERE s.userId = :userId AND s.status IN :activeStatuses")
    long countActiveSessionsByUserId(
            @Param("userId") String userId,
            @Param("activeStatuses") List<UploadSessionStatus> activeStatuses);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE UploadSession s
            SET s.processedFiles = :processedFiles,
                s.failedFiles = :failedFiles,
                s.processedBytes = :processedBytes,
                s.processingCompletedItems = :processingCompletedItems,
                s.processingTotalItems = :processingTotalItems,
                s.processingCurrentFileName = :processingCurrentFileName,
                s.updated = :updatedAt
            WHERE s.id = :sessionId
              AND s.status <> :blockedStatus
            """)
    int updateProgressIfStatusNot(
            @Param("sessionId") String sessionId,
            @Param("processedFiles") int processedFiles,
            @Param("failedFiles") int failedFiles,
            @Param("processedBytes") long processedBytes,
            @Param("processingCompletedItems") int processingCompletedItems,
            @Param("processingTotalItems") int processingTotalItems,
            @Param("processingCurrentFileName") String processingCurrentFileName,
            @Param("updatedAt") LocalDateTime updatedAt,
            @Param("blockedStatus") UploadSessionStatus blockedStatus);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE UploadSession s
            SET s.status = :status,
                s.completedAt = :completedAt,
                s.updated = :updatedAt
            WHERE s.id = :sessionId
              AND s.status <> :blockedStatus
            """)
    int updateStatusAndCompletedAtIfStatusNot(
            @Param("sessionId") String sessionId,
            @Param("status") UploadSessionStatus status,
            @Param("completedAt") LocalDateTime completedAt,
            @Param("updatedAt") LocalDateTime updatedAt,
            @Param("blockedStatus") UploadSessionStatus blockedStatus);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE UploadSession s
            SET s.status = :status,
                s.errorMessage = :errorMessage,
                s.completedAt = :completedAt,
                s.updated = :updatedAt
            WHERE s.id = :sessionId
              AND s.status <> :blockedStatus
            """)
    int updateStatusErrorAndCompletedAtIfStatusNot(
            @Param("sessionId") String sessionId,
            @Param("status") UploadSessionStatus status,
            @Param("errorMessage") String errorMessage,
            @Param("completedAt") LocalDateTime completedAt,
            @Param("updatedAt") LocalDateTime updatedAt,
            @Param("blockedStatus") UploadSessionStatus blockedStatus);

    @Modifying
    @Query("DELETE FROM UploadSession s WHERE s.status IN :statuses AND s.created < :before")
    int deleteStaleSessionsCreatedBefore(
            @Param("statuses") List<UploadSessionStatus> statuses,
            @Param("before") LocalDateTime before);
}
