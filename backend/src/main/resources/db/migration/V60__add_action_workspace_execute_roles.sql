CREATE TABLE action_workspace_execute_roles (
    id character varying(255) NOT NULL PRIMARY KEY,
    processor_definition_id character varying(255) NOT NULL REFERENCES action_processor_definitions(id) ON DELETE CASCADE,
    workspace_id character varying(255) NOT NULL,
    execute_role character varying(32) NOT NULL,
    created_by_user_id character varying(255) NOT NULL,
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    CONSTRAINT uk_action_workspace_execute_role UNIQUE (processor_definition_id, workspace_id)
);

CREATE INDEX idx_action_workspace_execute_role_workspace ON action_workspace_execute_roles (workspace_id);
