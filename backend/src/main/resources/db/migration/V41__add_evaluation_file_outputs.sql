ALTER TABLE action_runs ADD COLUMN evaluation_output_mode VARCHAR(16) NOT NULL DEFAULT 'REPORT';
ALTER TABLE action_runs ADD CONSTRAINT action_runs_evaluation_output_mode_check CHECK (evaluation_output_mode IN ('REPORT', 'FILES'));
ALTER TABLE datasets ADD COLUMN output_retention_days INTEGER;
ALTER TABLE datasets ADD CONSTRAINT datasets_output_retention_days_check CHECK (output_retention_days IS NULL OR output_retention_days > 0);

ALTER TABLE action_outputs ALTER COLUMN project_id DROP NOT NULL;
ALTER TABLE action_outputs ADD COLUMN dataset_id VARCHAR(255) REFERENCES datasets(id);
ALTER TABLE action_outputs ADD CONSTRAINT action_outputs_owner_check CHECK ((project_id IS NOT NULL AND dataset_id IS NULL) OR (project_id IS NULL AND dataset_id IS NOT NULL));
CREATE INDEX idx_action_outputs_dataset_status ON action_outputs(dataset_id, status);

ALTER TABLE stored_files ALTER COLUMN project_id DROP NOT NULL;
ALTER TABLE stored_files ADD COLUMN dataset_id VARCHAR(255);
ALTER TABLE stored_files ADD CONSTRAINT stored_files_owner_check CHECK ((project_id IS NOT NULL AND dataset_id IS NULL) OR (project_id IS NULL AND dataset_id IS NOT NULL AND file_type = 'OUTPUT'));
CREATE INDEX idx_stored_files_dataset_status ON stored_files(dataset_id, status);
