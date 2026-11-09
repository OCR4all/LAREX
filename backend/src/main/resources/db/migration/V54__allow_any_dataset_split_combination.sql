ALTER TABLE datasets DROP CONSTRAINT datasets_split_template_check;

ALTER TABLE datasets
    ADD CONSTRAINT datasets_split_template_check
    CHECK (split_template IN ('TRAIN', 'VAL', 'TEST', 'TRAIN_VAL', 'TRAIN_TEST', 'VAL_TEST', 'TRAIN_VAL_TEST'));
