package de.uniwue.zpd.dachs.larex.backend.service.action;

import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionWorkflowRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ActionWorkflowScheduler {
    private static final Logger log = LoggerFactory.getLogger(ActionWorkflowScheduler.class);
    private final ActionWorkflowRunRepository runs;
    private final ActionWorkflowService workflows;

    public ActionWorkflowScheduler(ActionWorkflowRunRepository runs, ActionWorkflowService workflows) {
        this.runs = runs;
        this.workflows = workflows;
    }

    @Scheduled(fixedDelayString = "${larex.actions.workflows.poll-ms:2000}")
    public void continueWorkflows() {
        for (String id : runs.findActiveIds()) {
            var snapshot = runs.findById(id).orElse(null);
            if (snapshot == null) continue;
            try {
                workflows.advance(id, snapshot.getVersion());
            } catch (IllegalArgumentException | IllegalStateException | SecurityException error) {
                // The failed step transaction rolled back. Persist failure in a fresh transaction.
                try {
                    // A concurrent cancellation/continuation invalidates this failure report.
                    workflows.fail(id, snapshot.getVersion(), error.getMessage());
                } catch (RuntimeException failure) {
                    log.warn("Could not record Action workflow failure {}", id, failure);
                }
            } catch (RuntimeException error) {
                // Transient database/locking failures are retried on the next tick.
                log.warn("Could not advance Action workflow {}", id, error);
            }
        }
    }
}
