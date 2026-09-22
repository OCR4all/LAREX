CREATE TABLE project_activity_metrics (
    id character varying(255) NOT NULL,
    project_id character varying(255) NOT NULL,
    bucket_start date NOT NULL,
    event_count bigint NOT NULL DEFAULT 0,
    last_activity_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT project_activity_metrics_pkey PRIMARY KEY (id),
    CONSTRAINT uk_project_activity_bucket UNIQUE (project_id, bucket_start),
    CONSTRAINT fk_project_activity_project FOREIGN KEY (project_id)
        REFERENCES projects (id) ON DELETE CASCADE
);

CREATE INDEX idx_project_activity_metrics_project_bucket
    ON project_activity_metrics (project_id, bucket_start);
