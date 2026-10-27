package de.uniwue.zpd.dachs.larex.backend.repository.action;

import de.uniwue.zpd.dachs.larex.backend.entity.ActionWorkflowRun;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import java.util.List;
import java.util.Optional;

public interface ActionWorkflowRunRepository extends JpaRepository<ActionWorkflowRun, String> {
    List<ActionWorkflowRun> findByWorkspaceIdAndProjectIdOrderByCreatedDesc(String workspaceId, String projectId, Pageable pageable);

    List<ActionWorkflowRun> findByWorkflowIdOrderByCreatedDesc(String workflowId, Pageable pageable);

    boolean existsByWorkflowIdAndStatusIn(String workflowId, List<String> statuses);

    @Query("select r.id from ActionWorkflowRun r where r.status in ('RUNNING', 'CANCEL_REQUESTED')")
    List<String> findActiveIds();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ActionWorkflowRun r where r.id = :id")
    Optional<ActionWorkflowRun> findByIdForUpdate(String id);
}
