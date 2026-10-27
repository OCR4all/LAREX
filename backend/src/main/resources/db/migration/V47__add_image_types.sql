ALTER TABLE page_images ADD COLUMN image_type varchar(16)
    CHECK (image_type IN ('COLOR', 'GRAYSCALE', 'BINARY', 'UNKNOWN'));
ALTER TABLE dataset_item_copy_files ADD COLUMN image_type varchar(16)
    CHECK (image_type IN ('COLOR', 'GRAYSCALE', 'BINARY', 'UNKNOWN'));
ALTER TABLE action_training_inputs ADD COLUMN image_type varchar(16)
    CHECK (image_type IN ('COLOR', 'GRAYSCALE', 'BINARY', 'UNKNOWN'));
ALTER TABLE action_input_snapshots ADD COLUMN image_type varchar(16)
    CHECK (image_type IN ('COLOR', 'GRAYSCALE', 'BINARY', 'UNKNOWN'));

CREATE INDEX page_images_uninspected_type_idx ON page_images (id) WHERE image_type IS NULL;
CREATE INDEX dataset_copy_images_uninspected_type_idx ON dataset_item_copy_files (id) WHERE image_type IS NULL AND kind = 'IMAGE';
CREATE INDEX training_inputs_uninspected_type_idx ON action_training_inputs (id) WHERE image_type IS NULL;
CREATE INDEX input_snapshot_images_uninspected_type_idx ON action_input_snapshots (id) WHERE image_type IS NULL AND file_type = 'images';
