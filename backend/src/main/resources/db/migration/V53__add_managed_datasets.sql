-- Managed datasets back training and evaluation runs started from projects. They
-- stay out of dataset listings, and their key identifies the set of source projects.
ALTER TABLE datasets
    ADD COLUMN managed_key VARCHAR(64),
    ADD COLUMN managed_label VARCHAR(255);

CREATE UNIQUE INDEX uk_datasets_workspace_managed_key
    ON datasets (workspace_id, managed_key)
    WHERE managed_key IS NOT NULL;
