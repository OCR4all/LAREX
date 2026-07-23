package de.uniwue.zpd.dachs.larex.backend.repository.action;

import de.uniwue.zpd.dachs.larex.backend.entity.ActionOutputFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActionOutputFileRepository extends JpaRepository<ActionOutputFile, String> {
    Optional<ActionOutputFile> findByIdAndOutputId(String id, String outputId);

    @Query("SELECT COALESCE(SUM(f.sizeBytes), 0) FROM ActionOutputFile f WHERE f.output.workspaceId = :workspaceId")
    Long sumReadyFileSizeByWorkspaceId(@Param("workspaceId") String workspaceId);

    @Query("SELECT f.storedFile.storagePath FROM ActionOutputFile f WHERE f.storedFile.status = de.uniwue.zpd.dachs.larex.backend.entity.StoredFile.StoredFileStatus.READY")
    List<String> findAllReadyStoragePaths();
}
