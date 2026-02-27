package de.uniwue.zpd.dachs.larex.backend.repository;

import de.uniwue.zpd.dachs.larex.backend.entity.UploadSession;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSession.UploadSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    @Modifying
    @Query("DELETE FROM UploadSession s WHERE s.status IN :statuses AND s.created < :before")
    int deleteStaleSessionsCreatedBefore(
            @Param("statuses") List<UploadSessionStatus> statuses,
            @Param("before") LocalDateTime before);
}
