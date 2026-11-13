package de.uniwue.zpd.dachs.larex.backend.repository.action;

import de.uniwue.zpd.dachs.larex.backend.entity.ActionWorkspaceExecuteRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActionWorkspaceExecuteRoleRepository extends JpaRepository<ActionWorkspaceExecuteRole, String> {

    List<ActionWorkspaceExecuteRole> findByWorkspaceId(String workspaceId);

    Optional<ActionWorkspaceExecuteRole> findByProcessorDefinitionIdAndWorkspaceId(String processorDefinitionId, String workspaceId);

    List<ActionWorkspaceExecuteRole> findByProcessorDefinitionId(String processorDefinitionId);
}
