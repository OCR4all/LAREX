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

    @Query("""
        SELECT p.page.id, p.confidence
        FROM PageConfidenceIndex p
        WHERE p.page.project.id = :projectId
          AND p.page.id IN :pageIds
          AND p.elementType = :elementType
        ORDER BY p.page.id, p.confidence
        """)
    List<Object[]> findConfidenceValuesByProjectIdAndPageIdsAndElementType(
        @Param("projectId") String projectId,
        @Param("pageIds") Collection<String> pageIds,
        @Param("elementType") PageConfidenceIndex.ElementType elementType
    );
}
