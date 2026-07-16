ALTER TABLE pages
    ADD COLUMN workflow_state VARCHAR(32) NOT NULL DEFAULT 'OPEN';

ALTER TABLE pages
    ADD CONSTRAINT pages_workflow_state_check
        CHECK (workflow_state IN ('OPEN', 'IN_PROGRESS', 'DONE'));

CREATE INDEX idx_pages_project_workflow_state
    ON pages (project_id, workflow_state);

ALTER TABLE tasks
    ADD COLUMN sync_linked_page_states BOOLEAN NOT NULL DEFAULT FALSE;
