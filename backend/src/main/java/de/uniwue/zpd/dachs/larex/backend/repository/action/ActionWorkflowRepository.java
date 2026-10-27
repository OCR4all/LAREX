package de.uniwue.zpd.dachs.larex.backend.repository.action;

import de.uniwue.zpd.dachs.larex.backend.entity.ActionWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ActionWorkflowRepository extends JpaRepository<ActionWorkflow, String> {
    List<ActionWorkflow> findByWorkspaceIdOrderByNameAsc(String workspaceId);
    Optional<ActionWorkflow> findByIdAndWorkspaceId(String id, String workspaceId);
}
