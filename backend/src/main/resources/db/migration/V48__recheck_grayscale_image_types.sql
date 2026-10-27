-- Binarizers may store exact black/white pixels in 8/16-bit grayscale images.
-- Reinspect these cached classifications without changing content timestamps.
UPDATE page_images SET image_type = NULL WHERE image_type = 'GRAYSCALE';
UPDATE dataset_item_copy_files SET image_type = NULL WHERE kind = 'IMAGE' AND image_type = 'GRAYSCALE';

-- Retained history whose snapshot bytes have already been cleaned stays intact.
UPDATE action_training_inputs AS input SET image_type = NULL
FROM action_runs AS run
WHERE input.run_id = run.id AND input.image_type = 'GRAYSCALE' AND run.snapshot_cleaned_at IS NULL;
UPDATE action_input_snapshots AS input SET image_type = NULL
FROM action_runs AS run
WHERE input.run_id = run.id AND input.file_type = 'images'
  AND input.image_type = 'GRAYSCALE' AND run.snapshot_cleaned_at IS NULL;
