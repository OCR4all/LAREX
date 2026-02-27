package de.uniwue.zpd.dachs.larex.backend.repository;

import de.uniwue.zpd.dachs.larex.backend.entity.StoredFile;
import de.uniwue.zpd.dachs.larex.backend.entity.StoredFile.StoredFileStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StoredFileRepository extends JpaRepository<StoredFile, String> {

    Optional<StoredFile> findByStoragePath(String storagePath);

    List<StoredFile> findByWorkspaceIdAndProjectIdAndStatus(String workspaceId, String projectId, StoredFileStatus status);

    @Modifying
    @Query("UPDATE StoredFile sf SET sf.status = :status WHERE sf.workspaceId = :workspaceId AND sf.projectId = :projectId AND sf.status <> :status")
    int markStatusByWorkspaceAndProject(
            @Param("workspaceId") String workspaceId,
            @Param("projectId") String projectId,
            @Param("status") StoredFileStatus status
    );
}
