ALTER TABLE action_runs ADD COLUMN cancel_requested_at timestamp;
UPDATE action_runs SET cancel_requested_at = updated WHERE status = 'CANCEL_REQUESTED';
