package de.uniwue.zpd.dachs.larex.backend.repository.page;

import de.uniwue.zpd.dachs.larex.backend.entity.PageTextContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PageTextContentRepository extends JpaRepository<PageTextContent, String> {

    /**
     * Find all text content records for a page.
     */
    List<PageTextContent> findByPageId(String pageId);

    /**
     * Delete all text content records for a page (used before re-indexing).
     */
    @Modifying
    @Query("DELETE FROM PageTextContent p WHERE p.page.id = :pageId")
    void deleteByPageId(@Param("pageId") String pageId);

    @Modifying
    @Query("DELETE FROM PageTextContent p WHERE p.page.id IN :pageIds")
    int deleteByPageIdIn(@Param("pageIds") Collection<String> pageIds);

    /**
     * Find page IDs that contain the given text substring (case-insensitive).
     */
    @Query("SELECT DISTINCT p.page.id FROM PageTextContent p WHERE LOWER(p.textContent) LIKE LOWER(CONCAT('%', :searchText, '%'))")
    List<String> findPageIdsByTextContentContaining(@Param("searchText") String searchText);

    /**
     * Find page IDs within a project that contain the given text substring.
     */
    @Query("SELECT DISTINCT p.page.id FROM PageTextContent p WHERE p.page.project.id = :projectId AND LOWER(p.textContent) LIKE LOWER(CONCAT('%', :searchText, '%'))")
    List<String> findPageIdsByProjectIdAndTextContentContaining(
            @Param("projectId") String projectId,
            @Param("searchText") String searchText);

    /**
     * Find text line IDs within a page that contain the given text substring.
     */
    @Query("SELECT DISTINCT p.textLineId FROM PageTextContent p WHERE p.page.id = :pageId AND p.textLineId IS NOT NULL AND LOWER(p.textContent) LIKE LOWER(CONCAT('%', :searchText, '%'))")
    List<String> findTextLineIdsByPageIdAndTextContentContaining(
            @Param("pageId") String pageId,
            @Param("searchText") String searchText);

    /**
     * Find text region IDs within a page that contain the given text substring.
     */
    @Query("SELECT DISTINCT p.regionId FROM PageTextContent p WHERE p.page.id = :pageId AND p.regionId IS NOT NULL AND LOWER(p.textContent) LIKE LOWER(CONCAT('%', :searchText, '%'))")
    List<String> findRegionIdsByPageIdAndTextContentContaining(
            @Param("pageId") String pageId,
            @Param("searchText") String searchText);

    /**
     * Find all indexed text content rows for a project.
     */
    @Query("SELECT p FROM PageTextContent p JOIN FETCH p.page WHERE p.page.project.id = :projectId")
    List<PageTextContent> findByProjectId(@Param("projectId") String projectId);

    /**
     * Find all indexed text content rows for selected pages in a project.
     */
    @Query("SELECT p FROM PageTextContent p JOIN FETCH p.page WHERE p.page.project.id = :projectId AND p.page.id IN :pageIds")
    List<PageTextContent> findByProjectIdAndPageIds(
            @Param("projectId") String projectId,
            @Param("pageIds") List<String> pageIds);

    /**
     * Check if a page has any indexed text content.
     */
    boolean existsByPageId(String pageId);

    /**
     * Count indexed pages in a project.
     */
    @Query("SELECT COUNT(DISTINCT p.page.id) FROM PageTextContent p WHERE p.page.project.id = :projectId")
    long countIndexedPagesByProjectId(@Param("projectId") String projectId);

    /**
     * Delete all text content records for all pages in a project (used when deleting a project).
     */
    @Modifying
    @Query("DELETE FROM PageTextContent p WHERE p.page.project.id = :projectId")
    void deleteByPageProjectId(@Param("projectId") String projectId);
}
