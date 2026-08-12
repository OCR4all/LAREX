ALTER TABLE upload_sessions
    ADD COLUMN pdf_render_dpi integer,
    ADD COLUMN preflight_estimated_bytes bigint,
    ADD COLUMN preflight_completed_at timestamp(6);
