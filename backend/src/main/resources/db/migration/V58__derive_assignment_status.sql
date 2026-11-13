ALTER TABLE tasks ADD COLUMN archived_at TIMESTAMP(6) WITHOUT TIME ZONE;
ALTER TABLE tasks ADD COLUMN archived_by_user_id VARCHAR(255);

ALTER TABLE tasks DROP CONSTRAINT tasks_status_check;
UPDATE tasks SET status = 'PAUSED' WHERE status = 'CANCELLED';
ALTER TABLE tasks ADD CONSTRAINT tasks_status_check
    CHECK (status IN ('OPEN', 'IN_PROGRESS', 'COMPLETED', 'PAUSED'));

WITH progress AS (
    SELECT t.id,
           COUNT(s.id) AS total,
           COUNT(s.id) FILTER (WHERE s.completed) AS completed
    FROM tasks t
    LEFT JOIN subtasks s ON s.task_id = t.id
    WHERE t.status <> 'PAUSED'
    GROUP BY t.id
), derived AS (
    SELECT id,
           CASE
               WHEN total > 0 AND completed = total THEN 'COMPLETED'
               WHEN completed > 0 THEN 'IN_PROGRESS'
               ELSE 'OPEN'
           END AS status
    FROM progress
)
UPDATE tasks t
SET status = d.status,
    completed_at = CASE WHEN d.status = 'COMPLETED' THEN COALESCE(t.completed_at, t.updated) END,
    completed_by_user_id = CASE WHEN d.status = 'COMPLETED' THEN t.completed_by_user_id END
FROM derived d
WHERE t.id = d.id;

UPDATE task_activity_logs SET old_value = 'PAUSED' WHERE activity_type = 'STATUS_CHANGED' AND old_value = 'CANCELLED';
UPDATE task_activity_logs SET new_value = 'PAUSED' WHERE activity_type = 'STATUS_CHANGED' AND new_value = 'CANCELLED';

ALTER TABLE task_activity_logs DROP CONSTRAINT task_activity_logs_activity_type_check;
ALTER TABLE task_activity_logs ADD CONSTRAINT task_activity_logs_activity_type_check
    CHECK (activity_type IN (
        'CREATED', 'TITLE_CHANGED', 'DESCRIPTION_CHANGED', 'STATUS_CHANGED', 'STATUS_DERIVED',
        'PRIORITY_CHANGED', 'DUE_DATE_CHANGED', 'ASSIGNEES_CHANGED', 'COMMENT_ADDED', 'COMMENT_UPDATED',
        'COMMENT_DELETED', 'SUBTASK_ADDED', 'SUBTASK_COMPLETED', 'SUBTASK_DELETED', 'LINK_ADDED',
        'LINK_REMOVED', 'ARCHIVED', 'UNARCHIVED'
    ));

CREATE INDEX idx_tasks_workspace_archived ON tasks (workspace_id, archived_at);
