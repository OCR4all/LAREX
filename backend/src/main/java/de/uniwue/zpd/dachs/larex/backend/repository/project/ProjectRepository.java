package de.uniwue.zpd.dachs.larex.backend.repository.project;

import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, String> {

    @EntityGraph(attributePaths = {"library", "codec", "labelSet", "tagSet", "tags"})
    List<Project> findByLibraryId(String libraryId);

    @EntityGraph(attributePaths = {"library", "codec", "labelSet", "tagSet", "tags"})
    Page<Project> findByLibraryId(String libraryId, Pageable pageable);

    List<Project> findByLibraryIdAndNameContainingIgnoreCase(String libraryId, String name);

    @EntityGraph(attributePaths = {"library", "codec", "labelSet", "tagSet", "tags"})
    List<Project> findByLibraryWorkspaceId(String workspaceId);

    @EntityGraph(attributePaths = {"library", "codec", "labelSet", "tagSet", "tags"})
    Page<Project> findByLibraryWorkspaceId(String workspaceId, Pageable pageable);

    @EntityGraph(attributePaths = {"library", "codec", "labelSet", "tagSet", "tags"})
    Optional<Project> findWithAssociationsById(String projectId);

    Optional<Project> findByIdAndLibraryWorkspaceId(String projectId, String workspaceId);

    List<Project> findByLibraryWorkspaceIdAndDictionaryId(String workspaceId, String dictionaryId);

    @EntityGraph(attributePaths = {"library", "codec", "labelSet", "tagSet", "tags"})
    @Query("SELECT DISTINCT p FROM Project p JOIN p.tags t WHERE t IN :tags")
    List<Project> findByTagsIn(@Param("tags") List<String> tags);

    @EntityGraph(attributePaths = {"library", "codec", "labelSet", "tagSet", "tags"})
    @Query("SELECT DISTINCT p FROM Project p JOIN p.tags t WHERE p.library.workspaceId = :workspaceId AND t IN :tags")
    List<Project> findByLibraryWorkspaceIdAndTagsIn(@Param("workspaceId") String workspaceId, @Param("tags") List<String> tags);

    @Query("SELECT p.library.workspaceId, COUNT(p) FROM Project p WHERE p.library.workspaceId IN :workspaceIds GROUP BY p.library.workspaceId")
    List<Object[]> countByWorkspaceIds(@Param("workspaceIds") Collection<String> workspaceIds);

    @EntityGraph(attributePaths = {"library", "codec", "labelSet", "tagSet", "tags"})
    @Query("SELECT DISTINCT p FROM Project p LEFT JOIN p.tags t WHERE " +
           "p.library.workspaceId = :workspaceId AND (" +
           "LOWER(p.name) LIKE %:query% OR " +
           "LOWER(p.description) LIKE %:query% OR " +
           "LOWER(t) LIKE %:query%)")
    List<Project> findProjectsInWorkspaceBySearch(@Param("workspaceId") String workspaceId,
                                                  @Param("query") String query);

    @EntityGraph(attributePaths = {"library", "codec", "labelSet", "tagSet", "tags"})
    @Query("SELECT DISTINCT p FROM Project p LEFT JOIN p.tags t WHERE " +
           "p.library.workspaceId = :workspaceId AND (" +
           "LOWER(p.name) LIKE %:query% OR " +
           "LOWER(p.description) LIKE %:query% OR " +
           "LOWER(t) LIKE %:query%)")
    Page<Project> findProjectsInWorkspaceBySearch(@Param("workspaceId") String workspaceId,
                                                  @Param("query") String query, Pageable pageable);

    @EntityGraph(attributePaths = {"library", "codec", "labelSet", "tagSet", "tags"})
    @Query("SELECT DISTINCT p FROM Project p LEFT JOIN p.tags t WHERE " +
           "p.library.workspaceId IN :workspaceIds AND (" +
           "LOWER(p.name) LIKE %:query% OR " +
           "LOWER(p.description) LIKE %:query% OR " +
           "LOWER(t) LIKE %:query%)")
    List<Project> findProjectsInWorkspacesBySearch(@Param("workspaceIds") Collection<String> workspaceIds,
                                                   @Param("query") String query);

    boolean existsByNameAndLibraryId(String name, String libraryId);

    List<Project> findByTagSetId(String tagSetId);
}
