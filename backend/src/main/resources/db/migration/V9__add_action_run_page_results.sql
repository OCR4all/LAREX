CREATE TABLE action_run_page_results (
    id VARCHAR(255) NOT NULL,
    run_id VARCHAR(255) NOT NULL,
    page_id VARCHAR(255) NOT NULL,
    result_summary_json TEXT,
    created TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT action_run_page_results_pkey PRIMARY KEY (id),
    CONSTRAINT uk_action_run_page_results_run_page UNIQUE (run_id, page_id),
    CONSTRAINT fk_action_run_page_results_run
        FOREIGN KEY (run_id) REFERENCES action_runs(id) ON DELETE CASCADE
);

CREATE INDEX idx_action_run_page_results_run ON action_run_page_results(run_id);
