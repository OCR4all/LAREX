package de.uniwue.zpd.dachs.larex.backend.repository.action;

import de.uniwue.zpd.dachs.larex.backend.entity.ActionRun;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionRun.Status;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ActionRunRepository extends JpaRepository<ActionRun, String> {

    @EntityGraph(attributePaths = {"processorDefinition"})
    @Query("SELECT r FROM ActionRun r WHERE r.id = :id")
    Optional<ActionRun> findWithProcessorDefinitionById(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"processorDefinition"})
    @Query("SELECT r FROM ActionRun r WHERE r.id = :id")
    Optional<ActionRun> findWithProcessorDefinitionByIdForUpdate(@Param("id") String id);

    @EntityGraph(attributePaths = {"processorDefinition"})
    List<ActionRun> findByWorkspaceIdAndProjectIdOrderByCreatedDesc(String workspaceId, String projectId);

    @EntityGraph(attributePaths = {"processorDefinition"})
    List<ActionRun> findByProcessorDefinitionIdOrderByCreatedDesc(String processorDefinitionId);

    List<ActionRun> findByStatusIn(Collection<Status> statuses);

    List<ActionRun> findByProcessorDefinitionIdAndStatusIn(String processorDefinitionId, Collection<Status> statuses);
}
