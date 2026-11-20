CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;

CREATE SEQUENCE annotation_lease_epochs
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

CREATE TABLE action_audit_events (
    created timestamp(6) without time zone NOT NULL,
    outcome character varying(32) NOT NULL,
    action character varying(64) NOT NULL,
    actor_user_id character varying(255),
    details_json text,
    id character varying(255) NOT NULL,
    processor_definition_id character varying(255),
    project_id character varying(255),
    run_id character varying(255),
    workspace_id character varying(255),
    CONSTRAINT action_audit_events_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_action_audit_events_created ON action_audit_events (created);
CREATE INDEX idx_action_audit_events_definition ON action_audit_events (processor_definition_id);
CREATE INDEX idx_action_audit_events_workspace_project ON action_audit_events (workspace_id, project_id);

CREATE TABLE action_endpoint_secrets (
    created timestamp(6) without time zone NOT NULL,
    last_used_at timestamp(6) without time zone,
    rotated_at timestamp(6) without time zone,
    updated timestamp(6) without time zone NOT NULL,
    ref character varying(128) NOT NULL,
    created_by_user_id character varying(255) NOT NULL,
    display_name character varying(255),
    description text,
    encrypted_secret text NOT NULL,
    id character varying(255) NOT NULL,
    CONSTRAINT action_endpoint_secrets_pkey PRIMARY KEY (id),
    CONSTRAINT action_endpoint_secrets_ref_key UNIQUE (ref)
);
CREATE UNIQUE INDEX idx_action_endpoint_secret_ref ON action_endpoint_secrets (ref);

CREATE TABLE action_input_snapshots (
    id character varying(255) NOT NULL,
    run_id character varying(255) NOT NULL,
    input_id character varying(255) NOT NULL,
    page_name character varying(255) NOT NULL,
    source_page_id character varying(255) NOT NULL,
    split character varying(255),
    file_type character varying(255) NOT NULL,
    source_file_id character varying(255) NOT NULL,
    file_name character varying(255) NOT NULL,
    variant character varying(255),
    mime_type character varying(255) NOT NULL,
    file_size bigint NOT NULL,
    checksum_sha256 character varying(255) NOT NULL,
    snapshot_path text NOT NULL,
    "position" integer NOT NULL,
    image_type character varying(16),
    CONSTRAINT action_input_snapshots_file_type_check CHECK ((file_type IN ('images', 'xml'))),
    CONSTRAINT action_input_snapshots_image_type_check CHECK ((image_type IN ('COLOR', 'GRAYSCALE', 'BINARY', 'UNKNOWN'))),
    CONSTRAINT action_input_snapshots_pkey PRIMARY KEY (id),
    CONSTRAINT action_input_snapshots_run_id_position_key UNIQUE (run_id, "position")
);
CREATE INDEX idx_action_input_snapshots_run ON action_input_snapshots (run_id);
CREATE INDEX input_snapshot_images_uninspected_type_idx ON action_input_snapshots (id) WHERE ((image_type IS NULL) AND ((file_type)::text = 'images'::text));

CREATE TABLE action_output_files (
    id character varying(255) NOT NULL,
    output_id character varying(255) NOT NULL,
    stored_file_uuid character varying(32) NOT NULL,
    page_id character varying(255),
    file_name character varying(512) NOT NULL,
    mime_type character varying(128) NOT NULL,
    size_bytes bigint NOT NULL,
    checksum_sha256 character varying(64) NOT NULL,
    created timestamp(6) without time zone NOT NULL,
    CONSTRAINT action_output_files_pkey PRIMARY KEY (id),
    CONSTRAINT uk_action_output_files_stored_file UNIQUE (stored_file_uuid)
);
CREATE INDEX idx_action_output_files_output ON action_output_files (output_id);

CREATE TABLE action_outputs (
    id character varying(255) NOT NULL,
    project_id character varying(255),
    workspace_id character varying(255) NOT NULL,
    source_run_id character varying(255) NOT NULL,
    processor_definition_id character varying(255) NOT NULL,
    processor_key character varying(128) NOT NULL,
    processor_name character varying(255) NOT NULL,
    created_by_user_id character varying(255) NOT NULL,
    status character varying(32) NOT NULL,
    retention_days integer,
    expires_at timestamp(6) without time zone,
    completed_at timestamp(6) without time zone,
    total_size_bytes bigint DEFAULT 0 NOT NULL,
    file_count integer DEFAULT 0 NOT NULL,
    share_public_id character varying(64),
    share_secret_hash character varying(64),
    share_secret_prefix character varying(16),
    share_created_by_user_id character varying(255),
    share_created_at timestamp(6) without time zone,
    share_expires_at timestamp(6) without time zone,
    share_revoked_at timestamp(6) without time zone,
    share_last_used_at timestamp(6) without time zone,
    share_download_count bigint DEFAULT 0 NOT NULL,
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    dataset_id character varying(255),
    CONSTRAINT action_outputs_owner_check CHECK ((((project_id IS NOT NULL) AND (dataset_id IS NULL)) OR ((project_id IS NULL) AND (dataset_id IS NOT NULL)))),
    CONSTRAINT action_outputs_retention_days_check CHECK (((retention_days IS NULL) OR (retention_days > 0))),
    CONSTRAINT action_outputs_status_check CHECK ((status IN ('DRAFT', 'READY', 'DELETING'))),
    CONSTRAINT action_outputs_pkey PRIMARY KEY (id),
    CONSTRAINT uk_action_outputs_share_public_id UNIQUE (share_public_id),
    CONSTRAINT uk_action_outputs_source_run UNIQUE (source_run_id)
);
CREATE INDEX idx_action_outputs_dataset_status ON action_outputs (dataset_id, status);
CREATE INDEX idx_action_outputs_expiry ON action_outputs (status, expires_at);
CREATE INDEX idx_action_outputs_project_status_created ON action_outputs (project_id, status, created DESC);
CREATE INDEX idx_action_outputs_status_created ON action_outputs (status, created DESC);
CREATE INDEX idx_action_outputs_status_size ON action_outputs (status, total_size_bytes DESC);

CREATE TABLE action_processor_assignments (
    enabled boolean NOT NULL,
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    created_by_user_id character varying(255) NOT NULL,
    id character varying(255) NOT NULL,
    processor_definition_id character varying(255) NOT NULL,
    project_id character varying(255),
    workspace_id character varying(255) NOT NULL,
    CONSTRAINT action_processor_assignments_pkey PRIMARY KEY (id),
    CONSTRAINT uk_action_assignment_scope UNIQUE (processor_definition_id, workspace_id, project_id)
);
CREATE INDEX idx_action_assignment_project ON action_processor_assignments (project_id);
CREATE INDEX idx_action_assignment_workspace ON action_processor_assignments (workspace_id);

CREATE TABLE action_processor_definitions (
    accepts_images boolean NOT NULL,
    accepts_xml boolean NOT NULL,
    enabled boolean NOT NULL,
    endpoint_timeout_seconds integer NOT NULL,
    global_available boolean DEFAULT false NOT NULL,
    outputs_images boolean NOT NULL,
    outputs_xml boolean NOT NULL,
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    execute_role character varying(32) NOT NULL,
    lock_mode character varying(32) NOT NULL,
    processor_key character varying(128) NOT NULL,
    created_by_user_id character varying(255) NOT NULL,
    description text,
    endpoint_url text NOT NULL,
    id character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    parsed_json text NOT NULL,
    target_types_json text NOT NULL,
    updated_by_user_id character varying(255) NOT NULL,
    yaml_source text NOT NULL,
    outputs_files boolean DEFAULT false NOT NULL,
    action_kind character varying(32) DEFAULT 'PROCESSING'::character varying NOT NULL,
    workflow_id character varying(255),
    CONSTRAINT action_processor_definitions_action_kind_check CHECK ((action_kind IN ('PROCESSING', 'TRAINING', 'EVALUATION', 'INTEGRATION'))),
    CONSTRAINT action_processor_definitions_execute_role_check CHECK (((execute_role)::text = ANY (ARRAY[('EDITOR'::character varying)::text, ('CURATOR'::character varying)::text]))),
    CONSTRAINT action_processor_definitions_lock_mode_check CHECK ((lock_mode IN ('PAGES', 'PROJECT', 'NONE'))),
    CONSTRAINT action_processor_definitions_pkey PRIMARY KEY (id),
    CONSTRAINT action_processor_definitions_processor_key_key UNIQUE (processor_key),
    CONSTRAINT action_processor_definitions_workflow_id_key UNIQUE (workflow_id)
);

CREATE TABLE action_processor_workspace_availability (
    enabled boolean NOT NULL,
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    created_by_user_id character varying(255) CONSTRAINT action_processor_workspace_availabi_created_by_user_id_not_null NOT NULL,
    id character varying(255) NOT NULL,
    processor_definition_id character varying(255) CONSTRAINT action_processor_workspace_ava_processor_definition_id_not_null NOT NULL,
    workspace_id character varying(255) NOT NULL,
    CONSTRAINT action_processor_workspace_availability_pkey PRIMARY KEY (id),
    CONSTRAINT uk_action_workspace_availability UNIQUE (processor_definition_id, workspace_id)
);
CREATE INDEX idx_action_availability_definition ON action_processor_workspace_availability (processor_definition_id);
CREATE INDEX idx_action_availability_workspace ON action_processor_workspace_availability (workspace_id);

CREATE TABLE action_run_dismissals (
    created timestamp(6) without time zone NOT NULL,
    id character varying(255) NOT NULL,
    run_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    CONSTRAINT action_run_dismissals_pkey PRIMARY KEY (id),
    CONSTRAINT uk_action_run_dismissals_run_user UNIQUE (run_id, user_id)
);
CREATE INDEX idx_action_run_dismissals_run ON action_run_dismissals (run_id);
CREATE INDEX idx_action_run_dismissals_user ON action_run_dismissals (user_id);

CREATE TABLE action_run_log_events (
    created timestamp(6) without time zone NOT NULL,
    level character varying(16) NOT NULL,
    id character varying(255) NOT NULL,
    message text NOT NULL,
    run_id character varying(255) NOT NULL,
    CONSTRAINT action_run_log_events_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_action_run_log_events_run_created ON action_run_log_events (run_id, created);

CREATE TABLE action_run_page_results (
    id character varying(255) NOT NULL,
    run_id character varying(255) NOT NULL,
    page_id character varying(255) NOT NULL,
    result_summary_json text,
    created timestamp(6) without time zone NOT NULL,
    outcome_json text,
    validation_rejected boolean DEFAULT false NOT NULL,
    CONSTRAINT action_run_page_results_pkey PRIMARY KEY (id),
    CONSTRAINT uk_action_run_page_results_run_page UNIQUE (run_id, page_id)
);
CREATE INDEX idx_action_run_page_results_run ON action_run_page_results (run_id);

CREATE TABLE action_runs (
    cancel_requested boolean NOT NULL,
    progress_percent integer NOT NULL,
    completed_at timestamp(6) without time zone,
    created timestamp(6) without time zone NOT NULL,
    last_heartbeat_at timestamp(6) without time zone,
    secret_expires_at timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    secret_prefix character varying(16) NOT NULL,
    lock_mode character varying(32) NOT NULL,
    secret_hash character varying(64) NOT NULL,
    created_by_user_id character varying(255) NOT NULL,
    error_message text,
    id character varying(255) NOT NULL,
    log_text text,
    page_ids_json text NOT NULL,
    parameters_json text,
    processor_definition_id character varying(255) NOT NULL,
    project_id character varying(255),
    public_api_base_url text,
    result_summary_json text,
    status character varying(255) NOT NULL,
    status_message text,
    target_selection_json text,
    workspace_id character varying(255) NOT NULL,
    run_kind character varying(32) DEFAULT 'PROCESSING'::character varying NOT NULL,
    dataset_id character varying(255),
    dataset_label character varying(255),
    input_count integer DEFAULT 0 NOT NULL,
    split_counts_json text,
    snapshot_cleaned_at timestamp(6) without time zone,
    dataset_input_fingerprint character varying(64),
    cancel_requested_at timestamp without time zone,
    version bigint DEFAULT 0 NOT NULL,
    input_requirements_json text,
    run_report_json text,
    evaluation_output_mode character varying(16) DEFAULT 'REPORT'::character varying NOT NULL,
    workflow_run_id character varying(255),
    CONSTRAINT action_runs_evaluation_output_mode_check CHECK ((evaluation_output_mode IN ('REPORT', 'FILES'))),
    CONSTRAINT action_runs_lock_mode_check CHECK ((lock_mode IN ('PAGES', 'PROJECT', 'NONE'))),
    CONSTRAINT action_runs_resource_scope_check CHECK (((((run_kind)::text = 'PROCESSING'::text) AND (project_id IS NOT NULL) AND (dataset_id IS NULL)) OR ((run_kind IN ('TRAINING', 'EVALUATION')) AND (project_id IS NULL) AND (dataset_id IS NOT NULL)) OR (((run_kind)::text = 'INTEGRATION'::text) AND (((project_id IS NOT NULL) AND (dataset_id IS NULL)) OR ((project_id IS NULL) AND (dataset_id IS NOT NULL)))))),
    CONSTRAINT action_runs_run_kind_check CHECK ((run_kind IN ('PROCESSING', 'TRAINING', 'EVALUATION', 'INTEGRATION'))),
    CONSTRAINT action_runs_status_check CHECK ((status IN ('QUEUED', 'PENDING', 'DISPATCHING', 'RUNNING', 'IMPORTING_RESULTS', 'COMPLETED', 'COMPLETED_WITH_ISSUES', 'FAILED', 'CANCEL_REQUESTED', 'CANCELLED'))),
    CONSTRAINT action_runs_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_action_runs_dataset_status ON action_runs (dataset_id, status);
CREATE INDEX idx_action_runs_processor_created ON action_runs (processor_definition_id, created DESC);
CREATE INDEX idx_action_runs_project_status ON action_runs (project_id, status);
CREATE INDEX idx_action_runs_workflow_run ON action_runs (workflow_run_id);
CREATE INDEX idx_action_runs_workspace_created ON action_runs (workspace_id, created DESC);
CREATE INDEX idx_action_runs_workspace_project_created ON action_runs (workspace_id, project_id, created DESC);
CREATE INDEX idx_action_runs_workspace_status ON action_runs (workspace_id, status);

CREATE TABLE action_training_inputs (
    id character varying(255) NOT NULL,
    run_id character varying(255) NOT NULL,
    dataset_item_id character varying(255) NOT NULL,
    source_page_id character varying(255) NOT NULL,
    page_name character varying(255) NOT NULL,
    split character varying(16) NOT NULL,
    image_file_id character varying(255),
    image_file_name character varying(255),
    image_variant character varying(255),
    image_mime_type character varying(255),
    image_file_size bigint,
    image_checksum_sha256 character varying(64),
    image_snapshot_path text,
    xml_file_id character varying(255) NOT NULL,
    xml_file_name character varying(255) NOT NULL,
    xml_mime_type character varying(255) NOT NULL,
    xml_file_size bigint NOT NULL,
    xml_checksum_sha256 character varying(64) NOT NULL,
    xml_snapshot_path text NOT NULL,
    created timestamp(6) without time zone NOT NULL,
    image_type character varying(16),
    CONSTRAINT action_training_inputs_image_type_check CHECK ((image_type IN ('COLOR', 'GRAYSCALE', 'BINARY', 'UNKNOWN'))),
    CONSTRAINT action_training_inputs_split_check CHECK ((split IN ('TRAIN', 'VAL', 'TEST'))),
    CONSTRAINT action_training_inputs_pkey PRIMARY KEY (id),
    CONSTRAINT uk_action_training_inputs_run_item_image UNIQUE (run_id, dataset_item_id, image_file_id)
);
CREATE INDEX idx_action_training_inputs_run ON action_training_inputs (run_id);
CREATE INDEX training_inputs_uninspected_type_idx ON action_training_inputs (id) WHERE (image_type IS NULL);

CREATE TABLE action_workflow_run_dismissals (
    id character varying(255) NOT NULL,
    run_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    created timestamp without time zone NOT NULL,
    CONSTRAINT action_workflow_run_dismissals_pkey PRIMARY KEY (id),
    CONSTRAINT uk_action_workflow_run_dismissals_run_user UNIQUE (run_id, user_id)
);
CREATE INDEX idx_action_workflow_run_dismissals_run ON action_workflow_run_dismissals (run_id);
CREATE INDEX idx_action_workflow_run_dismissals_user ON action_workflow_run_dismissals (user_id);

CREATE TABLE action_workflow_runs (
    id character varying(255) NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    workspace_id character varying(255) NOT NULL,
    project_id character varying(255) NOT NULL,
    workflow_id character varying(255) NOT NULL,
    workflow_name character varying(120) NOT NULL,
    created_by_user_id character varying(255) NOT NULL,
    status character varying(32) NOT NULL,
    steps_json text NOT NULL,
    page_ids_json text NOT NULL,
    step_run_ids_json text DEFAULT '[]'::text NOT NULL,
    public_api_base_url text NOT NULL,
    error_message text,
    created timestamp without time zone NOT NULL,
    updated timestamp without time zone NOT NULL,
    completed_at timestamp without time zone,
    CONSTRAINT action_workflow_runs_status_check CHECK ((status IN ('RUNNING', 'CANCEL_REQUESTED', 'COMPLETED', 'FAILED', 'CANCELLED'))),
    CONSTRAINT action_workflow_runs_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_action_workflow_runs_active ON action_workflow_runs (status) WHERE (status IN ('RUNNING', 'CANCEL_REQUESTED'));
CREATE INDEX idx_action_workflow_runs_project ON action_workflow_runs (workspace_id, project_id, created DESC);

CREATE TABLE action_workflows (
    id character varying(255) NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    workspace_id character varying(255),
    name character varying(120) NOT NULL,
    description text,
    steps_json text NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    created timestamp without time zone NOT NULL,
    updated timestamp without time zone NOT NULL,
    CONSTRAINT action_workflows_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_action_workflows_workspace ON action_workflows (workspace_id);

CREATE TABLE action_workspace_execute_roles (
    id character varying(255) NOT NULL,
    processor_definition_id character varying(255) NOT NULL,
    workspace_id character varying(255) NOT NULL,
    execute_role character varying(32) NOT NULL,
    created_by_user_id character varying(255) NOT NULL,
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    CONSTRAINT action_workspace_execute_roles_pkey PRIMARY KEY (id),
    CONSTRAINT uk_action_workspace_execute_role UNIQUE (processor_definition_id, workspace_id)
);
CREATE INDEX idx_action_workspace_execute_role_workspace ON action_workspace_execute_roles (workspace_id);

CREATE TABLE admin_user_audit_logs (
    created timestamp(6) without time zone NOT NULL,
    action character varying(255) NOT NULL,
    actor_user_id character varying(255) NOT NULL,
    actor_username character varying(255) NOT NULL,
    details text,
    id character varying(255) NOT NULL,
    outcome character varying(255) NOT NULL,
    target_user_id character varying(255),
    target_username character varying(255),
    CONSTRAINT admin_user_audit_logs_action_check CHECK (((action)::text = ANY (ARRAY[('CREATE'::character varying)::text, ('ENABLE'::character varying)::text, ('DISABLE'::character varying)::text, ('RESEND_SETUP_EMAIL'::character varying)::text, ('GLOBAL_CURATOR_GRANT'::character varying)::text, ('GLOBAL_CURATOR_REVOKE'::character varying)::text]))),
    CONSTRAINT admin_user_audit_logs_outcome_check CHECK (((outcome)::text = ANY (ARRAY[('SUCCESS'::character varying)::text, ('FAILURE'::character varying)::text]))),
    CONSTRAINT admin_user_audit_logs_pkey PRIMARY KEY (id)
);

CREATE TABLE avatar_runtime_settings (
    id smallint NOT NULL,
    default_style character varying(32) NOT NULL,
    updated_at timestamp without time zone,
    updated_by_user_id character varying(255),
    CONSTRAINT avatar_runtime_settings_default_style_check CHECK ((default_style IN ('GRADIENT', 'IDENTICON', 'FLOW_FIELD', 'INITIALS'))),
    CONSTRAINT avatar_runtime_settings_id_check CHECK ((id = 1)),
    CONSTRAINT avatar_runtime_settings_pkey PRIMARY KEY (id)
);

CREATE TABLE codec_characters (
    character_value text,
    codec_id character varying(255) NOT NULL
);

CREATE TABLE codec_tags (
    codec_id character varying(255) NOT NULL,
    tag_value text
);

CREATE TABLE codecs (
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    description text,
    id character varying(255) NOT NULL,
    library_id character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    CONSTRAINT codecs_pkey PRIMARY KEY (id),
    CONSTRAINT uk_codec_name_library UNIQUE (name, library_id)
);

CREATE TABLE controlled_dictionaries (
    case_sensitive boolean NOT NULL,
    locked boolean NOT NULL,
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    unicode_normalization character varying(32) NOT NULL,
    description text,
    id character varying(255) NOT NULL,
    library_id character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    CONSTRAINT controlled_dictionaries_pkey PRIMARY KEY (id),
    CONSTRAINT uk_dictionary_name_library UNIQUE (name, library_id)
);

CREATE TABLE controlled_dictionary_entries (
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    dictionary_id character varying(255) NOT NULL,
    id character varying(255) NOT NULL,
    metadata_json text,
    normalized_value text NOT NULL,
    source_entry_key character varying(255),
    surface_form text NOT NULL,
    CONSTRAINT controlled_dictionary_entries_pkey PRIMARY KEY (id)
);

CREATE TABLE controlled_dictionary_tags (
    dictionary_id character varying(255) NOT NULL,
    tag_value text
);

CREATE TABLE dataset_item_copy_files (
    created timestamp(6) without time zone NOT NULL,
    file_size bigint NOT NULL,
    source_updated_at timestamp(6) without time zone,
    updated timestamp(6) without time zone NOT NULL,
    kind character varying(16) NOT NULL,
    base_name character varying(255),
    checksum_sha256 character varying(255) NOT NULL,
    dataset_item_id character varying(255) NOT NULL,
    file_name character varying(255) NOT NULL,
    file_path character varying(255) NOT NULL,
    id character varying(255) NOT NULL,
    mime_type character varying(255) NOT NULL,
    source_file_id character varying(255) NOT NULL,
    variant_name character varying(255),
    image_type character varying(16),
    CONSTRAINT dataset_item_copy_files_image_type_check CHECK ((image_type IN ('COLOR', 'GRAYSCALE', 'BINARY', 'UNKNOWN'))),
    CONSTRAINT dataset_item_copy_files_kind_check CHECK (((kind)::text = ANY (ARRAY[('XML'::character varying)::text, ('IMAGE'::character varying)::text]))),
    CONSTRAINT dataset_item_copy_files_pkey PRIMARY KEY (id)
);
CREATE INDEX dataset_copy_images_uninspected_type_idx ON dataset_item_copy_files (id) WHERE ((image_type IS NULL) AND ((kind)::text = 'IMAGE'::text));

CREATE TABLE dataset_item_copy_xml_versions (
    version_number integer NOT NULL,
    created timestamp(6) without time zone NOT NULL,
    file_size bigint,
    comment character varying(500),
    copy_file_id character varying(255) NOT NULL,
    file_path character varying(255) NOT NULL,
    id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    CONSTRAINT dataset_item_copy_xml_versions_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_ds_copy_xml_ver_copy_file_id ON dataset_item_copy_xml_versions (copy_file_id);
CREATE INDEX idx_ds_copy_xml_ver_copy_file_id_version ON dataset_item_copy_xml_versions (copy_file_id, version_number);

CREATE TABLE dataset_item_source_images (
    dataset_item_id character varying(255) NOT NULL,
    source_image_id character varying(255)
);

CREATE TABLE dataset_item_tags (
    dataset_item_id character varying(255) NOT NULL,
    tag character varying(255)
);

CREATE TABLE dataset_items (
    manual_split boolean NOT NULL,
    copied_at timestamp(6) without time zone,
    created timestamp(6) without time zone NOT NULL,
    selected_source_xml_updated_at timestamp(6) without time zone,
    source_page_updated_at_snapshot timestamp(6) without time zone,
    updated timestamp(6) without time zone NOT NULL,
    assigned_split character varying(16) NOT NULL,
    mode character varying(16) NOT NULL,
    status character varying(16) NOT NULL,
    broken_reason text,
    dataset_id character varying(255) NOT NULL,
    id character varying(255) NOT NULL,
    selected_source_xml_file_name character varying(255) NOT NULL,
    selected_source_xml_id character varying(255) NOT NULL,
    source_page_id character varying(255) NOT NULL,
    source_page_name character varying(255) NOT NULL,
    source_project_id character varying(255) NOT NULL,
    source_project_name character varying(255) NOT NULL,
    CONSTRAINT dataset_items_assigned_split_check CHECK (((assigned_split)::text = ANY (ARRAY[('TRAIN'::character varying)::text, ('VAL'::character varying)::text, ('TEST'::character varying)::text]))),
    CONSTRAINT dataset_items_mode_check CHECK (((mode)::text = ANY (ARRAY[('LINK'::character varying)::text, ('COPY'::character varying)::text]))),
    CONSTRAINT dataset_items_status_check CHECK (((status)::text = ANY (ARRAY[('READY'::character varying)::text, ('BROKEN'::character varying)::text]))),
    CONSTRAINT dataset_items_pkey PRIMARY KEY (id),
    CONSTRAINT uk_dataset_item_source_page UNIQUE (dataset_id, source_page_id)
);

CREATE TABLE dataset_releases (
    version_number integer NOT NULL,
    created timestamp(6) without time zone NOT NULL,
    item_count bigint NOT NULL,
    package_file_size bigint,
    share_created_at timestamp(6) without time zone,
    share_download_count bigint,
    share_expires_at timestamp(6) without time zone,
    share_last_used_at timestamp(6) without time zone,
    share_revoked_at timestamp(6) without time zone,
    source_dataset_updated_at timestamp(6) without time zone,
    updated timestamp(6) without time zone NOT NULL,
    share_secret_prefix character varying(16),
    status character varying(32) NOT NULL,
    validation_status character varying(32) NOT NULL,
    manifest_checksum_sha256 character varying(64),
    package_checksum_sha256 character varying(64),
    share_public_id character varying(64),
    share_secret_hash character varying(64),
    version_tag character varying(128) NOT NULL,
    created_by_user_id character varying(255) NOT NULL,
    dataset_id character varying(255) NOT NULL,
    failure_reason text,
    id character varying(255) NOT NULL,
    manifest_json text,
    notes text,
    package_file_name character varying(255),
    package_file_path character varying(255),
    share_created_by_user_id character varying(255),
    stats_json text,
    warnings_json text,
    CONSTRAINT dataset_releases_status_check CHECK (((status)::text = ANY (ARRAY[('CREATING'::character varying)::text, ('READY'::character varying)::text, ('FAILED'::character varying)::text]))),
    CONSTRAINT dataset_releases_validation_status_check CHECK (((validation_status)::text = ANY (ARRAY[('NOT_VALIDATED'::character varying)::text, ('VALID'::character varying)::text, ('INVALID'::character varying)::text]))),
    CONSTRAINT dataset_releases_pkey PRIMARY KEY (id),
    CONSTRAINT dataset_releases_share_public_id_key UNIQUE (share_public_id),
    CONSTRAINT uk_dataset_release_version_number UNIQUE (dataset_id, version_number),
    CONSTRAINT uk_dataset_release_version_tag UNIQUE (dataset_id, version_tag)
);

CREATE TABLE dataset_stratify_tags (
    dataset_id character varying(255) NOT NULL,
    tag_id character varying(255)
);

CREATE TABLE dataset_tags (
    dataset_id character varying(255) NOT NULL,
    tag character varying(255)
);

CREATE TABLE datasets (
    test_percentage integer NOT NULL,
    train_percentage integer NOT NULL,
    val_percentage integer NOT NULL,
    created timestamp(6) without time zone NOT NULL,
    last_exported_at timestamp(6) without time zone,
    last_validation_at timestamp(6) without time zone,
    split_seed bigint NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    last_export_status character varying(32) NOT NULL,
    last_validation_status character varying(32) NOT NULL,
    split_template character varying(32) NOT NULL,
    split_algorithm character varying(64) NOT NULL,
    description text,
    id character varying(255) NOT NULL,
    last_validation_warnings_json text,
    name character varying(255) NOT NULL,
    workspace_id character varying(255) NOT NULL,
    output_retention_days integer,
    managed_key character varying(64),
    managed_label character varying(255),
    CONSTRAINT datasets_last_export_status_check CHECK (((last_export_status)::text = ANY (ARRAY[('NEVER_EXPORTED'::character varying)::text, ('READY'::character varying)::text, ('FAILED'::character varying)::text]))),
    CONSTRAINT datasets_last_validation_status_check CHECK (((last_validation_status)::text = ANY (ARRAY[('NOT_VALIDATED'::character varying)::text, ('VALID'::character varying)::text, ('INVALID'::character varying)::text]))),
    CONSTRAINT datasets_output_retention_days_check CHECK (((output_retention_days IS NULL) OR (output_retention_days > 0))),
    CONSTRAINT datasets_split_algorithm_check CHECK (((split_algorithm)::text = ANY (ARRAY[('RANDOM_SEEDED'::character varying)::text, ('GROUP_BY_SOURCE_PROJECT'::character varying)::text, ('MULTILABEL_STRATIFIED_BY_TAGS'::character varying)::text]))),
    CONSTRAINT datasets_split_template_check CHECK ((split_template IN ('TRAIN', 'VAL', 'TEST', 'TRAIN_VAL', 'TRAIN_TEST', 'VAL_TEST', 'TRAIN_VAL_TEST'))),
    CONSTRAINT datasets_pkey PRIMARY KEY (id),
    CONSTRAINT uk_dataset_workspace_name UNIQUE (workspace_id, name)
);
CREATE UNIQUE INDEX uk_datasets_workspace_managed_key ON datasets (workspace_id, managed_key) WHERE (managed_key IS NOT NULL);

CREATE TABLE editor_preferences (
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    preferences jsonb DEFAULT '{}'::jsonb NOT NULL,
    CONSTRAINT editor_preferences_pkey PRIMARY KEY (id),
    CONSTRAINT editor_preferences_user_id_key UNIQUE (user_id)
);

CREATE TABLE error_events (
    status integer NOT NULL,
    created timestamp(6) without time zone NOT NULL,
    method character varying(16) NOT NULL,
    severity character varying(16) NOT NULL,
    code character varying(128),
    path character varying(512) NOT NULL,
    details_json text,
    error character varying(255) NOT NULL,
    exception_class character varying(255),
    id character varying(255) NOT NULL,
    message text NOT NULL,
    stack_trace text,
    user_id character varying(255),
    username character varying(255),
    workspace_id character varying(255),
    CONSTRAINT error_events_severity_check CHECK (((severity)::text = ANY (ARRAY[('WARN'::character varying)::text, ('ERROR'::character varying)::text]))),
    CONSTRAINT error_events_pkey PRIMARY KEY (id)
);

CREATE TABLE iiif_import_job_items (
    id character varying(255) NOT NULL,
    job_id character varying(255) NOT NULL,
    canvas_id text,
    canvas_label text,
    canvas_index integer NOT NULL,
    requested_page_name character varying(255),
    final_page_name character varying(255),
    action character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    page_id character varying(255),
    message text,
    actual_bytes bigint,
    created timestamp without time zone NOT NULL,
    CONSTRAINT iiif_import_job_items_pkey PRIMARY KEY (id),
    CONSTRAINT uk_iiif_import_job_item_canvas UNIQUE (job_id, canvas_index)
);
CREATE INDEX idx_iiif_import_job_items_job_status ON iiif_import_job_items (job_id, status);

CREATE TABLE iiif_import_jobs (
    failed_canvases integer NOT NULL,
    processed_canvases integer NOT NULL,
    quota_reservation_released boolean NOT NULL,
    skipped_canvases integer NOT NULL,
    total_canvases integer NOT NULL,
    completed_at timestamp(6) without time zone,
    created timestamp(6) without time zone NOT NULL,
    estimated_storage_bytes bigint NOT NULL,
    reserved_bytes bigint NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    canvas_payload_json text,
    created_by_user_id character varying(255) NOT NULL,
    error_message text,
    id character varying(255) NOT NULL,
    import_log text,
    manifest_summary_json text,
    project_id character varying(255) NOT NULL,
    results_json text,
    source_reference text NOT NULL,
    source_type character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    warnings_json text,
    workspace_id character varying(255) NOT NULL,
    dismissed boolean DEFAULT false NOT NULL,
    lease_owner character varying(255),
    lease_expires_at timestamp without time zone,
    last_heartbeat_at timestamp without time zone,
    CONSTRAINT iiif_import_jobs_source_type_check CHECK (((source_type)::text = ANY (ARRAY[('MANIFEST_URL'::character varying)::text, ('MANIFEST_FILE'::character varying)::text]))),
    CONSTRAINT iiif_import_jobs_status_check CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('IMPORTING'::character varying)::text, ('COMPLETED'::character varying)::text, ('FAILED'::character varying)::text, ('CANCELLED'::character varying)::text]))),
    CONSTRAINT iiif_import_jobs_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_iiif_import_jobs_expired_lease ON iiif_import_jobs (status, lease_expires_at);
CREATE INDEX idx_iiif_import_jobs_status_panel ON iiif_import_jobs (workspace_id, created_by_user_id, dismissed, created DESC);

CREATE TABLE iiif_runtime_settings (
    id smallint NOT NULL,
    download_min_interval_ms integer,
    updated_at timestamp without time zone NOT NULL,
    updated_by_user_id character varying(255),
    CONSTRAINT iiif_runtime_settings_download_min_interval_ms_check CHECK (((download_min_interval_ms IS NULL) OR ((download_min_interval_ms >= 0) AND (download_min_interval_ms <= 60000)))),
    CONSTRAINT iiif_runtime_settings_id_check CHECK ((id = 1)),
    CONSTRAINT iiif_runtime_settings_pkey PRIMARY KEY (id)
);

CREATE TABLE import_jobs (
    failed_files integer NOT NULL,
    overwrite_existing boolean NOT NULL,
    processed_files integer NOT NULL,
    quota_reservation_released boolean NOT NULL,
    skipped_files integer NOT NULL,
    total_files integer NOT NULL,
    completed_at timestamp(6) without time zone,
    created timestamp(6) without time zone NOT NULL,
    reserved_bytes bigint NOT NULL,
    total_bytes bigint NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    copy_mode character varying(255) NOT NULL,
    created_by_user_id character varying(255) NOT NULL,
    error_message text,
    id character varying(255) NOT NULL,
    import_log text,
    project_id character varying(255) NOT NULL,
    scan_results text,
    source_path character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    validation_errors text,
    workspace_id character varying(255) NOT NULL,
    CONSTRAINT import_jobs_copy_mode_check CHECK (((copy_mode)::text = 'COPY'::text)),
    CONSTRAINT import_jobs_status_check CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('SCANNING'::character varying)::text, ('VALIDATING'::character varying)::text, ('IMPORTING'::character varying)::text, ('COMPLETED'::character varying)::text, ('FAILED'::character varying)::text, ('CANCELLED'::character varying)::text]))),
    CONSTRAINT import_jobs_pkey PRIMARY KEY (id)
);

CREATE TABLE keyboard_items (
    w integer NOT NULL,
    x integer NOT NULL,
    y integer NOT NULL,
    id bigint NOT NULL,
    char_value character varying(255),
    color_class character varying(255),
    description character varying(255),
    shift_char character varying(255),
    shift_description character varying(255),
    text_class character varying(255),
    virtual_keyboard_id character varying(255),
    CONSTRAINT keyboard_items_pkey PRIMARY KEY (id)
);
ALTER TABLE keyboard_items ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME keyboard_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

CREATE TABLE label_set_tags (
    label_set_id character varying(255) NOT NULL,
    tag character varying(255) NOT NULL
);

CREATE TABLE label_sets (
    is_system boolean NOT NULL,
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    description text,
    id character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    workspace_id character varying(255) NOT NULL,
    definition jsonb NOT NULL,
    CONSTRAINT label_sets_pkey PRIMARY KEY (id),
    CONSTRAINT uk_label_set_workspace_name UNIQUE (workspace_id, name)
);

CREATE TABLE libraries (
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    id character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    workspace_id character varying(255) NOT NULL,
    CONSTRAINT libraries_pkey PRIMARY KEY (id),
    CONSTRAINT libraries_workspace_id_key UNIQUE (workspace_id)
);

CREATE TABLE normalization_profile_replacement_rules (
    regex_rule boolean NOT NULL,
    rule_order integer NOT NULL,
    normalization_profile_id character varying(255) CONSTRAINT normalization_profile_replace_normalization_profile_id_not_null NOT NULL,
    replacement_value text CONSTRAINT normalization_profile_replacement_ru_replacement_value_not_null NOT NULL,
    search_value text NOT NULL,
    CONSTRAINT normalization_profile_replacement_rules_pkey PRIMARY KEY (rule_order, normalization_profile_id)
);

CREATE TABLE normalization_profile_tags (
    normalization_profile_id character varying(255) NOT NULL,
    tag character varying(255) NOT NULL
);

CREATE TABLE normalization_profiles (
    collapse_whitespace boolean NOT NULL,
    dehyphenate_line_breaks boolean NOT NULL,
    expand_common_ligatures boolean NOT NULL,
    map_longstos boolean NOT NULL,
    normalize_dashes boolean NOT NULL,
    normalize_ellipsis boolean NOT NULL,
    normalize_quotes boolean NOT NULL,
    trim_text boolean NOT NULL,
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    unicode_normalization character varying(32) NOT NULL,
    description text,
    id character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    workspace_id character varying(255) NOT NULL,
    CONSTRAINT normalization_profiles_pkey PRIMARY KEY (id),
    CONSTRAINT uk_normalization_profile_workspace_name UNIQUE (workspace_id, name)
);

CREATE TABLE notification_preferences (
    desktop_enabled boolean NOT NULL,
    email_enabled boolean NOT NULL,
    in_app_enabled boolean NOT NULL,
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    id character varying(255) NOT NULL,
    notification_type character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    CONSTRAINT notification_preferences_notification_type_check CHECK ((notification_type IN ('WORKSPACE_INVITATION', 'TASK_ASSIGNED', 'TASK_COMPLETED', 'TASK_REMINDER', 'TASK_MENTIONED', 'TASK_UPDATED', 'TASK_DUE_SOON', 'TASK_OVERDUE', 'TASK_COMMENT_ADDED', 'PROJECT_CREATED', 'PROJECT_DELETED', 'PAGE_CREATED', 'PAGE_DELETED', 'WORKSPACE_WATCH', 'PROJECT_WATCH', 'UPLOAD_COMPLETED', 'UPLOAD_FAILED', 'IMPORT_COMPLETED', 'IMPORT_FAILED', 'COLLAB_TAKEOVER_REQUESTED', 'COLLAB_TAKEOVER_GRANTED', 'COLLAB_TAKEOVER_DECLINED', 'COLLAB_TAKEOVER_FORCED', 'COLLAB_LEASE_EXPIRED', 'PAGE_REVIEW_REQUESTED', 'PAGE_CHANGES_REQUESTED'))),
    CONSTRAINT notification_preferences_pkey PRIMARY KEY (id),
    CONSTRAINT notification_preferences_user_id_notification_type_key UNIQUE (user_id, notification_type)
);

CREATE TABLE notifications (
    read boolean NOT NULL,
    created timestamp(6) without time zone NOT NULL,
    read_at timestamp(6) without time zone,
    updated timestamp(6) without time zone NOT NULL,
    id character varying(255) NOT NULL,
    link text,
    message text,
    related_entity_id character varying(255),
    related_entity_type character varying(255),
    title character varying(255) NOT NULL,
    type character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    CONSTRAINT notifications_type_check CHECK ((type IN ('WORKSPACE_INVITATION', 'TASK_ASSIGNED', 'TASK_COMPLETED', 'TASK_REMINDER', 'TASK_MENTIONED', 'TASK_UPDATED', 'TASK_DUE_SOON', 'TASK_OVERDUE', 'TASK_COMMENT_ADDED', 'PROJECT_CREATED', 'PROJECT_DELETED', 'PAGE_CREATED', 'PAGE_DELETED', 'WORKSPACE_WATCH', 'PROJECT_WATCH', 'UPLOAD_COMPLETED', 'UPLOAD_FAILED', 'IMPORT_COMPLETED', 'IMPORT_FAILED', 'COLLAB_TAKEOVER_REQUESTED', 'COLLAB_TAKEOVER_GRANTED', 'COLLAB_TAKEOVER_DECLINED', 'COLLAB_TAKEOVER_FORCED', 'COLLAB_LEASE_EXPIRED', 'PAGE_REVIEW_REQUESTED', 'PAGE_CHANGES_REQUESTED'))),
    CONSTRAINT notifications_pkey PRIMARY KEY (id)
);

CREATE TABLE page_confidence_index (
    confidence double precision NOT NULL,
    created timestamp(6) without time zone NOT NULL,
    element_id character varying(255),
    element_type character varying(255) NOT NULL,
    id character varying(255) NOT NULL,
    page_id character varying(255) NOT NULL,
    CONSTRAINT page_confidence_index_element_type_check CHECK (((element_type)::text = ANY (ARRAY[('PAGE'::character varying)::text, ('COORDS'::character varying)::text, ('TEXTEQUIV'::character varying)::text, ('READING_ORDER'::character varying)::text, ('BASELINE'::character varying)::text, ('ALTERNATIVE_IMAGE'::character varying)::text]))),
    CONSTRAINT page_confidence_index_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_page_confidence_index_confidence ON page_confidence_index (confidence);
CREATE INDEX idx_page_confidence_index_element_type ON page_confidence_index (element_type);
CREATE INDEX idx_page_confidence_index_page_id ON page_confidence_index (page_id);

CREATE TABLE page_images (
    created timestamp(6) without time zone NOT NULL,
    file_size bigint,
    updated timestamp(6) without time zone NOT NULL,
    base_name character varying(255) NOT NULL,
    file_name character varying(255) NOT NULL,
    file_path character varying(255) NOT NULL,
    id character varying(255) NOT NULL,
    mime_type character varying(255) NOT NULL,
    page_id character varying(255) NOT NULL,
    thumbnail_path character varying(255),
    variant character varying(255) NOT NULL,
    image_type character varying(16),
    CONSTRAINT page_images_image_type_check CHECK ((image_type IN ('COLOR', 'GRAYSCALE', 'BINARY', 'UNKNOWN'))),
    CONSTRAINT page_images_pkey PRIMARY KEY (id)
);
CREATE INDEX page_images_uninspected_type_idx ON page_images (id) WHERE (image_type IS NULL);

CREATE TABLE page_label_index (
    created timestamp(6) without time zone NOT NULL,
    element_id character varying(255) NOT NULL,
    element_type character varying(255) NOT NULL,
    id character varying(255) NOT NULL,
    label_id character varying(255) NOT NULL,
    page_id character varying(255) NOT NULL,
    CONSTRAINT page_label_index_element_type_check CHECK (((element_type)::text = ANY (ARRAY[('REGION'::character varying)::text, ('LINE'::character varying)::text]))),
    CONSTRAINT page_label_index_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_page_label_index_label_id ON page_label_index (label_id);
CREATE INDEX idx_page_label_index_page_id ON page_label_index (page_id);

CREATE TABLE page_tags (
    page_id character varying(255) NOT NULL,
    tag character varying(255)
);

CREATE TABLE page_text_content (
    comment_entry boolean NOT NULL,
    variant_index integer,
    created timestamp(6) without time zone NOT NULL,
    id character varying(255) NOT NULL,
    normalized_text text,
    page_id character varying(255) NOT NULL,
    region_id character varying(255),
    text_content text,
    text_line_id character varying(255),
    search_vector tsvector,
    CONSTRAINT page_text_content_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_page_text_content_comment_entry ON page_text_content (comment_entry);
CREATE INDEX idx_page_text_content_normalized_trgm ON page_text_content USING gin (normalized_text gin_trgm_ops);
CREATE INDEX idx_page_text_content_page_id ON page_text_content (page_id);
CREATE INDEX idx_page_text_content_search_vector ON page_text_content USING gin (search_vector);
CREATE INDEX idx_page_text_content_text ON page_text_content (text_content);

CREATE TABLE page_workflow_events (
    id character varying(255) NOT NULL,
    page_id character varying(255) NOT NULL,
    action character varying(32) NOT NULL,
    from_state character varying(32) NOT NULL,
    to_state character varying(32) NOT NULL,
    actor_user_id character varying(255),
    comment text,
    created timestamp(6) without time zone NOT NULL,
    CONSTRAINT page_workflow_events_action_check CHECK ((action IN ('SUBMIT', 'APPROVE', 'REQUEST_CHANGES', 'WITHDRAW', 'MARK_DONE', 'REOPEN', 'ASSIGNMENT_SYNC'))),
    CONSTRAINT page_workflow_events_from_state_check CHECK ((from_state IN ('OPEN', 'IN_REVIEW', 'DONE'))),
    CONSTRAINT page_workflow_events_to_state_check CHECK ((to_state IN ('OPEN', 'IN_REVIEW', 'DONE'))),
    CONSTRAINT page_workflow_events_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_page_workflow_events_page_created ON page_workflow_events (page_id, created DESC);

CREATE TABLE page_xml_attribute_index (
    created timestamp(6) without time zone NOT NULL,
    id character varying(255) NOT NULL,
    page_id character varying(255) NOT NULL,
    element_name character varying(255) NOT NULL,
    attribute_name character varying(255) NOT NULL,
    attribute_value text NOT NULL,
    CONSTRAINT page_xml_attribute_index_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_page_xml_attribute_index_element_name ON page_xml_attribute_index (element_name, attribute_name);
CREATE INDEX idx_page_xml_attribute_index_name ON page_xml_attribute_index (attribute_name);
CREATE INDEX idx_page_xml_attribute_index_page_id ON page_xml_attribute_index (page_id);
CREATE INDEX idx_page_xml_attribute_index_value_exact ON page_xml_attribute_index USING hash (attribute_value);
CREATE INDEX idx_page_xml_attribute_index_value_trgm ON page_xml_attribute_index USING gin (attribute_value gin_trgm_ops);

CREATE TABLE page_xml_versions (
    version_number integer NOT NULL,
    created timestamp(6) without time zone NOT NULL,
    file_size bigint,
    comment character varying(500),
    file_path character varying(255) NOT NULL,
    id character varying(255) NOT NULL,
    page_xml_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    CONSTRAINT page_xml_versions_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_page_xml_version_xml_id ON page_xml_versions (page_xml_id);
CREATE INDEX idx_page_xml_version_xml_id_version ON page_xml_versions (page_xml_id, version_number);

CREATE TABLE page_xmls (
    created timestamp(6) without time zone NOT NULL,
    file_size bigint,
    updated timestamp(6) without time zone NOT NULL,
    base_name character varying(255) NOT NULL,
    file_name character varying(255) NOT NULL,
    file_path character varying(255) NOT NULL,
    id character varying(255) NOT NULL,
    mime_type character varying(255) NOT NULL,
    page_id character varying(255) NOT NULL,
    schema character varying(255) NOT NULL,
    schema_version character varying(255),
    variant character varying(255) NOT NULL,
    CONSTRAINT page_xmls_schema_check CHECK (((schema)::text = ANY (ARRAY[('PAGE_XML'::character varying)::text, ('ALTO_XML'::character varying)::text, ('UNKNOWN'::character varying)::text]))),
    CONSTRAINT page_xmls_pkey PRIMARY KEY (id),
    CONSTRAINT uk_page_xmls_page_id UNIQUE (page_id)
);

CREATE TABLE pages (
    locked boolean NOT NULL,
    sort_order integer,
    created timestamp(6) without time zone NOT NULL,
    locked_at timestamp(6) without time zone,
    updated timestamp(6) without time zone NOT NULL,
    external_source_type character varying(64),
    description text,
    external_source_id text,
    external_source_metadata_json text,
    external_source_url text,
    id character varying(255) NOT NULL,
    locked_by_action_run_id character varying(255),
    locked_reason text,
    name character varying(255) NOT NULL,
    project_id character varying(255) NOT NULL,
    workflow_state character varying(32) DEFAULT 'OPEN'::character varying NOT NULL,
    review_submitted_by_user_id character varying(255),
    review_submitted_at timestamp(6) without time zone,
    changes_requested boolean DEFAULT false NOT NULL,
    CONSTRAINT pages_workflow_state_check CHECK ((workflow_state IN ('OPEN', 'IN_REVIEW', 'DONE'))),
    CONSTRAINT pages_pkey PRIMARY KEY (id),
    CONSTRAINT uk_page_name_project UNIQUE (name, project_id)
);
CREATE INDEX idx_pages_project_workflow_state ON pages (project_id, workflow_state);

CREATE TABLE personal_workspaces (
    default_gt_index integer,
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    avatar character varying(255),
    codec_id character varying(255),
    default_recognition_indices text,
    description character varying(255),
    dictionary_id character varying(255),
    id character varying(255) NOT NULL,
    label_set_id character varying(255),
    normalization_profile_id character varying(255),
    owner_user_id character varying(255) NOT NULL,
    tag_set_id character varying(255),
    validation_ruleset_id character varying(255),
    allow_editors_edit_page_name boolean DEFAULT false NOT NULL,
    allow_editors_edit_page_description boolean DEFAULT false CONSTRAINT personal_workspaces_allow_editors_edit_page_descriptio_not_null NOT NULL,
    allow_editors_edit_page_tags boolean DEFAULT false NOT NULL,
    default_review_required boolean DEFAULT false NOT NULL,
    CONSTRAINT personal_workspaces_pkey PRIMARY KEY (id)
);

CREATE TABLE project_export_jobs (
    cancel_requested boolean DEFAULT false NOT NULL,
    artifact_size bigint,
    completed_at timestamp without time zone,
    created timestamp without time zone NOT NULL,
    expires_at timestamp without time zone,
    last_heartbeat_at timestamp without time zone,
    lease_expires_at timestamp without time zone,
    started_at timestamp without time zone,
    updated timestamp without time zone NOT NULL,
    artifact_checksum_sha256 character varying(64),
    status character varying(32) NOT NULL,
    created_by_user_id character varying(255) NOT NULL,
    id character varying(255) NOT NULL,
    lease_owner character varying(255),
    workspace_id character varying(255) NOT NULL,
    artifact_file_name text,
    artifact_path text,
    error_message text,
    request_json text NOT NULL,
    CONSTRAINT project_export_jobs_status_check CHECK ((status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED', 'EXPIRED'))),
    CONSTRAINT project_export_jobs_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_project_export_jobs_expiry ON project_export_jobs (status, expires_at);
CREATE INDEX idx_project_export_jobs_lease ON project_export_jobs (status, lease_expires_at);
CREATE INDEX idx_project_export_jobs_owner ON project_export_jobs (workspace_id, created_by_user_id, created DESC);
CREATE INDEX idx_project_export_jobs_queue ON project_export_jobs (status, created);

CREATE TABLE project_package_releases (
    version_number integer NOT NULL,
    created timestamp(6) without time zone NOT NULL,
    package_file_size bigint,
    page_count bigint NOT NULL,
    share_created_at timestamp(6) without time zone,
    share_download_count bigint,
    share_expires_at timestamp(6) without time zone,
    share_last_used_at timestamp(6) without time zone,
    share_revoked_at timestamp(6) without time zone,
    source_project_updated_at timestamp(6) without time zone,
    updated timestamp(6) without time zone NOT NULL,
    share_secret_prefix character varying(16),
    status character varying(32) NOT NULL,
    target_page_xml_version character varying(32),
    manifest_checksum_sha256 character varying(64),
    package_checksum_sha256 character varying(64),
    share_public_id character varying(64),
    share_secret_hash character varying(64),
    version_tag character varying(128) NOT NULL,
    created_by_user_id character varying(255) NOT NULL,
    embedded_outputs_json text,
    failure_reason text,
    id character varying(255) NOT NULL,
    notes text,
    package_file_name character varying(255),
    package_file_path character varying(255),
    project_id character varying(255) NOT NULL,
    share_created_by_user_id character varying(255),
    include_xml_history boolean DEFAULT true NOT NULL,
    CONSTRAINT project_package_releases_status_check CHECK (((status)::text = ANY (ARRAY[('CREATING'::character varying)::text, ('READY'::character varying)::text, ('FAILED'::character varying)::text]))),
    CONSTRAINT project_package_releases_pkey PRIMARY KEY (id),
    CONSTRAINT project_package_releases_share_public_id_key UNIQUE (share_public_id),
    CONSTRAINT uk_project_package_release_version_number UNIQUE (project_id, version_number),
    CONSTRAINT uk_project_package_release_version_tag UNIQUE (project_id, version_tag)
);

CREATE TABLE project_stars (
    created timestamp(6) without time zone NOT NULL,
    id character varying(255) NOT NULL,
    project_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    CONSTRAINT project_stars_pkey PRIMARY KEY (id),
    CONSTRAINT uk_star_user_project UNIQUE (user_id, project_id)
);

CREATE TABLE project_tags (
    project_id character varying(255) NOT NULL,
    tag character varying(255)
);

CREATE TABLE project_transfer_requests (
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    approved_by_user_id character varying(255),
    id character varying(255) NOT NULL,
    message text,
    project_id character varying(255) NOT NULL,
    rejection_reason text,
    requested_by_user_id character varying(255) NOT NULL,
    source_workspace_id character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    target_workspace_id character varying(255) NOT NULL,
    transfer_type character varying(255) NOT NULL,
    target_project_name character varying(255),
    CONSTRAINT project_transfer_requests_status_check CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('APPROVED'::character varying)::text, ('REJECTED'::character varying)::text, ('COMPLETED'::character varying)::text, ('CANCELLED'::character varying)::text]))),
    CONSTRAINT project_transfer_requests_transfer_type_check CHECK (((transfer_type)::text = ANY (ARRAY[('MOVE'::character varying)::text, ('COPY'::character varying)::text]))),
    CONSTRAINT project_transfer_requests_pkey PRIMARY KEY (id)
);

CREATE TABLE projects (
    allow_codec_override boolean NOT NULL,
    allow_dictionary_override boolean NOT NULL,
    allow_label_set_override boolean NOT NULL,
    allow_normalization_profile_override boolean NOT NULL,
    allow_tag_set_override boolean NOT NULL,
    allow_validation_ruleset_override boolean NOT NULL,
    allow_virtual_keyboard_override boolean NOT NULL,
    default_gt_index integer,
    locked boolean NOT NULL,
    created timestamp(6) without time zone NOT NULL,
    locked_at timestamp(6) without time zone,
    updated timestamp(6) without time zone NOT NULL,
    codec_id character varying(255),
    default_recognition_indices text,
    description text,
    dictionary_id character varying(255),
    id character varying(255) NOT NULL,
    label_set_id character varying(255),
    library_id character varying(255) NOT NULL,
    locked_by_action_run_id character varying(255),
    locked_reason text,
    name character varying(255) NOT NULL,
    normalization_profile_id character varying(255),
    tag_set_id character varying(255),
    validation_ruleset_id character varying(255),
    virtual_keyboard_id character varying(255),
    output_retention_days integer,
    primary_image_variant character varying(255),
    review_required boolean,
    CONSTRAINT projects_output_retention_days_check CHECK (((output_retention_days IS NULL) OR (output_retention_days > 0))),
    CONSTRAINT projects_pkey PRIMARY KEY (id),
    CONSTRAINT uk_project_name_library UNIQUE (name, library_id)
);

CREATE TABLE recent_projects (
    created timestamp(6) without time zone NOT NULL,
    last_accessed timestamp(6) without time zone NOT NULL,
    access_type character varying(255) NOT NULL,
    id character varying(255) NOT NULL,
    project_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    CONSTRAINT recent_projects_access_type_check CHECK (((access_type)::text = ANY (ARRAY[('CREATED'::character varying)::text, ('VIEWED'::character varying)::text, ('EDITED'::character varying)::text]))),
    CONSTRAINT recent_projects_pkey PRIMARY KEY (id),
    CONSTRAINT uk_recent_user_project UNIQUE (user_id, project_id)
);

CREATE TABLE resource_transfer_requests (
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    approved_by_user_id character varying(255),
    id character varying(255) NOT NULL,
    message text,
    rejection_reason text,
    requested_by_user_id character varying(255) NOT NULL,
    resource_id character varying(255) NOT NULL,
    resource_type character varying(255) NOT NULL,
    source_workspace_id character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    target_workspace_id character varying(255) NOT NULL,
    transfer_type character varying(255) NOT NULL,
    target_name character varying(255),
    CONSTRAINT resource_transfer_requests_resource_type_check CHECK (((resource_type)::text = ANY (ARRAY[('CODEC'::character varying)::text, ('DICTIONARY'::character varying)::text, ('VIRTUAL_KEYBOARD'::character varying)::text, ('LABEL_SET'::character varying)::text, ('TAG_SET'::character varying)::text, ('NORMALIZATION_PROFILE'::character varying)::text, ('VALIDATION_RULESET'::character varying)::text]))),
    CONSTRAINT resource_transfer_requests_status_check CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('APPROVED'::character varying)::text, ('REJECTED'::character varying)::text, ('COMPLETED'::character varying)::text, ('CANCELLED'::character varying)::text]))),
    CONSTRAINT resource_transfer_requests_transfer_type_check CHECK (((transfer_type)::text = ANY (ARRAY[('MOVE'::character varying)::text, ('COPY'::character varying)::text]))),
    CONSTRAINT resource_transfer_requests_pkey PRIMARY KEY (id)
);

CREATE TABLE search_lexicon_entries (
    occurrence_count integer NOT NULL,
    id character varying(255) NOT NULL,
    normalized_token character varying(255) NOT NULL,
    project_id character varying(255) NOT NULL,
    workspace_id character varying(255) NOT NULL,
    CONSTRAINT search_lexicon_entries_pkey PRIMARY KEY (id),
    CONSTRAINT uk_search_lexicon_workspace_project_token UNIQUE (workspace_id, project_id, normalized_token)
);
CREATE INDEX idx_search_lexicon_normalized_trgm ON search_lexicon_entries USING gin (normalized_token gin_trgm_ops);
CREATE INDEX idx_search_lexicon_workspace_project ON search_lexicon_entries (workspace_id, project_id);

CREATE TABLE stored_files (
    created_at timestamp(6) without time zone NOT NULL,
    size_bytes bigint NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    extension character varying(16) NOT NULL,
    file_type character varying(16) NOT NULL,
    status character varying(16) NOT NULL,
    uuid character varying(32) NOT NULL,
    checksum_sha256 character varying(64) NOT NULL,
    mime_type character varying(128) NOT NULL,
    original_filename character varying(512) NOT NULL,
    storage_path character varying(1024) NOT NULL,
    created_by character varying(255) NOT NULL,
    project_id character varying(255),
    workspace_id character varying(255) NOT NULL,
    dataset_id character varying(255),
    CONSTRAINT stored_files_file_type_check CHECK ((file_type IN ('IMG', 'XML', 'THUMB', 'OUTPUT'))),
    CONSTRAINT stored_files_owner_check CHECK ((((project_id IS NOT NULL) AND (dataset_id IS NULL)) OR ((project_id IS NULL) AND (dataset_id IS NOT NULL) AND ((file_type)::text = 'OUTPUT'::text)))),
    CONSTRAINT stored_files_status_check CHECK (((status)::text = ANY (ARRAY[('READY'::character varying)::text, ('DELETED'::character varying)::text, ('FAILED'::character varying)::text]))),
    CONSTRAINT stored_files_pkey PRIMARY KEY (uuid),
    CONSTRAINT stored_files_storage_path_key UNIQUE (storage_path)
);
CREATE INDEX idx_stored_files_checksum ON stored_files (checksum_sha256);
CREATE INDEX idx_stored_files_dataset_status ON stored_files (dataset_id, status);
CREATE INDEX idx_stored_files_ws_pr_status ON stored_files (workspace_id, project_id, status);

CREATE TABLE subtasks (
    completed boolean NOT NULL,
    sort_order integer NOT NULL,
    completed_at timestamp(6) without time zone,
    created timestamp(6) without time zone NOT NULL,
    assigned_user_id character varying(255),
    completed_by_user_id character varying(255),
    description text,
    id character varying(255) NOT NULL,
    page_id character varying(255),
    task_id character varying(255) NOT NULL,
    title character varying(255) NOT NULL,
    CONSTRAINT subtasks_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_subtasks_open_assigned_page ON subtasks (assigned_user_id, task_id, page_id) WHERE ((completed = false) AND (page_id IS NOT NULL));
CREATE INDEX idx_subtasks_page ON subtasks (page_id) WHERE (page_id IS NOT NULL);
CREATE INDEX idx_subtasks_task_sort ON subtasks (task_id, sort_order);

CREATE TABLE tag_set_meta_tags (
    tag character varying(255) NOT NULL,
    tag_set_id character varying(255) NOT NULL
);

CREATE TABLE tag_sets (
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    description text,
    id character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    workspace_id character varying(255) NOT NULL,
    definition jsonb NOT NULL,
    CONSTRAINT tag_sets_pkey PRIMARY KEY (id),
    CONSTRAINT uk_tag_set_workspace_name UNIQUE (workspace_id, name)
);

CREATE TABLE task_activity_logs (
    created timestamp(6) without time zone NOT NULL,
    activity_type character varying(255) NOT NULL,
    details text,
    id character varying(255) NOT NULL,
    new_value text,
    old_value text,
    task_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    CONSTRAINT task_activity_logs_activity_type_check CHECK ((activity_type IN ('CREATED', 'TITLE_CHANGED', 'DESCRIPTION_CHANGED', 'STATUS_CHANGED', 'STATUS_DERIVED', 'PRIORITY_CHANGED', 'DUE_DATE_CHANGED', 'ASSIGNEES_CHANGED', 'COMMENT_ADDED', 'COMMENT_UPDATED', 'COMMENT_DELETED', 'SUBTASK_ADDED', 'SUBTASK_COMPLETED', 'SUBTASK_DELETED', 'LINK_ADDED', 'LINK_REMOVED', 'ARCHIVED', 'UNARCHIVED'))),
    CONSTRAINT task_activity_logs_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_task_activity_logs_task_created ON task_activity_logs (task_id, created);

CREATE TABLE task_assignees (
    task_id character varying(255) NOT NULL,
    user_id character varying(255)
);
CREATE INDEX idx_task_assignees_user_task ON task_assignees (user_id, task_id);

CREATE TABLE task_comment_mentions (
    comment_id character varying(255) NOT NULL,
    user_id character varying(255)
);

CREATE TABLE task_comments (
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    content text NOT NULL,
    id character varying(255) NOT NULL,
    task_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    CONSTRAINT task_comments_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_task_comments_task ON task_comments (task_id);

CREATE TABLE task_page_links (
    created timestamp(6) without time zone NOT NULL,
    created_by_user_id character varying(255),
    id character varying(255) NOT NULL,
    link_type character varying(255) NOT NULL,
    page_id character varying(255) NOT NULL,
    tag_filter character varying(255),
    task_id character varying(255) NOT NULL,
    CONSTRAINT task_page_links_link_type_check CHECK (((link_type)::text = ANY (ARRAY[('MANUAL'::character varying)::text, ('BY_TAG'::character varying)::text]))),
    CONSTRAINT task_page_links_pkey PRIMARY KEY (id),
    CONSTRAINT task_page_links_task_id_page_id_key UNIQUE (task_id, page_id)
);
CREATE INDEX idx_task_page_links_page ON task_page_links (page_id);

CREATE TABLE task_project_links (
    created timestamp(6) without time zone NOT NULL,
    created_by_user_id character varying(255),
    id character varying(255) NOT NULL,
    project_id character varying(255) NOT NULL,
    task_id character varying(255) NOT NULL,
    CONSTRAINT task_project_links_pkey PRIMARY KEY (id),
    CONSTRAINT task_project_links_task_id_project_id_key UNIQUE (task_id, project_id)
);

CREATE TABLE task_reminders (
    sent boolean NOT NULL,
    created timestamp(6) without time zone NOT NULL,
    reminder_time timestamp(6) without time zone NOT NULL,
    id character varying(255) NOT NULL,
    task_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    CONSTRAINT task_reminders_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_task_reminders_pending ON task_reminders (reminder_time) WHERE (sent = false);
CREATE INDEX idx_task_reminders_task_user ON task_reminders (task_id, user_id);

CREATE TABLE tasks (
    completed_at timestamp(6) without time zone,
    created timestamp(6) without time zone NOT NULL,
    due_date timestamp(6) without time zone,
    updated timestamp(6) without time zone NOT NULL,
    completed_by_user_id character varying(255),
    created_by_user_id character varying(255) NOT NULL,
    description text,
    id character varying(255) NOT NULL,
    priority character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    title character varying(255) NOT NULL,
    workspace_id character varying(255) NOT NULL,
    page_completion character varying(32) DEFAULT 'NONE'::character varying NOT NULL,
    archived_at timestamp(6) without time zone,
    archived_by_user_id character varying(255),
    CONSTRAINT tasks_page_completion_check CHECK ((page_completion IN ('NONE', 'DONE', 'REVIEW'))),
    CONSTRAINT tasks_priority_check CHECK (((priority)::text = ANY (ARRAY[('LOW'::character varying)::text, ('MEDIUM'::character varying)::text, ('HIGH'::character varying)::text, ('URGENT'::character varying)::text]))),
    CONSTRAINT tasks_status_check CHECK ((status IN ('OPEN', 'IN_PROGRESS', 'COMPLETED', 'PAUSED'))),
    CONSTRAINT tasks_pkey PRIMARY KEY (id)
);
CREATE INDEX idx_tasks_workspace_archived ON tasks (workspace_id, archived_at);

CREATE TABLE team_workspaces (
    default_gt_index integer,
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    avatar character varying(255),
    codec_id character varying(255),
    default_recognition_indices text,
    description character varying(255),
    dictionary_id character varying(255),
    id character varying(255) NOT NULL,
    label_set_id character varying(255),
    name character varying(255) NOT NULL,
    normalization_profile_id character varying(255),
    owner_user_id character varying(255) NOT NULL,
    tag_set_id character varying(255),
    validation_ruleset_id character varying(255),
    allow_editors_edit_page_name boolean DEFAULT false NOT NULL,
    allow_editors_edit_page_description boolean DEFAULT false NOT NULL,
    allow_editors_edit_page_tags boolean DEFAULT false NOT NULL,
    default_review_required boolean DEFAULT false NOT NULL,
    CONSTRAINT team_workspaces_name_key UNIQUE (name),
    CONSTRAINT team_workspaces_pkey PRIMARY KEY (id)
);

CREATE TABLE upload_session_files (
    chunk_count integer,
    chunks_received integer,
    created timestamp(6) without time zone NOT NULL,
    file_size bigint NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    base_name character varying(255),
    conflict_resolution character varying(255),
    conflict_type character varying(255),
    created_page_id character varying(255),
    created_page_image_id character varying(255),
    error_message text,
    id character varying(255) NOT NULL,
    mime_type character varying(255),
    original_file_name character varying(255) NOT NULL,
    session_id character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    temp_file_path character varying(255),
    variant character varying(255),
    CONSTRAINT upload_session_files_status_check CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('UPLOADING'::character varying)::text, ('UPLOADED'::character varying)::text, ('PROCESSING'::character varying)::text, ('COMPLETED'::character varying)::text, ('FAILED'::character varying)::text, ('CONFLICT'::character varying)::text, ('SKIPPED'::character varying)::text]))),
    CONSTRAINT upload_session_files_pkey PRIMARY KEY (id)
);

CREATE TABLE upload_sessions (
    failed_files integer NOT NULL,
    processed_files integer NOT NULL,
    quota_reservation_released boolean NOT NULL,
    total_files integer NOT NULL,
    completed_at timestamp(6) without time zone,
    created timestamp(6) without time zone NOT NULL,
    processed_bytes bigint NOT NULL,
    reserved_bytes bigint NOT NULL,
    total_bytes bigint NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    error_message text,
    id character varying(255) NOT NULL,
    project_id character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    workspace_id character varying(255) NOT NULL,
    pdf_render_dpi integer,
    preflight_estimated_bytes bigint,
    preflight_completed_at timestamp(6) without time zone,
    processing_completed_items integer DEFAULT 0 NOT NULL,
    processing_total_items integer DEFAULT 0 NOT NULL,
    processing_current_file_name text,
    CONSTRAINT upload_sessions_status_check CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('UPLOADING'::character varying)::text, ('PROCESSING'::character varying)::text, ('COMPLETED'::character varying)::text, ('FAILED'::character varying)::text, ('CANCELLED'::character varying)::text]))),
    CONSTRAINT upload_sessions_pkey PRIMARY KEY (id)
);

CREATE TABLE user_avatars (
    user_id character varying(255) NOT NULL,
    storage_key character varying(255) NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    CONSTRAINT user_avatars_pkey PRIMARY KEY (user_id),
    CONSTRAINT user_avatars_storage_key_key UNIQUE (storage_key)
);

CREATE TABLE user_logins (
    user_id character varying(255) NOT NULL,
    last_login_at timestamp with time zone NOT NULL,
    CONSTRAINT user_logins_pkey PRIMARY KEY (user_id)
);

CREATE TABLE validation_ruleset_tags (
    tag character varying(255) NOT NULL,
    validation_ruleset_id character varying(255) NOT NULL
);

CREATE TABLE validation_rulesets (
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    description text,
    id character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    rules_json text NOT NULL,
    workspace_id character varying(255) NOT NULL,
    CONSTRAINT uk_validation_ruleset_workspace_name UNIQUE (workspace_id, name),
    CONSTRAINT validation_rulesets_pkey PRIMARY KEY (id)
);

CREATE TABLE virtual_keyboard_tags (
    tag character varying(255) NOT NULL,
    virtual_keyboard_id character varying(255) NOT NULL
);

CREATE TABLE virtual_keyboards (
    cols integer NOT NULL,
    rows integer NOT NULL,
    description text,
    id character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    user_id character varying(255),
    workspace_id character varying(255) NOT NULL,
    CONSTRAINT uk_virtual_keyboard_workspace_name UNIQUE (workspace_id, name),
    CONSTRAINT virtual_keyboards_pkey PRIMARY KEY (id)
);

CREATE TABLE workspace_members (
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    id character varying(255) NOT NULL,
    invitation_status character varying(255) NOT NULL,
    role character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    workspace_id character varying(255) NOT NULL,
    CONSTRAINT workspace_members_invitation_status_check CHECK (((invitation_status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('ACCEPTED'::character varying)::text, ('DECLINED'::character varying)::text]))),
    CONSTRAINT workspace_members_role_check CHECK (((role)::text = ANY (ARRAY[('CURATOR'::character varying)::text, ('EDITOR'::character varying)::text, ('ADMINISTRATOR'::character varying)::text, ('MEMBER'::character varying)::text]))),
    CONSTRAINT workspace_members_pkey PRIMARY KEY (id),
    CONSTRAINT workspace_members_workspace_id_user_id_key UNIQUE (workspace_id, user_id)
);

CREATE TABLE workspace_storage_quotas (
    is_custom boolean NOT NULL,
    created timestamp(6) without time zone NOT NULL,
    current_usage_bytes bigint NOT NULL,
    quota_limit_bytes bigint NOT NULL,
    reserved_bytes bigint NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    id character varying(255) NOT NULL,
    workspace_id character varying(255) NOT NULL,
    CONSTRAINT uk_workspace_storage_quota UNIQUE (workspace_id),
    CONSTRAINT workspace_storage_quotas_pkey PRIMARY KEY (id)
);

ALTER TABLE action_input_snapshots
    ADD CONSTRAINT action_input_snapshots_run_id_fkey FOREIGN KEY (run_id) REFERENCES action_runs(id) ON DELETE CASCADE;

ALTER TABLE action_output_files
    ADD CONSTRAINT fk_action_output_files_output FOREIGN KEY (output_id) REFERENCES action_outputs(id) ON DELETE CASCADE;

ALTER TABLE action_output_files
    ADD CONSTRAINT fk_action_output_files_stored_file FOREIGN KEY (stored_file_uuid) REFERENCES stored_files(uuid);

ALTER TABLE action_outputs
    ADD CONSTRAINT action_outputs_dataset_id_fkey FOREIGN KEY (dataset_id) REFERENCES datasets(id);

ALTER TABLE action_outputs
    ADD CONSTRAINT fk_action_outputs_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE;

ALTER TABLE action_processor_assignments
    ADD CONSTRAINT fkb9usi4lwrro8hlslf10w4b81d FOREIGN KEY (processor_definition_id) REFERENCES action_processor_definitions(id);

ALTER TABLE action_processor_definitions
    ADD CONSTRAINT action_processor_definitions_workflow_id_fkey FOREIGN KEY (workflow_id) REFERENCES action_workflows(id);

ALTER TABLE action_processor_workspace_availability
    ADD CONSTRAINT fknfefnqhpkll57hydm0m9uv5vp FOREIGN KEY (processor_definition_id) REFERENCES action_processor_definitions(id);

ALTER TABLE action_run_dismissals
    ADD CONSTRAINT fkyjhsd5pbqc9uqwj2g1ylmsxi FOREIGN KEY (run_id) REFERENCES action_runs(id);

ALTER TABLE action_run_log_events
    ADD CONSTRAINT fk7u5ypat15hcygyio21hgyeoa4 FOREIGN KEY (run_id) REFERENCES action_runs(id);

ALTER TABLE action_run_page_results
    ADD CONSTRAINT fk_action_run_page_results_run FOREIGN KEY (run_id) REFERENCES action_runs(id) ON DELETE CASCADE;

ALTER TABLE action_runs
    ADD CONSTRAINT action_runs_workflow_run_id_fkey FOREIGN KEY (workflow_run_id) REFERENCES action_workflow_runs(id) ON DELETE SET NULL;

ALTER TABLE action_runs
    ADD CONSTRAINT fk5t43bap200rjy7368fssv9a41 FOREIGN KEY (processor_definition_id) REFERENCES action_processor_definitions(id);

ALTER TABLE action_training_inputs
    ADD CONSTRAINT fk_action_training_inputs_run FOREIGN KEY (run_id) REFERENCES action_runs(id) ON DELETE CASCADE;

ALTER TABLE action_workflow_run_dismissals
    ADD CONSTRAINT action_workflow_run_dismissals_run_id_fkey FOREIGN KEY (run_id) REFERENCES action_workflow_runs(id) ON DELETE CASCADE;

ALTER TABLE action_workspace_execute_roles
    ADD CONSTRAINT action_workspace_execute_roles_processor_definition_id_fkey FOREIGN KEY (processor_definition_id) REFERENCES action_processor_definitions(id) ON DELETE CASCADE;

ALTER TABLE codec_characters
    ADD CONSTRAINT fkbktsgql1vp55r44rqf85g1i4c FOREIGN KEY (codec_id) REFERENCES codecs(id);

ALTER TABLE codec_tags
    ADD CONSTRAINT fkligpbqb7tiwr38y6q9xbsm3tg FOREIGN KEY (codec_id) REFERENCES codecs(id);

ALTER TABLE codecs
    ADD CONSTRAINT fkqwe18yj3fm0759tow5oe13nw3 FOREIGN KEY (library_id) REFERENCES libraries(id);

ALTER TABLE controlled_dictionaries
    ADD CONSTRAINT fkpx720pcupk3fxncmjale9hxa9 FOREIGN KEY (library_id) REFERENCES libraries(id);

ALTER TABLE controlled_dictionary_entries
    ADD CONSTRAINT fkgf2f2tsjbcg0hv9mbfdhm4scx FOREIGN KEY (dictionary_id) REFERENCES controlled_dictionaries(id);

ALTER TABLE controlled_dictionary_tags
    ADD CONSTRAINT fkg5o4d12y8agy0di6ll2t62l23 FOREIGN KEY (dictionary_id) REFERENCES controlled_dictionaries(id);

ALTER TABLE dataset_item_copy_files
    ADD CONSTRAINT fkq2hl6ont9tcya3ef5bp2et87f FOREIGN KEY (dataset_item_id) REFERENCES dataset_items(id) ON DELETE CASCADE;

ALTER TABLE dataset_item_copy_xml_versions
    ADD CONSTRAINT fk9ggdnob9nn6tirh4td86jl6qc FOREIGN KEY (copy_file_id) REFERENCES dataset_item_copy_files(id) ON DELETE CASCADE;

ALTER TABLE dataset_item_source_images
    ADD CONSTRAINT fk40averrc0rfqcxca6x82rxyro FOREIGN KEY (dataset_item_id) REFERENCES dataset_items(id);

ALTER TABLE dataset_item_tags
    ADD CONSTRAINT fk61xbuj1bbb2ywvj18tlxty7o7 FOREIGN KEY (dataset_item_id) REFERENCES dataset_items(id);

ALTER TABLE dataset_items
    ADD CONSTRAINT fk9ngqhlbd9bf1iegiywp80yfi8 FOREIGN KEY (dataset_id) REFERENCES datasets(id) ON DELETE CASCADE;

ALTER TABLE dataset_releases
    ADD CONSTRAINT fkt5cdjisipcedtgjfx5c8f0aid FOREIGN KEY (dataset_id) REFERENCES datasets(id) ON DELETE CASCADE;

ALTER TABLE dataset_stratify_tags
    ADD CONSTRAINT fkl28owy80np5c253ihpfjwwtsr FOREIGN KEY (dataset_id) REFERENCES datasets(id);

ALTER TABLE dataset_tags
    ADD CONSTRAINT fk8o0wghadnunvptkaaviytf0uj FOREIGN KEY (dataset_id) REFERENCES datasets(id);

ALTER TABLE iiif_import_job_items
    ADD CONSTRAINT fk_iiif_import_job_item_job FOREIGN KEY (job_id) REFERENCES iiif_import_jobs(id) ON DELETE CASCADE;

ALTER TABLE keyboard_items
    ADD CONSTRAINT fk78q8lmr0eipvqqoc1hqm4xm2f FOREIGN KEY (virtual_keyboard_id) REFERENCES virtual_keyboards(id);

ALTER TABLE label_set_tags
    ADD CONSTRAINT fkin0f832uynu76os7uo9isyisb FOREIGN KEY (label_set_id) REFERENCES label_sets(id);

ALTER TABLE normalization_profile_replacement_rules
    ADD CONSTRAINT fk398dwj70km4gm1frtmaedj1g4 FOREIGN KEY (normalization_profile_id) REFERENCES normalization_profiles(id);

ALTER TABLE normalization_profile_tags
    ADD CONSTRAINT fkraoxcdl3kbcpjd6enka6mc4t FOREIGN KEY (normalization_profile_id) REFERENCES normalization_profiles(id);

ALTER TABLE page_confidence_index
    ADD CONSTRAINT fks033wea6nvsg38mboereo8htm FOREIGN KEY (page_id) REFERENCES pages(id) ON DELETE CASCADE;

ALTER TABLE page_images
    ADD CONSTRAINT fkqwjoh1h3v04a3lf9unkyxamb3 FOREIGN KEY (page_id) REFERENCES pages(id) ON DELETE CASCADE;

ALTER TABLE page_label_index
    ADD CONSTRAINT fkkk70oasah0mv6giwtww4i0ufx FOREIGN KEY (page_id) REFERENCES pages(id) ON DELETE CASCADE;

ALTER TABLE page_tags
    ADD CONSTRAINT fk1k4tyfqy8sol5t7yfeghpx9d7 FOREIGN KEY (page_id) REFERENCES pages(id);

ALTER TABLE page_text_content
    ADD CONSTRAINT fkk1avgqv49m6c7kww6u6wrcxpc FOREIGN KEY (page_id) REFERENCES pages(id) ON DELETE CASCADE;

ALTER TABLE page_workflow_events
    ADD CONSTRAINT page_workflow_events_page_id_fkey FOREIGN KEY (page_id) REFERENCES pages(id) ON DELETE CASCADE;

ALTER TABLE page_xml_attribute_index
    ADD CONSTRAINT fk_page_xml_attribute_index_page FOREIGN KEY (page_id) REFERENCES pages(id) ON DELETE CASCADE;

ALTER TABLE page_xml_versions
    ADD CONSTRAINT fksw3lu90ato89wqmjdw754f8q4 FOREIGN KEY (page_xml_id) REFERENCES page_xmls(id) ON DELETE CASCADE;

ALTER TABLE page_xmls
    ADD CONSTRAINT fksjbtwcgy4bhvrticwk3tf98hg FOREIGN KEY (page_id) REFERENCES pages(id) ON DELETE CASCADE;

ALTER TABLE pages
    ADD CONSTRAINT fk9ao2h9hn2vi6f653wg9wwlinv FOREIGN KEY (project_id) REFERENCES projects(id);

ALTER TABLE personal_workspaces
    ADD CONSTRAINT fk13j5o86kea3vgouecm8dmouo5 FOREIGN KEY (tag_set_id) REFERENCES tag_sets(id);

ALTER TABLE personal_workspaces
    ADD CONSTRAINT fkce10w6rsdy6yunkleojcsui6d FOREIGN KEY (label_set_id) REFERENCES label_sets(id);

ALTER TABLE personal_workspaces
    ADD CONSTRAINT fki47xwqrys2nkjltqxddagrdrf FOREIGN KEY (codec_id) REFERENCES codecs(id);

ALTER TABLE personal_workspaces
    ADD CONSTRAINT fkln1kfe59tro1b9xm9ffgx6xrk FOREIGN KEY (validation_ruleset_id) REFERENCES validation_rulesets(id);

ALTER TABLE personal_workspaces
    ADD CONSTRAINT fknur63txuk9ok87f2msudy2bup FOREIGN KEY (normalization_profile_id) REFERENCES normalization_profiles(id);

ALTER TABLE personal_workspaces
    ADD CONSTRAINT fkq6akapsit3n20oufy0gfo9xki FOREIGN KEY (dictionary_id) REFERENCES controlled_dictionaries(id);

ALTER TABLE project_package_releases
    ADD CONSTRAINT fkdnk0of09up1wmf82pd4lbjf6a FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE;

ALTER TABLE project_stars
    ADD CONSTRAINT fk5ixxvajjut9jxwui4vku148ni FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE;

ALTER TABLE project_tags
    ADD CONSTRAINT fkra1vi3p19o2pqtm3c1geaose9 FOREIGN KEY (project_id) REFERENCES projects(id);

ALTER TABLE project_transfer_requests
    ADD CONSTRAINT fkjx13xpfj2u63uq4miw6kl909h FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE;

ALTER TABLE projects
    ADD CONSTRAINT fk291wrcfll60sp2jnpelfkc4fe FOREIGN KEY (tag_set_id) REFERENCES tag_sets(id);

ALTER TABLE projects
    ADD CONSTRAINT fk3ppk95wobcbf2k3alkjb1efn6 FOREIGN KEY (normalization_profile_id) REFERENCES normalization_profiles(id);

ALTER TABLE projects
    ADD CONSTRAINT fk8aroq4472uvwtstfpf0cf7tie FOREIGN KEY (dictionary_id) REFERENCES controlled_dictionaries(id);

ALTER TABLE projects
    ADD CONSTRAINT fk9wqr4tos539y8b5wklfle5ufh FOREIGN KEY (library_id) REFERENCES libraries(id);

ALTER TABLE projects
    ADD CONSTRAINT fkdp0h5yu85a4kgti9i39qps1c5 FOREIGN KEY (label_set_id) REFERENCES label_sets(id);

ALTER TABLE projects
    ADD CONSTRAINT fkmf4l87a7yw7daowr83f4hl1sr FOREIGN KEY (virtual_keyboard_id) REFERENCES virtual_keyboards(id);

ALTER TABLE projects
    ADD CONSTRAINT fkri3rj35o889tn6lm6hd1d4366 FOREIGN KEY (validation_ruleset_id) REFERENCES validation_rulesets(id);

ALTER TABLE projects
    ADD CONSTRAINT fkt3ak6j34eulslrwqxwrhon2kk FOREIGN KEY (codec_id) REFERENCES codecs(id);

ALTER TABLE recent_projects
    ADD CONSTRAINT fk8d9tckw5a9y33orn53kstknn9 FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE;

ALTER TABLE tag_set_meta_tags
    ADD CONSTRAINT fk28vxy8xatkltspk6acu0lfhbt FOREIGN KEY (tag_set_id) REFERENCES tag_sets(id);

ALTER TABLE task_assignees
    ADD CONSTRAINT fks0jy5sv972lpa2wfx95m7xebb FOREIGN KEY (task_id) REFERENCES tasks(id);

ALTER TABLE task_comment_mentions
    ADD CONSTRAINT fk3ec6o104g8q573ptmfyso0x5r FOREIGN KEY (comment_id) REFERENCES task_comments(id);

ALTER TABLE team_workspaces
    ADD CONSTRAINT fk1o9v84cnuyj7a9al2u8ajrmbg FOREIGN KEY (dictionary_id) REFERENCES controlled_dictionaries(id);

ALTER TABLE team_workspaces
    ADD CONSTRAINT fk9x3w4ojiknwaq4qsfto4snsus FOREIGN KEY (tag_set_id) REFERENCES tag_sets(id);

ALTER TABLE team_workspaces
    ADD CONSTRAINT fka0b8hocc8f5m2lk57agjtoq8s FOREIGN KEY (normalization_profile_id) REFERENCES normalization_profiles(id);

ALTER TABLE team_workspaces
    ADD CONSTRAINT fkbafyfebr2usx0hdgmvql2r01h FOREIGN KEY (codec_id) REFERENCES codecs(id);

ALTER TABLE team_workspaces
    ADD CONSTRAINT fklf9h85xbo29hdvj8lctouhf6d FOREIGN KEY (validation_ruleset_id) REFERENCES validation_rulesets(id);

ALTER TABLE team_workspaces
    ADD CONSTRAINT fkpcqwkdjghrg5t9vjw3129973b FOREIGN KEY (label_set_id) REFERENCES label_sets(id);

ALTER TABLE upload_session_files
    ADD CONSTRAINT fkdvaapsma841ok5t9vrfsfokr1 FOREIGN KEY (session_id) REFERENCES upload_sessions(id);

ALTER TABLE validation_ruleset_tags
    ADD CONSTRAINT fkh1evd0cf5kjbm9060fehy06fx FOREIGN KEY (validation_ruleset_id) REFERENCES validation_rulesets(id);

ALTER TABLE virtual_keyboard_tags
    ADD CONSTRAINT fkpnyggx3yhlr4f9oju8eg3oc6s FOREIGN KEY (virtual_keyboard_id) REFERENCES virtual_keyboards(id);

INSERT INTO avatar_runtime_settings (id, default_style, updated_at, updated_by_user_id) VALUES (1, 'GRADIENT', NULL, NULL);
