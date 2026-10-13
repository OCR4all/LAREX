ALTER TABLE action_runs ADD COLUMN run_report_json TEXT;
ALTER TABLE action_run_page_results ADD COLUMN outcome_json TEXT;
ALTER TABLE action_run_page_results ADD COLUMN validation_rejected BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE action_runs DROP CONSTRAINT action_runs_status_check;
ALTER TABLE action_runs ADD CONSTRAINT action_runs_status_check CHECK (status IN (
    'QUEUED', 'PENDING', 'DISPATCHING', 'RUNNING', 'IMPORTING_RESULTS',
    'COMPLETED', 'COMPLETED_WITH_ISSUES', 'FAILED', 'CANCEL_REQUESTED', 'CANCELLED'
));
