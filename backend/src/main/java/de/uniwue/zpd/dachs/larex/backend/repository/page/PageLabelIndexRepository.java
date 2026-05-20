package de.uniwue.zpd.dachs.larex.backend.repository.page;

import de.uniwue.zpd.dachs.larex.backend.entity.PageLabelIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PageLabelIndexRepository extends JpaRepository<PageLabelIndex, String> {

    /**
     * Find all label index records for a page.
     */
    List<PageLabelIndex> findByPageId(String pageId);

    /**
     * Delete all label index records for a page (used before re-indexing).
     */
    @Modifying
    @Query("DELETE FROM PageLabelIndex p WHERE p.page.id = :pageId")
    void deleteByPageId(@Param("pageId") String pageId);

    @Modifying
    @Query("DELETE FROM PageLabelIndex p WHERE p.page.id IN :pageIds")
    int deleteByPageIdIn(@Param("pageIds") Collection<String> pageIds);

    /**
     * Find page IDs that have ANY of the given labels (OR mode).
     */
    @Query("SELECT DISTINCT p.page.id FROM PageLabelIndex p WHERE p.page.project.id = :projectId AND p.labelId IN :labelIds")
    List<String> findPageIdsByProjectIdAndLabelIdsIn(
            @Param("projectId") String projectId,
            @Param("labelIds") List<String> labelIds);

    /**
     * Find page IDs that have ALL of the given labels (AND mode).
     * Uses a count approach: page must have all labelIds.
     */
    @Query("SELECT p.page.id FROM PageLabelIndex p WHERE p.page.project.id = :projectId AND p.labelId IN :labelIds GROUP BY p.page.id HAVING COUNT(DISTINCT p.labelId) = :labelCount")
    List<String> findPageIdsByProjectIdAndAllLabelIds(
            @Param("projectId") String projectId,
            @Param("labelIds") List<String> labelIds,
            @Param("labelCount") long labelCount);

    /**
     * Get unique label IDs used in a project (for populating filter dropdown).
     */
    @Query("SELECT DISTINCT p.labelId FROM PageLabelIndex p WHERE p.page.project.id = :projectId")
    List<String> findDistinctLabelIdsByProjectId(@Param("projectId") String projectId);

    /**
     * Count pages per label in a project (for showing filter counts).
     */
    @Query("SELECT p.labelId, COUNT(DISTINCT p.page.id) FROM PageLabelIndex p WHERE p.page.project.id = :projectId GROUP BY p.labelId")
    List<Object[]> countPagesByLabelForProject(@Param("projectId") String projectId);

    /**
     * Check if a page has any indexed labels.
     */
    boolean existsByPageId(String pageId);

    /**
     * Count indexed pages in a project.
     */
    @Query("SELECT COUNT(DISTINCT p.page.id) FROM PageLabelIndex p WHERE p.page.project.id = :projectId")
    long countIndexedPagesByProjectId(@Param("projectId") String projectId);

    @Query("""
            SELECT DISTINCT p.page.id
            FROM PageLabelIndex p
            WHERE p.page.project.id = :projectId
              AND p.page.id IN :pageIds
            """)
    List<String> findIndexedPageIdsByProjectIdAndPageIds(
            @Param("projectId") String projectId,
            @Param("pageIds") Collection<String> pageIds);

    /**
     * Delete all label index records for all pages in a project (used when deleting a project).
     */
    @Modifying
    @Query("DELETE FROM PageLabelIndex p WHERE p.page.project.id = :projectId")
    void deleteByPageProjectId(@Param("projectId") String projectId);
}
