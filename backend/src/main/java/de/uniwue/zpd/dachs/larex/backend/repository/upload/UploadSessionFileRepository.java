package de.uniwue.zpd.dachs.larex.backend.repository.upload;

import de.uniwue.zpd.dachs.larex.backend.entity.UploadSessionFile;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSessionFile.UploadFileStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UploadSessionFileRepository extends JpaRepository<UploadSessionFile, String> {

    @Query("SELECT f FROM UploadSessionFile f WHERE f.session.id = :sessionId")
    List<UploadSessionFile> findBySessionId(@Param("sessionId") String sessionId);

    @Query("SELECT f FROM UploadSessionFile f WHERE f.id = :fileId AND f.session.id = :sessionId")
    Optional<UploadSessionFile> findByIdAndSessionId(@Param("fileId") String fileId, @Param("sessionId") String sessionId);

    @Query("SELECT f FROM UploadSessionFile f WHERE f.session.id = :sessionId AND f.status = :status")
    List<UploadSessionFile> findBySessionIdAndStatus(@Param("sessionId") String sessionId, @Param("status") UploadFileStatus status);

    @Query("SELECT f FROM UploadSessionFile f WHERE f.session.id = :sessionId AND f.status IN :statuses")
    List<UploadSessionFile> findBySessionIdAndStatusIn(
            @Param("sessionId") String sessionId,
            @Param("statuses") List<UploadFileStatus> statuses);

    @Query("SELECT COUNT(f) FROM UploadSessionFile f WHERE f.session.id = :sessionId AND f.status = :status")
    long countBySessionIdAndStatus(
            @Param("sessionId") String sessionId,
            @Param("status") UploadFileStatus status);

    @Query("SELECT SUM(f.fileSize) FROM UploadSessionFile f WHERE f.session.id = :sessionId AND f.status = :status")
    Long sumFileSizeBySessionIdAndStatus(
            @Param("sessionId") String sessionId,
            @Param("status") UploadFileStatus status);

    boolean existsBySessionIdAndOriginalFileName(String sessionId, String originalFileName);

    @Query("SELECT f FROM UploadSessionFile f " +
           "JOIN f.session s " +
           "WHERE s.projectId = :projectId AND f.status = :status")
    List<UploadSessionFile> findByProjectIdAndStatus(
            @Param("projectId") String projectId,
            @Param("status") UploadFileStatus status);

    @Query("SELECT COUNT(f) > 0 FROM UploadSessionFile f " +
           "JOIN f.session s " +
           "WHERE s.projectId = :projectId AND f.status = :status")
    boolean existsByProjectIdAndStatus(
            @Param("projectId") String projectId,
            @Param("status") UploadFileStatus status);
}
