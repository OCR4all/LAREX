package de.uniwue.zpd.dachs.larex.backend.repository;

import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PageXmlRepository extends JpaRepository<PageXml, String> {

    List<PageXml> findByPage_Id(String pageId);

    List<PageXml> findByPage_IdAndVariant(String pageId, String variant);

    List<PageXml> findByPage_IdAndBaseName(String pageId, String baseName);

    List<PageXml> findByBaseName(String baseName);

    List<PageXml> findByPage_IdIn(Collection<String> pageIds);
    
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
