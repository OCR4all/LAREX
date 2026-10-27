CREATE TABLE action_workflows (
    id VARCHAR(255) PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    workspace_id VARCHAR(255) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description TEXT,
    steps_json TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created TIMESTAMP NOT NULL,
    updated TIMESTAMP NOT NULL
);
CREATE INDEX idx_action_workflows_workspace ON action_workflows(workspace_id);

-- Runs retain their own definition/parameter snapshot, including after a workflow is edited or deleted.
CREATE TABLE action_workflow_runs (
    id VARCHAR(255) PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    workspace_id VARCHAR(255) NOT NULL,
    project_id VARCHAR(255) NOT NULL,
    workflow_id VARCHAR(255) NOT NULL,
    workflow_name VARCHAR(120) NOT NULL,
    created_by_user_id VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('RUNNING', 'CANCEL_REQUESTED', 'COMPLETED', 'FAILED', 'CANCELLED')),
    steps_json TEXT NOT NULL,
    page_ids_json TEXT NOT NULL,
    step_run_ids_json TEXT NOT NULL DEFAULT '[]',
    public_api_base_url TEXT NOT NULL,
    error_message TEXT,
    created TIMESTAMP NOT NULL,
    updated TIMESTAMP NOT NULL,
    completed_at TIMESTAMP
);
CREATE INDEX idx_action_workflow_runs_project ON action_workflow_runs(workspace_id, project_id, created DESC);
CREATE INDEX idx_action_workflow_runs_active ON action_workflow_runs(status) WHERE status IN ('RUNNING', 'CANCEL_REQUESTED');
