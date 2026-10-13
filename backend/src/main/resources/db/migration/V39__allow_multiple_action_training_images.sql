ALTER TABLE action_training_inputs
    DROP CONSTRAINT uk_action_training_inputs_run_item;

ALTER TABLE action_training_inputs
    ADD CONSTRAINT uk_action_training_inputs_run_item_image UNIQUE (run_id, dataset_item_id, image_file_id);
