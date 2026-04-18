package de.uniwue.zpd.dachs.larex.backend.repository.dataset;

import de.uniwue.zpd.dachs.larex.backend.entity.DatasetItemCopyXmlVersion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DatasetItemCopyXmlVersionRepository extends JpaRepository<DatasetItemCopyXmlVersion, String> {

    List<DatasetItemCopyXmlVersion> findByCopyFile_IdOrderByVersionNumberDesc(String copyFileId);

    @Query("SELECT COALESCE(MAX(v.versionNumber), 0) FROM DatasetItemCopyXmlVersion v WHERE v.copyFile.id = :copyFileId")
    Integer findMaxVersionNumber(@Param("copyFileId") String copyFileId);

    long countByCopyFile_Id(String copyFileId);

    @Query("SELECT v FROM DatasetItemCopyXmlVersion v WHERE v.copyFile.id = :copyFileId ORDER BY v.versionNumber ASC")
    List<DatasetItemCopyXmlVersion> findOldestVersions(@Param("copyFileId") String copyFileId, Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(v.fileSize), 0)
            FROM DatasetItemCopyXmlVersion v
            WHERE v.copyFile.datasetItem.dataset.workspaceId = :workspaceId
            """)
    Long sumFileSizeByWorkspaceId(@Param("workspaceId") String workspaceId);
}
