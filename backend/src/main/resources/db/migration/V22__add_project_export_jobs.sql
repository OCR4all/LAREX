CREATE TABLE project_export_jobs (
    cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
    artifact_size BIGINT,
    completed_at TIMESTAMP WITHOUT TIME ZONE,
    created TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITHOUT TIME ZONE,
    last_heartbeat_at TIMESTAMP WITHOUT TIME ZONE,
    lease_expires_at TIMESTAMP WITHOUT TIME ZONE,
    started_at TIMESTAMP WITHOUT TIME ZONE,
    updated TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    artifact_checksum_sha256 VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    created_by_user_id VARCHAR(255) NOT NULL,
    id VARCHAR(255) NOT NULL,
    lease_owner VARCHAR(255),
    workspace_id VARCHAR(255) NOT NULL,
    artifact_file_name TEXT,
    artifact_path TEXT,
    error_message TEXT,
    request_json TEXT NOT NULL,
    CONSTRAINT project_export_jobs_pkey PRIMARY KEY (id),
    CONSTRAINT project_export_jobs_status_check CHECK (status IN (
        'QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED', 'EXPIRED'
    ))
);

CREATE INDEX idx_project_export_jobs_queue
    ON project_export_jobs (status, created);

CREATE INDEX idx_project_export_jobs_owner
    ON project_export_jobs (workspace_id, created_by_user_id, created DESC);

CREATE INDEX idx_project_export_jobs_expiry
    ON project_export_jobs (status, expires_at);

CREATE INDEX idx_project_export_jobs_lease
    ON project_export_jobs (status, lease_expires_at);
