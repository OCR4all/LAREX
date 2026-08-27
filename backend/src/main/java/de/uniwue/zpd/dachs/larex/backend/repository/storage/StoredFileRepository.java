package de.uniwue.zpd.dachs.larex.backend.repository.storage;

import de.uniwue.zpd.dachs.larex.backend.entity.StoredFile;
import de.uniwue.zpd.dachs.larex.backend.entity.StoredFile.StoredFileStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface StoredFileRepository extends JpaRepository<StoredFile, String> {

    Optional<StoredFile> findByStoragePath(String storagePath);

    List<StoredFile> findByStoragePathIn(Collection<String> storagePaths);

    @Query(value = """
            SELECT asset_refs.storage_path
            FROM (
                SELECT page_images.file_path AS storage_path
                FROM page_images
                WHERE page_images.file_path IN (:storagePaths)
                UNION ALL
                SELECT page_images.thumbnail_path AS storage_path
                FROM page_images
                WHERE page_images.thumbnail_path IN (:storagePaths)
                UNION ALL
                SELECT page_xmls.file_path AS storage_path
                FROM page_xmls
                WHERE page_xmls.file_path IN (:storagePaths)
                UNION ALL
                SELECT page_xml_versions.file_path AS storage_path
                FROM page_xml_versions
                WHERE page_xml_versions.file_path IN (:storagePaths)
            ) asset_refs
            GROUP BY asset_refs.storage_path
            HAVING COUNT(*) > 1
            """, nativeQuery = true)
    List<String> findPageAssetPathsWithMultipleReferences(
            @Param("storagePaths") Collection<String> storagePaths
    );

    @Query(value = """
            SELECT DISTINCT asset_refs.storage_path
            FROM (
                SELECT page_images.file_path AS storage_path
                FROM page_images
                WHERE page_images.file_path IN (:storagePaths)
                UNION ALL
                SELECT page_images.thumbnail_path AS storage_path
                FROM page_images
                WHERE page_images.thumbnail_path IN (:storagePaths)
                UNION ALL
                SELECT page_xmls.file_path AS storage_path
                FROM page_xmls
                WHERE page_xmls.file_path IN (:storagePaths)
                UNION ALL
                SELECT page_xml_versions.file_path AS storage_path
                FROM page_xml_versions
                WHERE page_xml_versions.file_path IN (:storagePaths)
            ) asset_refs
            """, nativeQuery = true)
    List<String> findReferencedPageAssetPaths(
            @Param("storagePaths") Collection<String> storagePaths
    );

    List<StoredFile> findByWorkspaceIdAndProjectIdAndStatus(String workspaceId, String projectId, StoredFileStatus status);

    List<StoredFile> findByProjectIdAndStatus(String projectId, StoredFileStatus status);

    @Modifying
    @Query("UPDATE StoredFile sf SET sf.status = :status WHERE sf.workspaceId = :workspaceId AND sf.projectId = :projectId AND sf.status <> :status")
    int markStatusByWorkspaceAndProject(
            @Param("workspaceId") String workspaceId,
            @Param("projectId") String projectId,
            @Param("status") StoredFileStatus status
    );

    @Modifying
    @Query("UPDATE StoredFile sf SET sf.status = :status WHERE sf.storagePath IN :storagePaths AND sf.status <> :status")
    int markStatusByStoragePaths(
            @Param("storagePaths") Collection<String> storagePaths,
            @Param("status") StoredFileStatus status
    );
}
