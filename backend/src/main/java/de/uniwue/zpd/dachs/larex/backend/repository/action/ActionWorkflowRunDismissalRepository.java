package de.uniwue.zpd.dachs.larex.backend.repository.action;

import de.uniwue.zpd.dachs.larex.backend.entity.ActionWorkflowRunDismissal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Set;

@Repository
public interface ActionWorkflowRunDismissalRepository extends JpaRepository<ActionWorkflowRunDismissal, String> {

    boolean existsByRunIdAndUserId(String runId, String userId);

    @Query("SELECT d.run.id FROM ActionWorkflowRunDismissal d WHERE d.userId = :userId AND d.run.id IN :runIds")
    Set<String> findRunIdsByUserIdAndRunIds(@Param("userId") String userId,
                                             @Param("runIds") Collection<String> runIds);

}
