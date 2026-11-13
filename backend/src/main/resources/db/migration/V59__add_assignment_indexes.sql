CREATE INDEX IF NOT EXISTS idx_subtasks_task_sort ON subtasks (task_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_subtasks_page ON subtasks (page_id) WHERE page_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_task_activity_logs_task_created ON task_activity_logs (task_id, created);
CREATE INDEX IF NOT EXISTS idx_task_comments_task ON task_comments (task_id);
CREATE INDEX IF NOT EXISTS idx_task_page_links_page ON task_page_links (page_id);
CREATE INDEX IF NOT EXISTS idx_task_reminders_task_user ON task_reminders (task_id, user_id);
CREATE INDEX IF NOT EXISTS idx_task_reminders_pending ON task_reminders (reminder_time) WHERE sent = false;
