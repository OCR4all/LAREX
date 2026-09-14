CREATE INDEX IF NOT EXISTS idx_task_assignees_user_task
    ON task_assignees (user_id, task_id);

CREATE INDEX IF NOT EXISTS idx_subtasks_open_assigned_page
    ON subtasks (assigned_user_id, task_id, page_id)
    WHERE completed = false AND page_id IS NOT NULL;
