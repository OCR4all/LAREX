package de.uniwue.zpd.dachs.larex.backend.repository.page;

import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PageRepository extends JpaRepository<Page, String> {

    @EntityGraph(attributePaths = {"images", "project"})
    List<Page> findByProjectId(String projectId);

    @EntityGraph(attributePaths = {"images", "project"})
    @Query("SELECT p FROM Page p WHERE p.project.id = :projectId")
    org.springframework.data.domain.Page<Page> findByProjectId(@Param("projectId") String projectId, Pageable pageable);

    Optional<Page> findByProjectIdAndNameIgnoreCase(String projectId, String name);

    Optional<Page> findByIdAndProjectId(String pageId, String projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"project"})
    @Query("SELECT p FROM Page p WHERE p.id = :pageId AND p.project.id = :projectId")
    Optional<Page> findByIdAndProjectIdForUpdate(@Param("pageId") String pageId,
                                                 @Param("projectId") String projectId);

    @EntityGraph(attributePaths = {"images", "project"})
    @Query("SELECT DISTINCT p FROM Page p JOIN p.tags t WHERE p.project.id = :projectId AND t IN :tags")
    List<Page> findByProjectIdAndTagsIn(@Param("projectId") String projectId, @Param("tags") List<String> tags);

    @EntityGraph(attributePaths = {"images", "project"})
    @Query("SELECT DISTINCT p FROM Page p JOIN p.tags t WHERE p.project.id = :projectId AND t IN :tags")
    org.springframework.data.domain.Page<Page> findByProjectIdAndTagsIn(@Param("projectId") String projectId, @Param("tags") List<String> tags, Pageable pageable);

    @EntityGraph(attributePaths = {"images", "project"})
    @Query("SELECT DISTINCT p FROM Page p LEFT JOIN p.tags t WHERE " +
           "p.project.id = :projectId AND (" +
           "LOWER(p.name) LIKE %:query% OR " +
           "LOWER(p.description) LIKE %:query% OR " +
           "LOWER(t) LIKE %:query%)")
    List<Page> findPagesInProjectBySearch(@Param("projectId") String projectId,
                                          @Param("query") String query);

    @EntityGraph(attributePaths = {"images", "project"})
    @Query("SELECT DISTINCT p FROM Page p LEFT JOIN p.tags t WHERE " +
           "p.project.id = :projectId AND (" +
           "LOWER(p.name) LIKE %:query% OR " +
           "LOWER(p.description) LIKE %:query% OR " +
           "LOWER(t) LIKE %:query%)")
    org.springframework.data.domain.Page<Page> findPagesInProjectBySearch(@Param("projectId") String projectId,
                                                                          @Param("query") String query, Pageable pageable);

    @EntityGraph(attributePaths = {"project"})
    @Query("SELECT DISTINCT p FROM Page p LEFT JOIN p.tags t WHERE " +
           "p.project.id IN :projectIds AND (" +
           "LOWER(p.name) LIKE %:query% OR " +
           "LOWER(p.description) LIKE %:query% OR " +
           "LOWER(t) LIKE %:query%)")
    List<Page> findPagesInProjectsBySearch(@Param("projectIds") Collection<String> projectIds,
                                           @Param("query") String query);

    boolean existsByNameAndProjectId(String name, String projectId);

    Optional<Page> findByProjectIdAndName(String projectId, String name);

    @Query("SELECT DISTINCT p FROM Page p JOIN p.tags t WHERE t = :tag")
    List<Page> findByTagsContaining(@Param("tag") String tag);

    @EntityGraph(attributePaths = {"project"})
    List<Page> findByIdInAndProjectId(List<String> pageIds, String projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Page p WHERE p.project.id = :projectId ORDER BY p.id")
    List<Page> findByProjectIdForUpdate(@Param("projectId") String projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Page p WHERE p.id IN :pageIds AND p.project.id = :projectId ORDER BY p.id")
    List<Page> findByIdInAndProjectIdForUpdate(@Param("pageIds") Collection<String> pageIds,
                                               @Param("projectId") String projectId);

    @EntityGraph(attributePaths = {"project"})
    List<Page> findAllByIdIn(Collection<String> pageIds);

    @Query("SELECT p.project.id, COUNT(p) FROM Page p WHERE p.project.id IN :projectIds GROUP BY p.project.id")
    List<Object[]> countByProjectIds(@Param("projectIds") Collection<String> projectIds);

    @Query("SELECT p.project.id, COUNT(p) FROM Page p WHERE p.project.id IN :projectIds " +
           "AND p.workflowState = :workflowState " +
           "GROUP BY p.project.id")
    List<Object[]> countByProjectIdsAndWorkflowState(@Param("projectIds") Collection<String> projectIds,
                                                     @Param("workflowState") Page.WorkflowState workflowState);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"project"})
    @Query("SELECT p FROM Page p WHERE p.id IN :pageIds ORDER BY p.id")
    List<Page> findAllByIdInForUpdate(@Param("pageIds") Collection<String> pageIds);

    List<Page> findByProjectIdAndNameIn(String projectId, Collection<String> names);

    @Query("SELECT p.name FROM Page p WHERE p.project.id = :projectId")
    List<String> findPageNamesByProjectId(@Param("projectId") String projectId);

    @Query("SELECT p FROM Page p WHERE p.project.id = :projectId AND LOWER(p.name) IN :lowerNames")
    List<Page> findByProjectIdAndLowerNameIn(@Param("projectId") String projectId,
                                             @Param("lowerNames") Collection<String> lowerNames);

    @Modifying
    @Query("DELETE FROM Page p WHERE p.id IN :pageIds")
    int deleteByIdIn(@Param("pageIds") Collection<String> pageIds);

    @Modifying
    @Query(value = "DELETE FROM page_tags WHERE page_id IN (:pageIds)", nativeQuery = true)
    int deleteTagsByPageIds(@Param("pageIds") Collection<String> pageIds);
}
