package de.uniwue.zpd.dachs.larex.backend.repository.tag;

import de.uniwue.zpd.dachs.larex.backend.entity.TagSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TagSetRepository extends JpaRepository<TagSet, String> {

    List<TagSet> findByWorkspaceId(String workspaceId);

    Optional<TagSet> findByIdAndWorkspaceId(String tagSetId, String workspaceId);

    Optional<TagSet> findByNameAndWorkspaceId(String name, String workspaceId);

    @Query("SELECT t FROM TagSet t WHERE " +
           "t.workspaceId = :workspaceId AND (" +
            "LOWER(t.name) LIKE CONCAT('%', LOWER(:query), '%') OR " +
            "LOWER(t.description) LIKE CONCAT('%', LOWER(:query), '%'))")
    List<TagSet> findTagSetsInWorkspaceBySearch(@Param("workspaceId") String workspaceId,
                                                 @Param("query") String query);

    boolean existsByNameAndWorkspaceId(String name, String workspaceId);
}
