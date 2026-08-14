package de.uniwue.zpd.dachs.larex.backend.repository.page;

import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PageXmlRepository extends JpaRepository<PageXml, String> {

    Optional<PageXml> findByPage_Id(String pageId);

    boolean existsByPage_Id(String pageId);

    @Query("SELECT px.page.id FROM PageXml px WHERE px.page.project.id = :projectId")
    List<String> findPageIdsByProjectId(@Param("projectId") String projectId);

    @Query("SELECT COUNT(px) FROM PageXml px WHERE px.page.project.id = :projectId AND px.schema = de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema.PAGE_XML")
    long countPageXmlByProjectId(@Param("projectId") String projectId);

    List<PageXml> findByBaseName(String baseName);

    List<PageXml> findByPage_IdIn(Collection<String> pageIds);

    @Modifying
    @Query("DELETE FROM PageXml px WHERE px.page.id IN :pageIds")
    int deleteByPageIdIn(@Param("pageIds") Collection<String> pageIds);
    
    @Query("SELECT px.filePath FROM PageXml px")
    List<String> findAllFilePaths();

    @Query("SELECT COALESCE(SUM(px.fileSize), 0) FROM PageXml px WHERE px.page.project.id = :projectId")
    Long sumFileSizeByProjectId(@Param("projectId") String projectId);

    @Query("SELECT COALESCE(SUM(px.fileSize), 0) FROM PageXml px WHERE px.page.project.library.workspaceId = :workspaceId")
    Long sumFileSizeByWorkspaceId(@Param("workspaceId") String workspaceId);

    @Query("SELECT px.page.id, px FROM PageXml px WHERE px.page.id IN :pageIds")
    List<Object[]> findByPageIds(@Param("pageIds") Collection<String> pageIds);

    @Query("SELECT px.page.project.id, COALESCE(SUM(px.fileSize), 0) " +
           "FROM PageXml px " +
           "WHERE px.page.project.id IN :projectIds " +
           "GROUP BY px.page.project.id")
    List<Object[]> sumFileSizeByProjectIds(@Param("projectIds") Collection<String> projectIds);
}
