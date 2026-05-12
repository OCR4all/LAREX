package de.uniwue.zpd.dachs.larex.backend.repository.action;

import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorAssignment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ActionProcessorAssignmentRepository extends JpaRepository<ActionProcessorAssignment, String> {

    @EntityGraph(attributePaths = {"processorDefinition"})
    List<ActionProcessorAssignment> findByWorkspaceIdAndProjectIdIsNullOrderByCreatedAsc(String workspaceId);

    @EntityGraph(attributePaths = {"processorDefinition"})
    List<ActionProcessorAssignment> findByWorkspaceIdAndProjectIdOrderByCreatedAsc(String workspaceId, String projectId);

    @EntityGraph(attributePaths = {"processorDefinition"})
    List<ActionProcessorAssignment> findByWorkspaceIdOrderByCreatedAsc(String workspaceId);

    @EntityGraph(attributePaths = {"processorDefinition"})
    @Query("""
            SELECT a FROM ActionProcessorAssignment a
            WHERE a.workspaceId = :workspaceId
              AND (a.projectId IS NULL OR a.projectId = :projectId)
              AND a.enabled = true
              AND a.processorDefinition.enabled = true
            ORDER BY a.created ASC
            """)
    List<ActionProcessorAssignment> findExecutableAssignments(@Param("workspaceId") String workspaceId,
                                                              @Param("projectId") String projectId);

    Optional<ActionProcessorAssignment> findByProcessorDefinitionIdAndWorkspaceIdAndProjectId(String processorDefinitionId,
                                                                                              String workspaceId,
                                                                                              String projectId);

    boolean existsByProcessorDefinitionIdAndWorkspaceId(String processorDefinitionId, String workspaceId);

    @Query("""
            SELECT a FROM ActionProcessorAssignment a
            WHERE a.processorDefinition.id = :definitionId
              AND a.workspaceId = :workspaceId
              AND a.projectId IS NULL
            """)
    Optional<ActionProcessorAssignment> findWorkspaceAssignment(@Param("definitionId") String definitionId,
                                                               @Param("workspaceId") String workspaceId);

    void deleteByProcessorDefinitionIdAndWorkspaceIdAndProjectId(String processorDefinitionId,
                                                                 String workspaceId,
                                                                 String projectId);

    @Query("SELECT a FROM ActionProcessorAssignment a WHERE a.processorDefinition.id IN :definitionIds")
    List<ActionProcessorAssignment> findByDefinitionIds(@Param("definitionIds") Collection<String> definitionIds);
}
