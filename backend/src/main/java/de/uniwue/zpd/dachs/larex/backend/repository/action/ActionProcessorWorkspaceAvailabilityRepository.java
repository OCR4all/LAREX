package de.uniwue.zpd.dachs.larex.backend.repository.action;

import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorWorkspaceAvailability;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ActionProcessorWorkspaceAvailabilityRepository extends JpaRepository<ActionProcessorWorkspaceAvailability, String> {

    @EntityGraph(attributePaths = {"processorDefinition"})
    List<ActionProcessorWorkspaceAvailability> findByWorkspaceIdAndEnabledTrueOrderByCreatedAsc(String workspaceId);

    @EntityGraph(attributePaths = {"processorDefinition"})
    List<ActionProcessorWorkspaceAvailability> findByProcessorDefinitionIdOrderByWorkspaceIdAsc(String processorDefinitionId);

    Optional<ActionProcessorWorkspaceAvailability> findByProcessorDefinitionIdAndWorkspaceId(String processorDefinitionId,
                                                                                            String workspaceId);

    boolean existsByProcessorDefinitionIdAndWorkspaceIdAndEnabledTrue(String processorDefinitionId, String workspaceId);

    List<ActionProcessorWorkspaceAvailability> findByWorkspaceIdAndProcessorDefinitionIdInAndEnabledTrue(String workspaceId,
                                                                                                         Collection<String> processorDefinitionIds);
}
