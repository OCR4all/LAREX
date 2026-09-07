ALTER TABLE action_runs ADD COLUMN IF NOT EXISTS dataset_input_fingerprint VARCHAR(64);

ALTER TABLE action_processor_definitions DROP CONSTRAINT IF EXISTS action_processor_definitions_action_kind_check;
ALTER TABLE action_processor_definitions ADD CONSTRAINT action_processor_definitions_action_kind_check
    CHECK (action_kind IN ('PROCESSING', 'TRAINING', 'EVALUATION'));
ALTER TABLE action_runs DROP CONSTRAINT IF EXISTS action_runs_run_kind_check;
ALTER TABLE action_runs ADD CONSTRAINT action_runs_run_kind_check
    CHECK (run_kind IN ('PROCESSING', 'TRAINING', 'EVALUATION'));
ALTER TABLE action_runs DROP CONSTRAINT IF EXISTS action_runs_resource_scope_check;
ALTER TABLE action_runs ADD CONSTRAINT action_runs_resource_scope_check CHECK (
    (run_kind = 'PROCESSING' AND project_id IS NOT NULL AND dataset_id IS NULL)
    OR (run_kind IN ('TRAINING', 'EVALUATION') AND project_id IS NULL AND dataset_id IS NOT NULL)
);

-- Dataset Action snapshots are shared by training and evaluation. Keep the
-- historical table name for compatibility with existing training records;
-- the run kind determines which dataset Action owns each row.
