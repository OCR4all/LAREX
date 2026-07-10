ALTER TABLE iiif_import_jobs
    ADD COLUMN lease_owner VARCHAR(255),
    ADD COLUMN lease_expires_at TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN last_heartbeat_at TIMESTAMP WITHOUT TIME ZONE;

CREATE INDEX idx_iiif_import_jobs_expired_lease
    ON iiif_import_jobs (status, lease_expires_at);
