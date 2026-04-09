package de.uniwue.zpd.dachs.larex.backend.repository.dataset;

import de.uniwue.zpd.dachs.larex.backend.entity.DatasetRelease;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DatasetReleaseRepository extends JpaRepository<DatasetRelease, String> {

    List<DatasetRelease> findByDatasetIdOrderByVersionNumberDesc(String datasetId);

    Optional<DatasetRelease> findByIdAndDatasetId(String id, String datasetId);

    Optional<DatasetRelease> findBySharePublicId(String sharePublicId);

    @Query("""
            SELECT COALESCE(MAX(r.versionNumber), 0)
            FROM DatasetRelease r
            WHERE r.dataset.id = :datasetId
            """)
    Integer findMaxVersionNumberByDatasetId(@Param("datasetId") String datasetId);

    @Query("""
            SELECT COALESCE(SUM(r.packageFileSize), 0)
            FROM DatasetRelease r
            WHERE r.dataset.workspaceId = :workspaceId AND r.packageFileSize IS NOT NULL
            """)
    Long sumPackageFileSizeByWorkspaceId(@Param("workspaceId") String workspaceId);
}
