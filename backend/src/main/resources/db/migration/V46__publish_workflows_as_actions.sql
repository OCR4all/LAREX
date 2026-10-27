ALTER TABLE action_workflows ALTER COLUMN workspace_id DROP NOT NULL;
ALTER TABLE action_processor_definitions ADD COLUMN workflow_id VARCHAR(255) UNIQUE
    REFERENCES action_workflows(id);

-- Preserve existing workspace workflows and expose them through the Action catalogue.
INSERT INTO action_processor_definitions (
    id, processor_key, name, description, yaml_source, parsed_json, endpoint_url,
    endpoint_timeout_seconds, execute_role, lock_mode, target_types_json,
    accepts_images, accepts_xml, outputs_images, outputs_xml, outputs_files,
    enabled, global_available, created_by_user_id, updated_by_user_id, created, updated,
    action_kind, workflow_id
)
SELECT w.id, 'workflow-' || w.id, w.name, w.description, '', '{"tags":[],"targets":["PAGE"]}',
    'urn:larex:workflow', 30, 'EDITOR', 'NONE', '["PAGE"]',
    FALSE, FALSE, FALSE, FALSE, FALSE, w.enabled, FALSE,
    'workflow-migration', 'workflow-migration', w.created, w.updated, 'PROCESSING', w.id
FROM action_workflows w;

INSERT INTO action_processor_workspace_availability (
    id, processor_definition_id, workspace_id, enabled, created_by_user_id, created, updated
)
SELECT w.id, w.id, w.workspace_id, TRUE, 'workflow-migration', w.created, w.updated
FROM action_workflows w WHERE w.workspace_id IS NOT NULL;

INSERT INTO action_processor_assignments (
    id, processor_definition_id, workspace_id, project_id, enabled, created_by_user_id, created, updated
)
SELECT w.id, w.id, w.workspace_id, NULL, TRUE, 'workflow-migration', w.created, w.updated
FROM action_workflows w WHERE w.workspace_id IS NOT NULL;
