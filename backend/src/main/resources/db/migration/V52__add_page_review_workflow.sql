UPDATE pages SET workflow_state = 'OPEN' WHERE workflow_state = 'IN_PROGRESS';

ALTER TABLE pages DROP CONSTRAINT pages_workflow_state_check;

ALTER TABLE pages
    ADD CONSTRAINT pages_workflow_state_check
        CHECK (workflow_state IN ('OPEN', 'IN_REVIEW', 'DONE'));

ALTER TABLE pages
    ADD COLUMN review_submitted_by_user_id VARCHAR(255),
    ADD COLUMN review_submitted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    ADD COLUMN changes_requested BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE page_workflow_events (
    id VARCHAR(255) PRIMARY KEY,
    page_id VARCHAR(255) NOT NULL REFERENCES pages (id) ON DELETE CASCADE,
    action VARCHAR(32) NOT NULL,
    from_state VARCHAR(32) NOT NULL,
    to_state VARCHAR(32) NOT NULL,
    actor_user_id VARCHAR(255),
    comment TEXT,
    created TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT page_workflow_events_action_check
        CHECK (action IN ('SUBMIT', 'APPROVE', 'REQUEST_CHANGES', 'WITHDRAW', 'MARK_DONE', 'REOPEN', 'ASSIGNMENT_SYNC')),
    CONSTRAINT page_workflow_events_from_state_check
        CHECK (from_state IN ('OPEN', 'IN_REVIEW', 'DONE')),
    CONSTRAINT page_workflow_events_to_state_check
        CHECK (to_state IN ('OPEN', 'IN_REVIEW', 'DONE'))
);

CREATE INDEX idx_page_workflow_events_page_created
    ON page_workflow_events (page_id, created DESC);

ALTER TABLE tasks ADD COLUMN page_completion VARCHAR(32) NOT NULL DEFAULT 'NONE';

UPDATE tasks SET page_completion = 'DONE' WHERE sync_linked_page_states;

ALTER TABLE tasks
    ADD CONSTRAINT tasks_page_completion_check
        CHECK (page_completion IN ('NONE', 'DONE', 'REVIEW'));

ALTER TABLE tasks DROP COLUMN sync_linked_page_states;

ALTER TABLE projects ADD COLUMN review_required BOOLEAN;

ALTER TABLE team_workspaces ADD COLUMN default_review_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE personal_workspaces ADD COLUMN default_review_required BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;
ALTER TABLE notifications
    ADD CONSTRAINT notifications_type_check CHECK (type IN (
        'WORKSPACE_INVITATION', 'TASK_ASSIGNED', 'TASK_COMPLETED', 'TASK_REMINDER', 'TASK_MENTIONED',
        'TASK_UPDATED', 'TASK_DUE_SOON', 'TASK_OVERDUE', 'TASK_COMMENT_ADDED', 'PROJECT_CREATED',
        'PROJECT_DELETED', 'PAGE_CREATED', 'PAGE_DELETED', 'WORKSPACE_WATCH', 'PROJECT_WATCH',
        'UPLOAD_COMPLETED', 'UPLOAD_FAILED', 'IMPORT_COMPLETED', 'IMPORT_FAILED',
        'COLLAB_TAKEOVER_REQUESTED', 'COLLAB_TAKEOVER_GRANTED', 'COLLAB_TAKEOVER_DECLINED',
        'COLLAB_TAKEOVER_FORCED', 'COLLAB_LEASE_EXPIRED', 'PAGE_REVIEW_REQUESTED', 'PAGE_CHANGES_REQUESTED'
    ));

ALTER TABLE notification_preferences DROP CONSTRAINT notification_preferences_notification_type_check;
ALTER TABLE notification_preferences
    ADD CONSTRAINT notification_preferences_notification_type_check CHECK (notification_type IN (
        'WORKSPACE_INVITATION', 'TASK_ASSIGNED', 'TASK_COMPLETED', 'TASK_REMINDER', 'TASK_MENTIONED',
        'TASK_UPDATED', 'TASK_DUE_SOON', 'TASK_OVERDUE', 'TASK_COMMENT_ADDED', 'PROJECT_CREATED',
        'PROJECT_DELETED', 'PAGE_CREATED', 'PAGE_DELETED', 'WORKSPACE_WATCH', 'PROJECT_WATCH',
        'UPLOAD_COMPLETED', 'UPLOAD_FAILED', 'IMPORT_COMPLETED', 'IMPORT_FAILED',
        'COLLAB_TAKEOVER_REQUESTED', 'COLLAB_TAKEOVER_GRANTED', 'COLLAB_TAKEOVER_DECLINED',
        'COLLAB_TAKEOVER_FORCED', 'COLLAB_LEASE_EXPIRED', 'PAGE_REVIEW_REQUESTED', 'PAGE_CHANGES_REQUESTED'
    ));
