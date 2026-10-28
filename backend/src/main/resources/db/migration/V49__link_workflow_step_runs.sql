ALTER TABLE action_runs ADD COLUMN workflow_run_id VARCHAR(255)
    REFERENCES action_workflow_runs(id) ON DELETE SET NULL;
CREATE INDEX idx_action_runs_workflow_run ON action_runs(workflow_run_id);

-- Recover parent identity for existing children without depending on the UI's history window.
UPDATE action_runs r SET workflow_run_id = w.id
FROM action_workflow_runs w
WHERE r.id IN (SELECT jsonb_array_elements_text(w.step_run_ids_json::jsonb));

CREATE TABLE action_workflow_run_dismissals (
    id VARCHAR(255) PRIMARY KEY,
    run_id VARCHAR(255) NOT NULL REFERENCES action_workflow_runs(id) ON DELETE CASCADE,
    user_id VARCHAR(255) NOT NULL,
    created TIMESTAMP NOT NULL,
    CONSTRAINT uk_action_workflow_run_dismissals_run_user UNIQUE (run_id, user_id)
);
CREATE INDEX idx_action_workflow_run_dismissals_user ON action_workflow_run_dismissals(user_id);
CREATE INDEX idx_action_workflow_run_dismissals_run ON action_workflow_run_dismissals(run_id);

-- Preserve previous per-user dismissals when every child was dismissed.
INSERT INTO action_workflow_run_dismissals (id, run_id, user_id, created)
SELECT gen_random_uuid()::text, w.id, d.user_id, CURRENT_TIMESTAMP
FROM action_workflow_runs w
JOIN action_runs r ON r.workflow_run_id = w.id
JOIN action_run_dismissals d ON d.run_id = r.id
WHERE w.status NOT IN ('RUNNING', 'CANCEL_REQUESTED')
GROUP BY w.id, d.user_id, w.step_run_ids_json
HAVING COUNT(DISTINCT r.id) = jsonb_array_length(w.step_run_ids_json::jsonb);
