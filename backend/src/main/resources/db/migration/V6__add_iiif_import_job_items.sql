CREATE TABLE iiif_import_job_items (
    id VARCHAR(255) NOT NULL,
    job_id VARCHAR(255) NOT NULL,
    canvas_id TEXT,
    canvas_label TEXT,
    canvas_index INTEGER NOT NULL,
    requested_page_name VARCHAR(255),
    final_page_name VARCHAR(255),
    action VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    page_id VARCHAR(255),
    message TEXT,
    actual_bytes BIGINT,
    created TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT iiif_import_job_items_pkey PRIMARY KEY (id),
    CONSTRAINT uk_iiif_import_job_item_canvas UNIQUE (job_id, canvas_index),
    CONSTRAINT fk_iiif_import_job_item_job
        FOREIGN KEY (job_id) REFERENCES iiif_import_jobs (id) ON DELETE CASCADE
);

CREATE INDEX idx_iiif_import_job_items_job_status
    ON iiif_import_job_items (job_id, status);

INSERT INTO iiif_import_job_items (
    id,
    job_id,
    canvas_id,
    canvas_label,
    canvas_index,
    requested_page_name,
    final_page_name,
    action,
    status,
    page_id,
    message,
    actual_bytes,
    created
)
SELECT
    job.id || ':' || result.ordinality,
    job.id,
    result.value ->> 'canvasId',
    result.value ->> 'canvasLabel',
    COALESCE((result.value ->> 'index')::INTEGER, result.ordinality::INTEGER - 1),
    result.value ->> 'requestedPageName',
    result.value ->> 'finalPageName',
    COALESCE(result.value ->> 'action', 'IMPORT'),
    COALESCE(result.value ->> 'status', 'FAILED'),
    result.value ->> 'pageId',
    result.value ->> 'message',
    NULL,
    COALESCE(job.completed_at, job.updated, job.created)
FROM iiif_import_jobs job
CROSS JOIN LATERAL jsonb_array_elements(job.results_json::jsonb)
    WITH ORDINALITY AS result(value, ordinality)
WHERE job.results_json IS NOT NULL
  AND BTRIM(job.results_json) <> ''
  AND BTRIM(job.results_json) <> '[]';
