package de.uniwue.zpd.dachs.larex.backend.repository.dataset;

import de.uniwue.zpd.dachs.larex.backend.entity.DatasetItemCopyFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DatasetItemCopyFileRepository extends JpaRepository<DatasetItemCopyFile, String> {

    List<DatasetItemCopyFile> findByDatasetItemIdOrderByKindAscCreatedAsc(String datasetItemId);

    @Query("""
            SELECT COALESCE(SUM(f.fileSize), 0)
            FROM DatasetItemCopyFile f
            WHERE f.datasetItem.dataset.workspaceId = :workspaceId
            """)
    Long sumFileSizeByWorkspaceId(@Param("workspaceId") String workspaceId);
}
