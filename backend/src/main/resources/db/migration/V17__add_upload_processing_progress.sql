ALTER TABLE upload_sessions
    ADD COLUMN processing_completed_items integer NOT NULL DEFAULT 0,
    ADD COLUMN processing_total_items integer NOT NULL DEFAULT 0,
    ADD COLUMN processing_current_file_name text;
