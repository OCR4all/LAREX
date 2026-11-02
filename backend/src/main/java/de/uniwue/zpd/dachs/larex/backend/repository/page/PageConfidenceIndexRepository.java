package de.uniwue.zpd.dachs.larex.backend.repository.page;

import de.uniwue.zpd.dachs.larex.backend.entity.PageConfidenceIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Collection;

@Repository
public interface PageConfidenceIndexRepository extends JpaRepository<PageConfidenceIndex, String> {

    boolean existsByPageId(String pageId);

    @Modifying
    @Query("DELETE FROM PageConfidenceIndex p WHERE p.page.id = :pageId")
    void deleteByPageId(@Param("pageId") String pageId);

    @Modifying
    @Query("DELETE FROM PageConfidenceIndex p WHERE p.page.id IN :pageIds")
    int deleteByPageIdIn(@Param("pageIds") Collection<String> pageIds);

    @Query("""
        SELECT DISTINCT p.page.id
        FROM PageConfidenceIndex p
        WHERE p.page.project.id = :projectId
          AND p.confidence >= :minConfidence
          AND p.confidence <= :maxConfidence
          AND p.elementType IN :elementTypes
        """)
    List<String> findPageIdsByProjectIdAndConfidenceRangeAndElementTypes(
        @Param("projectId") String projectId,
        @Param("minConfidence") double minConfidence,
        @Param("maxConfidence") double maxConfidence,
        @Param("elementTypes") List<PageConfidenceIndex.ElementType> elementTypes
    );

    @Query("SELECT COUNT(DISTINCT p.page.id) FROM PageConfidenceIndex p WHERE p.page.project.id = :projectId")
    long countIndexedPagesByProjectId(@Param("projectId") String projectId);

    @Query("SELECT DISTINCT p.page.id FROM PageConfidenceIndex p WHERE p.page.project.id = :projectId")
    List<String> findIndexedPageIdsByProjectId(@Param("projectId") String projectId);

    @Query("""
        SELECT DISTINCT p.page.id
        FROM PageConfidenceIndex p
        WHERE p.page.project.id = :projectId
          AND p.page.id IN :pageIds
        """)
    List<String> findIndexedPageIdsByProjectIdAndPageIds(
        @Param("projectId") String projectId,
        @Param("pageIds") Collection<String> pageIds
    );

    /** Per-page min, max, mean, median and count, aggregated in the database. */
    @Query(value = """
        SELECT pci.page_id,
               MIN(pci.confidence),
               MAX(pci.confidence),
               AVG(pci.confidence),
               PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY pci.confidence),
               COUNT(*)
        FROM page_confidence_index pci
        JOIN pages p ON p.id = pci.page_id
        WHERE p.project_id = :projectId
          AND pci.page_id IN (:pageIds)
          AND pci.element_type = :elementType
        GROUP BY pci.page_id
        """, nativeQuery = true)
    List<Object[]> aggregateConfidenceStatsByProjectIdAndPageIds(
        @Param("projectId") String projectId,
        @Param("pageIds") Collection<String> pageIds,
        @Param("elementType") String elementType
    );
}
