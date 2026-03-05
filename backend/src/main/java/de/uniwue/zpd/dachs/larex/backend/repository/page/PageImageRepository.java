package de.uniwue.zpd.dachs.larex.backend.repository.page;

import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PageImageRepository extends JpaRepository<PageImage, String> {
    
    List<PageImage> findByPageId(String pageId);
    
    List<PageImage> findByPageIdAndVariant(String pageId, String variant);
    
    List<PageImage> findByPageIdAndBaseName(String pageId, String baseName);
    
    List<PageImage> findByBaseName(String baseName);

    List<PageImage> findByPageIdIn(Collection<String> pageIds);
    
    @Query("SELECT COALESCE(SUM(pi.fileSize), 0) FROM PageImage pi WHERE pi.page.project.id = :projectId")
    Long sumFileSizeByProjectId(@Param("projectId") String projectId);

    @Query("SELECT COALESCE(SUM(pi.fileSize), 0) FROM PageImage pi WHERE pi.page.project.library.workspaceId = :workspaceId")
    Long sumFileSizeByWorkspaceId(@Param("workspaceId") String workspaceId);

    @Query("SELECT pi.filePath FROM PageImage pi WHERE pi.filePath IS NOT NULL")
    List<String> findAllFilePaths();

    @Query("SELECT pi.thumbnailPath FROM PageImage pi WHERE pi.thumbnailPath IS NOT NULL")
    List<String> findAllThumbnailPaths();

    @Query("SELECT pi.page.id, pi FROM PageImage pi WHERE pi.page.id IN :pageIds")
    List<Object[]> findByPageIds(@Param("pageIds") Collection<String> pageIds);

    @Query("SELECT pi.page.project.id, COALESCE(SUM(pi.fileSize), 0) " +
           "FROM PageImage pi " +
           "WHERE pi.page.project.id IN :projectIds " +
           "GROUP BY pi.page.project.id")
    List<Object[]> sumFileSizeByProjectIds(@Param("projectIds") Collection<String> projectIds);
}
