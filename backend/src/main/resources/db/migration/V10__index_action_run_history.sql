CREATE INDEX idx_action_runs_workspace_created
    ON action_runs(workspace_id, created DESC);

CREATE INDEX idx_action_runs_workspace_project_created
    ON action_runs(workspace_id, project_id, created DESC);

CREATE INDEX idx_action_runs_processor_created
    ON action_runs(processor_definition_id, created DESC);
