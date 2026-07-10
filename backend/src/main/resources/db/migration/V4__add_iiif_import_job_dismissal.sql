ALTER TABLE iiif_import_jobs
    ADD COLUMN dismissed BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_iiif_import_jobs_status_panel
    ON iiif_import_jobs (workspace_id, created_by_user_id, dismissed, created DESC);
