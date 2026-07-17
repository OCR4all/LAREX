package de.uniwue.zpd.dachs.larex.backend.repository.page;

import de.uniwue.zpd.dachs.larex.backend.entity.PageXmlVersion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.LocalDateTime;

@Repository
public interface PageXmlVersionRepository extends JpaRepository<PageXmlVersion, String> {

    List<PageXmlVersion> findByPageXml_IdOrderByVersionNumberDesc(String pageXmlId);

    @Query("SELECT COALESCE(MAX(v.versionNumber), 0) FROM PageXmlVersion v WHERE v.pageXml.id = :pageXmlId")
    Integer findMaxVersionNumber(@Param("pageXmlId") String pageXmlId);

    long countByPageXml_Id(String pageXmlId);

    @Query("SELECT v FROM PageXmlVersion v WHERE v.pageXml.id = :pageXmlId ORDER BY v.versionNumber ASC")
    List<PageXmlVersion> findOldestVersions(@Param("pageXmlId") String pageXmlId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(v.fileSize), 0) FROM PageXmlVersion v WHERE v.pageXml.page.project.library.workspaceId = :workspaceId")
    Long sumFileSizeByWorkspaceId(@Param("workspaceId") String workspaceId);

    @Query("SELECT v.filePath FROM PageXmlVersion v WHERE v.filePath IS NOT NULL")
    List<String> findAllFilePaths();

    @Modifying
    @Query(value = "UPDATE page_xml_versions SET created = :created WHERE id = :id", nativeQuery = true)
    int updateCreatedTimestamp(@Param("id") String id, @Param("created") LocalDateTime created);
}
