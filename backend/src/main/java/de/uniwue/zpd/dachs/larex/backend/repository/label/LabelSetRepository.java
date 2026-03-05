package de.uniwue.zpd.dachs.larex.backend.repository.label;

import de.uniwue.zpd.dachs.larex.backend.entity.LabelSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LabelSetRepository extends JpaRepository<LabelSet, String> {

    List<LabelSet> findByWorkspaceId(String workspaceId);

    Optional<LabelSet> findByIdAndWorkspaceId(String labelSetId, String workspaceId);

    Optional<LabelSet> findByNameAndWorkspaceId(String name, String workspaceId);

    @Query("SELECT ls FROM LabelSet ls WHERE " +
           "ls.workspaceId = :workspaceId AND (" +
            "LOWER(ls.name) LIKE CONCAT('%', LOWER(:query), '%') OR " +
            "LOWER(ls.description) LIKE CONCAT('%', LOWER(:query), '%'))")
    List<LabelSet> findLabelSetsInWorkspaceBySearch(@Param("workspaceId") String workspaceId,
                                                   @Param("query") String query);

    boolean existsByNameAndWorkspaceId(String name, String workspaceId);
}
