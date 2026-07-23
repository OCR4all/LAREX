ALTER TABLE action_processor_definitions
    ADD COLUMN outputs_files BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE action_processor_definitions
    DROP CONSTRAINT action_processor_definitions_category_check;

ALTER TABLE action_processor_definitions
    ADD CONSTRAINT action_processor_definitions_category_check
        CHECK (category IN ('WORKFLOW', 'OCR_HTR', 'LAYOUT', 'POSTPROCESSING'));

ALTER TABLE projects
    ADD COLUMN output_retention_days INTEGER;

ALTER TABLE projects
    ADD CONSTRAINT projects_output_retention_days_check
        CHECK (output_retention_days IS NULL OR output_retention_days > 0);

ALTER TABLE stored_files
    DROP CONSTRAINT stored_files_file_type_check;

ALTER TABLE stored_files
    ADD CONSTRAINT stored_files_file_type_check
        CHECK (file_type IN ('IMG', 'XML', 'THUMB', 'OUTPUT'));

CREATE TABLE action_outputs (
    id VARCHAR(255) NOT NULL,
    project_id VARCHAR(255) NOT NULL,
    workspace_id VARCHAR(255) NOT NULL,
    source_run_id VARCHAR(255) NOT NULL,
    processor_definition_id VARCHAR(255) NOT NULL,
    processor_key VARCHAR(128) NOT NULL,
    processor_name VARCHAR(255) NOT NULL,
    created_by_user_id VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    retention_days INTEGER,
    expires_at TIMESTAMP(6) WITHOUT TIME ZONE,
    completed_at TIMESTAMP(6) WITHOUT TIME ZONE,
    total_size_bytes BIGINT NOT NULL DEFAULT 0,
    file_count INTEGER NOT NULL DEFAULT 0,
    share_public_id VARCHAR(64),
    share_secret_hash VARCHAR(64),
    share_secret_prefix VARCHAR(16),
    share_created_by_user_id VARCHAR(255),
    share_created_at TIMESTAMP(6) WITHOUT TIME ZONE,
    share_expires_at TIMESTAMP(6) WITHOUT TIME ZONE,
    share_revoked_at TIMESTAMP(6) WITHOUT TIME ZONE,
    share_last_used_at TIMESTAMP(6) WITHOUT TIME ZONE,
    share_download_count BIGINT NOT NULL DEFAULT 0,
    created TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT action_outputs_pkey PRIMARY KEY (id),
    CONSTRAINT uk_action_outputs_source_run UNIQUE (source_run_id),
    CONSTRAINT uk_action_outputs_share_public_id UNIQUE (share_public_id),
    CONSTRAINT action_outputs_status_check CHECK (status IN ('DRAFT', 'READY', 'DELETING')),
    CONSTRAINT action_outputs_retention_days_check CHECK (retention_days IS NULL OR retention_days > 0),
    CONSTRAINT fk_action_outputs_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE INDEX idx_action_outputs_project_status_created
    ON action_outputs(project_id, status, created DESC);
CREATE INDEX idx_action_outputs_expiry
    ON action_outputs(status, expires_at);

CREATE TABLE action_output_files (
    id VARCHAR(255) NOT NULL,
    output_id VARCHAR(255) NOT NULL,
    stored_file_uuid VARCHAR(32) NOT NULL,
    page_id VARCHAR(255),
    file_name VARCHAR(512) NOT NULL,
    mime_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    checksum_sha256 VARCHAR(64) NOT NULL,
    created TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT action_output_files_pkey PRIMARY KEY (id),
    CONSTRAINT uk_action_output_files_stored_file UNIQUE (stored_file_uuid),
    CONSTRAINT fk_action_output_files_output FOREIGN KEY (output_id) REFERENCES action_outputs(id) ON DELETE CASCADE,
    CONSTRAINT fk_action_output_files_stored_file FOREIGN KEY (stored_file_uuid) REFERENCES stored_files(uuid)
);

CREATE INDEX idx_action_output_files_output ON action_output_files(output_id);
