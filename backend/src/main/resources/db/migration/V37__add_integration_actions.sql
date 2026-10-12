ALTER TABLE action_runs ADD COLUMN input_requirements_json TEXT;

ALTER TABLE action_processor_definitions DROP CONSTRAINT action_processor_definitions_action_kind_check;
ALTER TABLE action_processor_definitions ADD CONSTRAINT action_processor_definitions_action_kind_check
    CHECK (action_kind IN ('PROCESSING', 'TRAINING', 'EVALUATION', 'INTEGRATION'));
ALTER TABLE action_runs DROP CONSTRAINT action_runs_run_kind_check;
ALTER TABLE action_runs ADD CONSTRAINT action_runs_run_kind_check
    CHECK (run_kind IN ('PROCESSING', 'TRAINING', 'EVALUATION', 'INTEGRATION'));
ALTER TABLE action_runs DROP CONSTRAINT action_runs_resource_scope_check;
ALTER TABLE action_runs ADD CONSTRAINT action_runs_resource_scope_check CHECK (
    (run_kind = 'PROCESSING' AND project_id IS NOT NULL AND dataset_id IS NULL)
    OR (run_kind IN ('TRAINING', 'EVALUATION') AND project_id IS NULL AND dataset_id IS NOT NULL)
    OR (run_kind = 'INTEGRATION' AND ((project_id IS NOT NULL AND dataset_id IS NULL)
        OR (project_id IS NULL AND dataset_id IS NOT NULL)))
);
CREATE TABLE action_input_snapshots (
    id VARCHAR(255) PRIMARY KEY,
    run_id VARCHAR(255) NOT NULL REFERENCES action_runs(id) ON DELETE CASCADE,
    input_id VARCHAR(255) NOT NULL,
    page_name VARCHAR(255) NOT NULL,
    source_page_id VARCHAR(255) NOT NULL,
    split VARCHAR(255),
    file_type VARCHAR(255) NOT NULL CHECK (file_type IN ('images', 'xml')),
    source_file_id VARCHAR(255) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    variant VARCHAR(255),
    mime_type VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    checksum_sha256 VARCHAR(255) NOT NULL,
    snapshot_path TEXT NOT NULL,
    position INTEGER NOT NULL,
    UNIQUE (run_id, position)
);
CREATE INDEX idx_action_input_snapshots_run ON action_input_snapshots(run_id);
