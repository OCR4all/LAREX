CREATE TABLE iiif_runtime_settings
(
    id                           SMALLINT PRIMARY KEY CHECK (id = 1),
    download_min_interval_ms     INTEGER CHECK (
        download_min_interval_ms IS NULL
        OR download_min_interval_ms BETWEEN 0 AND 60000
    ),
    updated_at                   TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_by_user_id           VARCHAR(255)
);
