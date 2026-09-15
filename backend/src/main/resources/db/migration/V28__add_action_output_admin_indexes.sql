CREATE INDEX idx_action_outputs_status_created
    ON action_outputs(status, created DESC);

CREATE INDEX idx_action_outputs_status_size
    ON action_outputs(status, total_size_bytes DESC);
