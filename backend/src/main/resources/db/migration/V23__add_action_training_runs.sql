ALTER TABLE action_processor_definitions
    ADD COLUMN action_kind VARCHAR(32) NOT NULL DEFAULT 'PROCESSING';

ALTER TABLE action_processor_definitions DROP CONSTRAINT IF EXISTS action_processor_definitions_lock_mode_check;
ALTER TABLE action_processor_definitions ADD CONSTRAINT action_processor_definitions_lock_mode_check
    CHECK (lock_mode IN ('PAGES', 'PROJECT', 'NONE'));
ALTER TABLE action_processor_definitions ADD CONSTRAINT action_processor_definitions_action_kind_check
    CHECK (action_kind IN ('PROCESSING', 'TRAINING'));

ALTER TABLE action_runs
    ADD COLUMN run_kind VARCHAR(32) NOT NULL DEFAULT 'PROCESSING',
    ADD COLUMN dataset_id VARCHAR(255),
    ADD COLUMN dataset_label VARCHAR(255),
    ADD COLUMN input_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN split_counts_json TEXT,
    ADD COLUMN snapshot_cleaned_at TIMESTAMP(6) WITHOUT TIME ZONE;
ALTER TABLE action_runs ALTER COLUMN project_id DROP NOT NULL;
ALTER TABLE action_runs DROP CONSTRAINT IF EXISTS action_runs_lock_mode_check;
ALTER TABLE action_runs ADD CONSTRAINT action_runs_lock_mode_check
    CHECK (lock_mode IN ('PAGES', 'PROJECT', 'NONE'));
ALTER TABLE action_runs ADD CONSTRAINT action_runs_run_kind_check
    CHECK (run_kind IN ('PROCESSING', 'TRAINING'));
ALTER TABLE action_runs ADD CONSTRAINT action_runs_resource_scope_check CHECK (
    (run_kind = 'PROCESSING' AND project_id IS NOT NULL AND dataset_id IS NULL)
    OR (run_kind = 'TRAINING' AND project_id IS NULL AND dataset_id IS NOT NULL)
);

CREATE INDEX idx_action_runs_dataset_status ON action_runs(dataset_id, status);

CREATE TABLE action_training_inputs (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    run_id VARCHAR(255) NOT NULL,
    dataset_item_id VARCHAR(255) NOT NULL,
    source_page_id VARCHAR(255) NOT NULL,
    page_name VARCHAR(255) NOT NULL,
    split VARCHAR(16) NOT NULL,
    image_file_id VARCHAR(255) NOT NULL,
    image_file_name VARCHAR(255) NOT NULL,
    image_variant VARCHAR(255),
    image_mime_type VARCHAR(255) NOT NULL,
    image_file_size BIGINT NOT NULL,
    image_checksum_sha256 VARCHAR(64) NOT NULL,
    image_snapshot_path TEXT NOT NULL,
    xml_file_id VARCHAR(255) NOT NULL,
    xml_file_name VARCHAR(255) NOT NULL,
    xml_mime_type VARCHAR(255) NOT NULL,
    xml_file_size BIGINT NOT NULL,
    xml_checksum_sha256 VARCHAR(64) NOT NULL,
    xml_snapshot_path TEXT NOT NULL,
    created TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT action_training_inputs_split_check CHECK (split IN ('TRAIN', 'VAL', 'TEST')),
    CONSTRAINT fk_action_training_inputs_run FOREIGN KEY (run_id) REFERENCES action_runs(id) ON DELETE CASCADE,
    CONSTRAINT uk_action_training_inputs_run_item UNIQUE (run_id, dataset_item_id)
);

CREATE INDEX idx_action_training_inputs_run ON action_training_inputs(run_id);
