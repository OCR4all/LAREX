INSERT INTO task_page_links (
    id,
    created,
    created_by_user_id,
    link_type,
    page_id,
    tag_filter,
    task_id
)
SELECT
    md5('derived-task-page-link:' || grouped.task_id || ':' || grouped.page_id),
    CURRENT_TIMESTAMP,
    parent_task.created_by_user_id,
    'MANUAL',
    grouped.page_id,
    NULL,
    grouped.task_id
FROM (
    SELECT DISTINCT task_id, page_id
    FROM subtasks
    WHERE page_id IS NOT NULL
) grouped
JOIN tasks parent_task ON parent_task.id = grouped.task_id
WHERE NOT EXISTS (
    SELECT 1
    FROM task_page_links link
    WHERE link.task_id = grouped.task_id
      AND link.page_id = grouped.page_id
);

DELETE FROM task_page_links link
WHERE NOT EXISTS (
    SELECT 1
    FROM subtasks subtask
    WHERE subtask.task_id = link.task_id
      AND subtask.page_id = link.page_id
);
