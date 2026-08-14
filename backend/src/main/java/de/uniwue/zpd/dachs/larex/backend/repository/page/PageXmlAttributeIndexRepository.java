package de.uniwue.zpd.dachs.larex.backend.repository.page;

import de.uniwue.zpd.dachs.larex.backend.entity.PageXmlAttributeIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PageXmlAttributeIndexRepository extends JpaRepository<PageXmlAttributeIndex, String> {

    @Modifying
    @Query("DELETE FROM PageXmlAttributeIndex p WHERE p.page.id = :pageId")
    void deleteByPageId(@Param("pageId") String pageId);

    @Modifying
    @Query("DELETE FROM PageXmlAttributeIndex p WHERE p.page.id IN :pageIds")
    int deleteByPageIdIn(@Param("pageIds") Collection<String> pageIds);

    boolean existsByPageId(String pageId);

    @Query("SELECT COUNT(DISTINCT p.page.id) FROM PageXmlAttributeIndex p WHERE p.page.project.id = :projectId")
    long countIndexedPagesByProjectId(@Param("projectId") String projectId);

    @Query("""
            SELECT DISTINCT p.page.id
            FROM PageXmlAttributeIndex p
            WHERE p.page.project.id = :projectId
              AND p.page.id IN :pageIds
            """)
    List<String> findIndexedPageIdsByProjectIdAndPageIds(
            @Param("projectId") String projectId,
            @Param("pageIds") Collection<String> pageIds);

    @Query("""
            SELECT DISTINCT p.page.id
            FROM PageXmlAttributeIndex p
            WHERE p.page.project.id = :projectId
              AND p.attributeName = :attributeName
              AND (:elementName IS NULL OR p.elementName = :elementName)
            """)
    List<String> findPageIdsWithAttribute(
            @Param("projectId") String projectId,
            @Param("elementName") String elementName,
            @Param("attributeName") String attributeName);

    @Query("""
            SELECT DISTINCT p.page.id
            FROM PageXmlAttributeIndex p
            WHERE p.page.project.id = :projectId
              AND p.attributeName = :attributeName
              AND (:elementName IS NULL OR p.elementName = :elementName)
              AND p.attributeValue = :attributeValue
            """)
    List<String> findPageIdsWithAttributeValue(
            @Param("projectId") String projectId,
            @Param("elementName") String elementName,
            @Param("attributeName") String attributeName,
            @Param("attributeValue") String attributeValue);

    @Query("""
            SELECT DISTINCT p.page.id
            FROM PageXmlAttributeIndex p
            WHERE p.page.project.id = :projectId
              AND p.attributeName = :attributeName
              AND (:elementName IS NULL OR p.elementName = :elementName)
              AND LOCATE(:attributeValue, p.attributeValue) > 0
            """)
    List<String> findPageIdsWithAttributeValueContaining(
            @Param("projectId") String projectId,
            @Param("elementName") String elementName,
            @Param("attributeName") String attributeName,
            @Param("attributeValue") String attributeValue);

    @Query("""
            SELECT p.elementName, p.attributeName, COUNT(DISTINCT p.page.id)
            FROM PageXmlAttributeIndex p
            WHERE p.page.project.id = :projectId
            GROUP BY p.elementName, p.attributeName
            ORDER BY p.attributeName, p.elementName
            """)
    List<Object[]> findAvailableAttributesByProjectId(@Param("projectId") String projectId);
}
