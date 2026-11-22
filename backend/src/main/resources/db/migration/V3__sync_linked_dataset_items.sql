UPDATE dataset_items di
SET source_project_name = p.name
FROM projects p
WHERE di.mode = 'LINK'
  AND p.id = di.source_project_id
  AND di.source_project_name <> p.name;

UPDATE dataset_items di
SET source_page_name = pg.name
FROM pages pg
WHERE di.mode = 'LINK'
  AND pg.id = di.source_page_id
  AND di.source_page_name <> pg.name;

UPDATE dataset_items di
SET selected_source_xml_id = px.id,
    selected_source_xml_file_name = px.file_name,
    selected_source_xml_updated_at = px.updated,
    status = CASE WHEN di.broken_reason = 'Selected source annotation is missing.' THEN 'READY' ELSE di.status END,
    broken_reason = CASE WHEN di.broken_reason = 'Selected source annotation is missing.' THEN NULL ELSE di.broken_reason END
FROM page_xmls px
WHERE di.mode = 'LINK'
  AND px.page_id = di.source_page_id
  AND di.selected_source_xml_id <> px.id;

CREATE TEMP TABLE live_workspaces ON COMMIT DROP AS
SELECT id FROM team_workspaces
UNION
SELECT id FROM personal_workspaces;

CREATE TEMP TABLE gone_libraries ON COMMIT DROP AS
SELECT id FROM libraries WHERE workspace_id NOT IN (SELECT id FROM live_workspaces);

CREATE TEMP TABLE gone_projects ON COMMIT DROP AS
SELECT id FROM projects WHERE library_id IN (SELECT id FROM gone_libraries);

CREATE TEMP TABLE gone_pages ON COMMIT DROP AS
SELECT id FROM pages WHERE project_id IN (SELECT id FROM gone_projects);

CREATE TEMP TABLE gone_tasks ON COMMIT DROP AS
SELECT id FROM tasks WHERE workspace_id NOT IN (SELECT id FROM live_workspaces);

CREATE TEMP TABLE gone_runs ON COMMIT DROP AS
SELECT id FROM action_runs WHERE workspace_id NOT IN (SELECT id FROM live_workspaces);

CREATE TEMP TABLE gone_datasets ON COMMIT DROP AS
SELECT id FROM datasets WHERE workspace_id NOT IN (SELECT id FROM live_workspaces);

CREATE TEMP TABLE gone_workflow_definitions ON COMMIT DROP AS
SELECT id FROM action_processor_definitions
WHERE workflow_id IN (SELECT id FROM action_workflows WHERE workspace_id NOT IN (SELECT id FROM live_workspaces));

DELETE FROM task_comment_mentions WHERE comment_id IN (SELECT id FROM task_comments WHERE task_id IN (SELECT id FROM gone_tasks));
DELETE FROM task_comments WHERE task_id IN (SELECT id FROM gone_tasks);
DELETE FROM task_assignees WHERE task_id IN (SELECT id FROM gone_tasks);
DELETE FROM task_activity_logs WHERE task_id IN (SELECT id FROM gone_tasks);
DELETE FROM task_reminders WHERE task_id IN (SELECT id FROM gone_tasks);
DELETE FROM subtasks WHERE task_id IN (SELECT id FROM gone_tasks);
DELETE FROM task_page_links WHERE task_id IN (SELECT id FROM gone_tasks);
DELETE FROM task_project_links WHERE task_id IN (SELECT id FROM gone_tasks);
DELETE FROM tasks WHERE id IN (SELECT id FROM gone_tasks);

DELETE FROM action_run_dismissals WHERE run_id IN (SELECT id FROM gone_runs);
DELETE FROM action_run_log_events WHERE run_id IN (SELECT id FROM gone_runs);
DELETE FROM action_runs WHERE id IN (SELECT id FROM gone_runs);
DELETE FROM action_workflow_runs WHERE workspace_id NOT IN (SELECT id FROM live_workspaces);
DELETE FROM action_outputs
WHERE workspace_id NOT IN (SELECT id FROM live_workspaces)
   OR project_id IN (SELECT id FROM gone_projects)
   OR dataset_id IN (SELECT id FROM gone_datasets);
DELETE FROM action_processor_assignments
WHERE workspace_id NOT IN (SELECT id FROM live_workspaces)
   OR processor_definition_id IN (SELECT id FROM gone_workflow_definitions);
DELETE FROM action_processor_workspace_availability
WHERE workspace_id NOT IN (SELECT id FROM live_workspaces)
   OR processor_definition_id IN (SELECT id FROM gone_workflow_definitions);
DELETE FROM action_workspace_execute_roles
WHERE workspace_id NOT IN (SELECT id FROM live_workspaces)
   OR processor_definition_id IN (SELECT id FROM gone_workflow_definitions);
DELETE FROM action_processor_definitions WHERE id IN (SELECT id FROM gone_workflow_definitions);
DELETE FROM action_workflows WHERE workspace_id NOT IN (SELECT id FROM live_workspaces);

DELETE FROM dataset_item_source_images
WHERE dataset_item_id IN (SELECT id FROM dataset_items WHERE dataset_id IN (SELECT id FROM gone_datasets));
DELETE FROM dataset_item_tags
WHERE dataset_item_id IN (SELECT id FROM dataset_items WHERE dataset_id IN (SELECT id FROM gone_datasets));
DELETE FROM dataset_stratify_tags WHERE dataset_id IN (SELECT id FROM gone_datasets);
DELETE FROM dataset_tags WHERE dataset_id IN (SELECT id FROM gone_datasets);
DELETE FROM datasets WHERE id IN (SELECT id FROM gone_datasets);

DELETE FROM upload_session_files
WHERE session_id IN (SELECT id FROM upload_sessions WHERE workspace_id NOT IN (SELECT id FROM live_workspaces));
DELETE FROM upload_sessions WHERE workspace_id NOT IN (SELECT id FROM live_workspaces);
DELETE FROM import_jobs WHERE workspace_id NOT IN (SELECT id FROM live_workspaces);
DELETE FROM iiif_import_jobs WHERE workspace_id NOT IN (SELECT id FROM live_workspaces);
DELETE FROM project_export_jobs WHERE workspace_id NOT IN (SELECT id FROM live_workspaces);
DELETE FROM project_transfer_requests
WHERE source_workspace_id NOT IN (SELECT id FROM live_workspaces)
   OR target_workspace_id NOT IN (SELECT id FROM live_workspaces)
   OR project_id IN (SELECT id FROM gone_projects);
DELETE FROM resource_transfer_requests
WHERE source_workspace_id NOT IN (SELECT id FROM live_workspaces)
   OR target_workspace_id NOT IN (SELECT id FROM live_workspaces);

DELETE FROM dataset_item_source_images
WHERE dataset_item_id IN (SELECT id FROM dataset_items WHERE mode = 'LINK'
    AND (source_page_id IN (SELECT id FROM gone_pages) OR source_page_id NOT IN (SELECT id FROM pages)));
DELETE FROM dataset_item_tags
WHERE dataset_item_id IN (SELECT id FROM dataset_items WHERE mode = 'LINK'
    AND (source_page_id IN (SELECT id FROM gone_pages) OR source_page_id NOT IN (SELECT id FROM pages)));
DELETE FROM dataset_items
WHERE mode = 'LINK'
  AND (source_page_id IN (SELECT id FROM gone_pages) OR source_page_id NOT IN (SELECT id FROM pages));

DELETE FROM page_tags WHERE page_id IN (SELECT id FROM gone_pages);
DELETE FROM pages WHERE id IN (SELECT id FROM gone_pages);
DELETE FROM project_tags WHERE project_id IN (SELECT id FROM gone_projects);
DELETE FROM projects WHERE id IN (SELECT id FROM gone_projects);
DELETE FROM stored_files WHERE workspace_id NOT IN (SELECT id FROM live_workspaces);

DELETE FROM workspace_members WHERE workspace_id NOT IN (SELECT id FROM live_workspaces);
DELETE FROM workspace_storage_quotas WHERE workspace_id NOT IN (SELECT id FROM live_workspaces);

DELETE FROM codec_characters WHERE codec_id IN (SELECT id FROM codecs WHERE library_id IN (SELECT id FROM gone_libraries));
DELETE FROM codec_tags WHERE codec_id IN (SELECT id FROM codecs WHERE library_id IN (SELECT id FROM gone_libraries));
DELETE FROM codecs WHERE library_id IN (SELECT id FROM gone_libraries);
DELETE FROM controlled_dictionary_entries
WHERE dictionary_id IN (SELECT id FROM controlled_dictionaries WHERE library_id IN (SELECT id FROM gone_libraries));
DELETE FROM controlled_dictionary_tags
WHERE dictionary_id IN (SELECT id FROM controlled_dictionaries WHERE library_id IN (SELECT id FROM gone_libraries));
DELETE FROM controlled_dictionaries WHERE library_id IN (SELECT id FROM gone_libraries);
DELETE FROM label_set_tags
WHERE label_set_id IN (SELECT id FROM label_sets WHERE workspace_id NOT IN (SELECT id FROM live_workspaces));
DELETE FROM label_sets WHERE workspace_id NOT IN (SELECT id FROM live_workspaces);
DELETE FROM tag_set_meta_tags
WHERE tag_set_id IN (SELECT id FROM tag_sets WHERE workspace_id NOT IN (SELECT id FROM live_workspaces));
DELETE FROM tag_sets WHERE workspace_id NOT IN (SELECT id FROM live_workspaces);
DELETE FROM normalization_profile_replacement_rules
WHERE normalization_profile_id IN (SELECT id FROM normalization_profiles WHERE workspace_id NOT IN (SELECT id FROM live_workspaces));
DELETE FROM normalization_profile_tags
WHERE normalization_profile_id IN (SELECT id FROM normalization_profiles WHERE workspace_id NOT IN (SELECT id FROM live_workspaces));
DELETE FROM normalization_profiles WHERE workspace_id NOT IN (SELECT id FROM live_workspaces);
DELETE FROM validation_ruleset_tags
WHERE validation_ruleset_id IN (SELECT id FROM validation_rulesets WHERE workspace_id NOT IN (SELECT id FROM live_workspaces));
DELETE FROM validation_rulesets WHERE workspace_id NOT IN (SELECT id FROM live_workspaces);
DELETE FROM keyboard_items
WHERE virtual_keyboard_id IN (SELECT id FROM virtual_keyboards WHERE workspace_id NOT IN (SELECT id FROM live_workspaces));
DELETE FROM virtual_keyboard_tags
WHERE virtual_keyboard_id IN (SELECT id FROM virtual_keyboards WHERE workspace_id NOT IN (SELECT id FROM live_workspaces));
DELETE FROM virtual_keyboards WHERE workspace_id NOT IN (SELECT id FROM live_workspaces);
DELETE FROM libraries WHERE id IN (SELECT id FROM gone_libraries);

DELETE FROM subtasks s
WHERE s.page_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM pages pg
      JOIN projects p ON p.id = pg.project_id
      JOIN libraries l ON l.id = p.library_id
      JOIN tasks t ON t.id = s.task_id
      WHERE pg.id = s.page_id
        AND l.workspace_id = t.workspace_id
  );

DELETE FROM task_page_links tpl
WHERE NOT EXISTS (
    SELECT 1
    FROM pages pg
    JOIN projects p ON p.id = pg.project_id
    JOIN libraries l ON l.id = p.library_id
    JOIN tasks t ON t.id = tpl.task_id
    WHERE pg.id = tpl.page_id
      AND l.workspace_id = t.workspace_id
);

DELETE FROM task_project_links tpl
WHERE NOT EXISTS (
    SELECT 1
    FROM projects p
    JOIN libraries l ON l.id = p.library_id
    JOIN tasks t ON t.id = tpl.task_id
    WHERE p.id = tpl.project_id
      AND l.workspace_id = t.workspace_id
);

DELETE FROM search_lexicon_entries;
