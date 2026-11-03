-- Evaluation Actions may declare image inputs as optional or none, so frozen
-- dataset inputs can consist of PAGE XML only.
ALTER TABLE action_training_inputs
    ALTER COLUMN image_file_id DROP NOT NULL,
    ALTER COLUMN image_file_name DROP NOT NULL,
    ALTER COLUMN image_mime_type DROP NOT NULL,
    ALTER COLUMN image_file_size DROP NOT NULL,
    ALTER COLUMN image_checksum_sha256 DROP NOT NULL,
    ALTER COLUMN image_snapshot_path DROP NOT NULL;
