--
-- PostgreSQL database dump
--

-- Dumped from database version 18.2
-- Dumped by pg_dump version 18.2

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: pg_trgm; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: action_audit_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.action_audit_events (
    created timestamp(6) without time zone NOT NULL,
    outcome character varying(32) NOT NULL,
    action character varying(64) NOT NULL,
    actor_user_id character varying(255),
    details_json text,
    id character varying(255) NOT NULL,
    processor_definition_id character varying(255),
    project_id character varying(255),
    run_id character varying(255),
    workspace_id character varying(255)
);


--
-- Name: action_processor_assignments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.action_processor_assignments (
    enabled boolean NOT NULL,
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    created_by_user_id character varying(255) NOT NULL,
    id character varying(255) NOT NULL,
    processor_definition_id character varying(255) NOT NULL,
    project_id character varying(255),
    workspace_id character varying(255) NOT NULL
);


--
-- Name: action_processor_definitions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.action_processor_definitions (
    accepts_images boolean NOT NULL,
    accepts_xml boolean NOT NULL,
    enabled boolean NOT NULL,
    endpoint_timeout_seconds integer NOT NULL,
    global_available boolean DEFAULT false NOT NULL,
    outputs_images boolean NOT NULL,
    outputs_xml boolean NOT NULL,
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    category character varying(32) DEFAULT 'WORKFLOW'::character varying NOT NULL,
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
    CONSTRAINT action_processor_definitions_category_check CHECK (((category)::text = ANY ((ARRAY['WORKFLOW'::character varying, 'OCR_HTR'::character varying, 'LAYOUT'::character varying])::text[]))),
    CONSTRAINT action_processor_definitions_execute_role_check CHECK (((execute_role)::text = ANY ((ARRAY['EDITOR'::character varying, 'CURATOR'::character varying])::text[]))),
    CONSTRAINT action_processor_definitions_lock_mode_check CHECK (((lock_mode)::text = ANY ((ARRAY['PAGES'::character varying, 'PROJECT'::character varying])::text[])))
);


--
-- Name: action_processor_workspace_availability; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.action_processor_workspace_availability (
    enabled boolean NOT NULL,
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    created_by_user_id character varying(255) CONSTRAINT action_processor_workspace_availabi_created_by_user_id_not_null NOT NULL,
    id character varying(255) NOT NULL,
    processor_definition_id character varying(255) CONSTRAINT action_processor_workspace_ava_processor_definition_id_not_null NOT NULL,
    workspace_id character varying(255) NOT NULL
);


--
-- Name: action_run_dismissals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.action_run_dismissals (
    created timestamp(6) without time zone NOT NULL,
    id character varying(255) NOT NULL,
    run_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL
);


--
-- Name: action_run_log_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.action_run_log_events (
    created timestamp(6) without time zone NOT NULL,
    level character varying(16) NOT NULL,
    id character varying(255) NOT NULL,
    message text NOT NULL,
    run_id character varying(255) NOT NULL
);


--
-- Name: action_runs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.action_runs (
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
    project_id character varying(255) NOT NULL,
    public_api_base_url text,
    result_summary_json text,
    status character varying(255) NOT NULL,
    status_message text,
    target_selection_json text,
    workspace_id character varying(255) NOT NULL,
    CONSTRAINT action_runs_lock_mode_check CHECK (((lock_mode)::text = ANY ((ARRAY['PAGES'::character varying, 'PROJECT'::character varying])::text[]))),
    CONSTRAINT action_runs_status_check CHECK (((status)::text = ANY ((ARRAY['QUEUED'::character varying, 'PENDING'::character varying, 'DISPATCHING'::character varying, 'RUNNING'::character varying, 'IMPORTING_RESULTS'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'CANCEL_REQUESTED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: admin_user_audit_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admin_user_audit_logs (
    created timestamp(6) without time zone NOT NULL,
    action character varying(255) NOT NULL,
    actor_user_id character varying(255) NOT NULL,
    actor_username character varying(255) NOT NULL,
    details text,
    id character varying(255) NOT NULL,
    outcome character varying(255) NOT NULL,
    target_user_id character varying(255),
    target_username character varying(255),
    CONSTRAINT admin_user_audit_logs_action_check CHECK (((action)::text = ANY ((ARRAY['CREATE'::character varying, 'ENABLE'::character varying, 'DISABLE'::character varying, 'RESEND_SETUP_EMAIL'::character varying, 'GLOBAL_CURATOR_GRANT'::character varying, 'GLOBAL_CURATOR_REVOKE'::character varying])::text[]))),
    CONSTRAINT admin_user_audit_logs_outcome_check CHECK (((outcome)::text = ANY ((ARRAY['SUCCESS'::character varying, 'FAILURE'::character varying])::text[])))
);


--
-- Name: codec_characters; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.codec_characters (
    character_value text,
    codec_id character varying(255) NOT NULL
);


--
-- Name: codec_tags; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.codec_tags (
    codec_id character varying(255) NOT NULL,
    tag_value text
);


--
-- Name: codecs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.codecs (
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    description text,
    id character varying(255) NOT NULL,
    library_id character varying(255) NOT NULL,
    name character varying(255) NOT NULL
);


--
-- Name: controlled_dictionaries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.controlled_dictionaries (
    case_sensitive boolean NOT NULL,
    locked boolean NOT NULL,
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    unicode_normalization character varying(32) NOT NULL,
    description text,
    id character varying(255) NOT NULL,
    library_id character varying(255) NOT NULL,
    name character varying(255) NOT NULL
);


--
-- Name: controlled_dictionary_entries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.controlled_dictionary_entries (
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    dictionary_id character varying(255) NOT NULL,
    id character varying(255) NOT NULL,
    metadata_json text,
    normalized_value text NOT NULL,
    source_entry_key character varying(255),
    surface_form text NOT NULL
);


--
-- Name: controlled_dictionary_tags; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.controlled_dictionary_tags (
    dictionary_id character varying(255) NOT NULL,
    tag_value text
);


--
-- Name: dataset_item_copy_files; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dataset_item_copy_files (
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
    CONSTRAINT dataset_item_copy_files_kind_check CHECK (((kind)::text = ANY ((ARRAY['XML'::character varying, 'IMAGE'::character varying])::text[])))
);


--
-- Name: dataset_item_copy_xml_versions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dataset_item_copy_xml_versions (
    version_number integer NOT NULL,
    created timestamp(6) without time zone NOT NULL,
    file_size bigint,
    comment character varying(500),
    copy_file_id character varying(255) NOT NULL,
    file_path character varying(255) NOT NULL,
    id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL
);


--
-- Name: dataset_item_source_images; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dataset_item_source_images (
    dataset_item_id character varying(255) NOT NULL,
    source_image_id character varying(255)
);


--
-- Name: dataset_item_tags; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dataset_item_tags (
    dataset_item_id character varying(255) NOT NULL,
    tag character varying(255)
);


--
-- Name: dataset_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dataset_items (
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
    CONSTRAINT dataset_items_assigned_split_check CHECK (((assigned_split)::text = ANY ((ARRAY['TRAIN'::character varying, 'VAL'::character varying, 'TEST'::character varying])::text[]))),
    CONSTRAINT dataset_items_mode_check CHECK (((mode)::text = ANY ((ARRAY['LINK'::character varying, 'COPY'::character varying])::text[]))),
    CONSTRAINT dataset_items_status_check CHECK (((status)::text = ANY ((ARRAY['READY'::character varying, 'BROKEN'::character varying])::text[])))
);


--
-- Name: dataset_releases; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dataset_releases (
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
    CONSTRAINT dataset_releases_status_check CHECK (((status)::text = ANY ((ARRAY['CREATING'::character varying, 'READY'::character varying, 'FAILED'::character varying])::text[]))),
    CONSTRAINT dataset_releases_validation_status_check CHECK (((validation_status)::text = ANY ((ARRAY['NOT_VALIDATED'::character varying, 'VALID'::character varying, 'INVALID'::character varying])::text[])))
);


--
-- Name: dataset_stratify_tags; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dataset_stratify_tags (
    dataset_id character varying(255) NOT NULL,
    tag_id character varying(255)
);


--
-- Name: dataset_tags; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dataset_tags (
    dataset_id character varying(255) NOT NULL,
    tag character varying(255)
);


--
-- Name: datasets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.datasets (
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
    CONSTRAINT datasets_last_export_status_check CHECK (((last_export_status)::text = ANY ((ARRAY['NEVER_EXPORTED'::character varying, 'READY'::character varying, 'FAILED'::character varying])::text[]))),
    CONSTRAINT datasets_last_validation_status_check CHECK (((last_validation_status)::text = ANY ((ARRAY['NOT_VALIDATED'::character varying, 'VALID'::character varying, 'INVALID'::character varying])::text[]))),
    CONSTRAINT datasets_split_algorithm_check CHECK (((split_algorithm)::text = ANY ((ARRAY['RANDOM_SEEDED'::character varying, 'GROUP_BY_SOURCE_PROJECT'::character varying, 'MULTILABEL_STRATIFIED_BY_TAGS'::character varying])::text[]))),
    CONSTRAINT datasets_split_template_check CHECK (((split_template)::text = ANY ((ARRAY['TRAIN_VAL'::character varying, 'TRAIN_VAL_TEST'::character varying])::text[])))
);


--
-- Name: editor_preferences; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.editor_preferences (
    auto_select boolean,
    background_opacity double precision,
    canvas_text_correction_overlay_snap_to_line boolean,
    canvas_text_correction_overlay_x_ratio double precision,
    canvas_text_correction_overlay_y_ratio double precision,
    canvas_text_correction_zoom double precision,
    constrain_to_image boolean,
    constrain_to_parent boolean,
    cut_min_area_threshold double precision,
    highlight_unknown_codec_chars boolean,
    left_collapsed boolean,
    left_width_px integer,
    move_with_children boolean,
    onboarding_dashboard_tour_version integer,
    onboarding_editor_tour_version integer,
    onboarding_tours_opted_out boolean,
    prevent_overlap_on_create boolean,
    right_collapsed boolean,
    right_width_px integer,
    show_polygon_label_fill boolean,
    text_view_cutout_height integer,
    text_view_font_size integer,
    text_view_padding integer,
    toolbar_compact boolean,
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    background_color character varying(255),
    default_line_width character varying(255),
    id character varying(255) NOT NULL,
    text_item_layout character varying(255),
    text_mode_submode character varying(255),
    toolbar_layout character varying(255),
    user_id character varying(255) NOT NULL,
    onboarding_tour_completion jsonb,
    shortcut_bindings jsonb,
    table_column_visibility jsonb
);


--
-- Name: error_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.error_events (
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
    CONSTRAINT error_events_severity_check CHECK (((severity)::text = ANY ((ARRAY['WARN'::character varying, 'ERROR'::character varying])::text[])))
);


--
-- Name: iiif_import_jobs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.iiif_import_jobs (
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
    CONSTRAINT iiif_import_jobs_source_type_check CHECK (((source_type)::text = ANY ((ARRAY['MANIFEST_URL'::character varying, 'MANIFEST_FILE'::character varying])::text[]))),
    CONSTRAINT iiif_import_jobs_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'IMPORTING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: import_jobs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.import_jobs (
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
    CONSTRAINT import_jobs_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'SCANNING'::character varying, 'VALIDATING'::character varying, 'IMPORTING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: keyboard_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.keyboard_items (
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
    virtual_keyboard_id character varying(255)
);


--
-- Name: keyboard_items_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.keyboard_items ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.keyboard_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: label_set_tags; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.label_set_tags (
    label_set_id character varying(255) NOT NULL,
    tag character varying(255) NOT NULL
);


--
-- Name: label_sets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.label_sets (
    is_system boolean NOT NULL,
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    description text,
    id character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    workspace_id character varying(255) NOT NULL,
    definition jsonb NOT NULL
);


--
-- Name: libraries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.libraries (
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    id character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    workspace_id character varying(255) NOT NULL
);


--
-- Name: normalization_profile_replacement_rules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.normalization_profile_replacement_rules (
    regex_rule boolean NOT NULL,
    rule_order integer NOT NULL,
    normalization_profile_id character varying(255) CONSTRAINT normalization_profile_replace_normalization_profile_id_not_null NOT NULL,
    replacement_value text CONSTRAINT normalization_profile_replacement_ru_replacement_value_not_null NOT NULL,
    search_value text NOT NULL
);


--
-- Name: normalization_profile_tags; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.normalization_profile_tags (
    normalization_profile_id character varying(255) NOT NULL,
    tag character varying(255) NOT NULL
);


--
-- Name: normalization_profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.normalization_profiles (
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
    workspace_id character varying(255) NOT NULL
);


--
-- Name: notification_preferences; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_preferences (
    desktop_enabled boolean NOT NULL,
    email_enabled boolean NOT NULL,
    in_app_enabled boolean NOT NULL,
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    id character varying(255) NOT NULL,
    notification_type character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    CONSTRAINT notification_preferences_notification_type_check CHECK (((notification_type)::text = ANY ((ARRAY['WORKSPACE_INVITATION'::character varying, 'TASK_ASSIGNED'::character varying, 'TASK_COMPLETED'::character varying, 'TASK_REMINDER'::character varying, 'TASK_MENTIONED'::character varying, 'TASK_UPDATED'::character varying, 'TASK_DUE_SOON'::character varying, 'TASK_OVERDUE'::character varying, 'TASK_COMMENT_ADDED'::character varying, 'PROJECT_CREATED'::character varying, 'PROJECT_DELETED'::character varying, 'PAGE_CREATED'::character varying, 'PAGE_DELETED'::character varying, 'WORKSPACE_WATCH'::character varying, 'PROJECT_WATCH'::character varying, 'UPLOAD_COMPLETED'::character varying, 'UPLOAD_FAILED'::character varying, 'IMPORT_COMPLETED'::character varying, 'IMPORT_FAILED'::character varying, 'COLLAB_TAKEOVER_REQUESTED'::character varying, 'COLLAB_TAKEOVER_GRANTED'::character varying, 'COLLAB_TAKEOVER_DECLINED'::character varying, 'COLLAB_TAKEOVER_FORCED'::character varying, 'COLLAB_LEASE_EXPIRED'::character varying])::text[])))
);


--
-- Name: notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notifications (
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
    CONSTRAINT notifications_type_check CHECK (((type)::text = ANY ((ARRAY['WORKSPACE_INVITATION'::character varying, 'TASK_ASSIGNED'::character varying, 'TASK_COMPLETED'::character varying, 'TASK_REMINDER'::character varying, 'TASK_MENTIONED'::character varying, 'TASK_UPDATED'::character varying, 'TASK_DUE_SOON'::character varying, 'TASK_OVERDUE'::character varying, 'TASK_COMMENT_ADDED'::character varying, 'PROJECT_CREATED'::character varying, 'PROJECT_DELETED'::character varying, 'PAGE_CREATED'::character varying, 'PAGE_DELETED'::character varying, 'WORKSPACE_WATCH'::character varying, 'PROJECT_WATCH'::character varying, 'UPLOAD_COMPLETED'::character varying, 'UPLOAD_FAILED'::character varying, 'IMPORT_COMPLETED'::character varying, 'IMPORT_FAILED'::character varying, 'COLLAB_TAKEOVER_REQUESTED'::character varying, 'COLLAB_TAKEOVER_GRANTED'::character varying, 'COLLAB_TAKEOVER_DECLINED'::character varying, 'COLLAB_TAKEOVER_FORCED'::character varying, 'COLLAB_LEASE_EXPIRED'::character varying])::text[])))
);


--
-- Name: page_confidence_index; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.page_confidence_index (
    confidence double precision NOT NULL,
    created timestamp(6) without time zone NOT NULL,
    element_id character varying(255),
    element_type character varying(255) NOT NULL,
    id character varying(255) NOT NULL,
    page_id character varying(255) NOT NULL,
    CONSTRAINT page_confidence_index_element_type_check CHECK (((element_type)::text = ANY ((ARRAY['PAGE'::character varying, 'COORDS'::character varying, 'TEXTEQUIV'::character varying, 'READING_ORDER'::character varying, 'BASELINE'::character varying, 'ALTERNATIVE_IMAGE'::character varying])::text[])))
);


--
-- Name: page_images; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.page_images (
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
    variant character varying(255) NOT NULL
);


--
-- Name: page_label_index; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.page_label_index (
    created timestamp(6) without time zone NOT NULL,
    element_id character varying(255) NOT NULL,
    element_type character varying(255) NOT NULL,
    id character varying(255) NOT NULL,
    label_id character varying(255) NOT NULL,
    page_id character varying(255) NOT NULL,
    CONSTRAINT page_label_index_element_type_check CHECK (((element_type)::text = ANY ((ARRAY['REGION'::character varying, 'LINE'::character varying])::text[])))
);


--
-- Name: page_tags; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.page_tags (
    page_id character varying(255) NOT NULL,
    tag character varying(255)
);


--
-- Name: page_text_content; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.page_text_content (
    comment_entry boolean NOT NULL,
    variant_index integer,
    created timestamp(6) without time zone NOT NULL,
    id character varying(255) NOT NULL,
    normalized_text text,
    page_id character varying(255) NOT NULL,
    region_id character varying(255),
    text_content text,
    text_line_id character varying(255),
    search_vector tsvector
);


--
-- Name: page_xml_versions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.page_xml_versions (
    version_number integer NOT NULL,
    created timestamp(6) without time zone NOT NULL,
    file_size bigint,
    comment character varying(500),
    file_path character varying(255) NOT NULL,
    id character varying(255) NOT NULL,
    page_xml_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL
);


--
-- Name: page_xmls; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.page_xmls (
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
    CONSTRAINT page_xmls_schema_check CHECK (((schema)::text = ANY ((ARRAY['PAGE_XML'::character varying, 'ALTO_XML'::character varying, 'UNKNOWN'::character varying])::text[])))
);


--
-- Name: pages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pages (
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
    project_id character varying(255) NOT NULL
);


--
-- Name: personal_workspaces; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.personal_workspaces (
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
    validation_ruleset_id character varying(255)
);


--
-- Name: project_package_releases; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.project_package_releases (
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
    CONSTRAINT project_package_releases_status_check CHECK (((status)::text = ANY ((ARRAY['CREATING'::character varying, 'READY'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: project_stars; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.project_stars (
    created timestamp(6) without time zone NOT NULL,
    id character varying(255) NOT NULL,
    project_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL
);


--
-- Name: project_tags; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.project_tags (
    project_id character varying(255) NOT NULL,
    tag character varying(255)
);


--
-- Name: project_transfer_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.project_transfer_requests (
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
    CONSTRAINT project_transfer_requests_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT project_transfer_requests_transfer_type_check CHECK (((transfer_type)::text = ANY ((ARRAY['MOVE'::character varying, 'COPY'::character varying])::text[])))
);


--
-- Name: projects; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.projects (
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
    virtual_keyboard_id character varying(255)
);


--
-- Name: recent_projects; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.recent_projects (
    created timestamp(6) without time zone NOT NULL,
    last_accessed timestamp(6) without time zone NOT NULL,
    access_type character varying(255) NOT NULL,
    id character varying(255) NOT NULL,
    project_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    CONSTRAINT recent_projects_access_type_check CHECK (((access_type)::text = ANY ((ARRAY['CREATED'::character varying, 'VIEWED'::character varying, 'EDITED'::character varying])::text[])))
);


--
-- Name: resource_transfer_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.resource_transfer_requests (
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
    CONSTRAINT resource_transfer_requests_resource_type_check CHECK (((resource_type)::text = ANY ((ARRAY['CODEC'::character varying, 'DICTIONARY'::character varying, 'VIRTUAL_KEYBOARD'::character varying, 'LABEL_SET'::character varying, 'TAG_SET'::character varying, 'NORMALIZATION_PROFILE'::character varying, 'VALIDATION_RULESET'::character varying])::text[]))),
    CONSTRAINT resource_transfer_requests_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT resource_transfer_requests_transfer_type_check CHECK (((transfer_type)::text = ANY ((ARRAY['MOVE'::character varying, 'COPY'::character varying])::text[])))
);


--
-- Name: search_lexicon_entries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.search_lexicon_entries (
    occurrence_count integer NOT NULL,
    id character varying(255) NOT NULL,
    normalized_token character varying(255) NOT NULL,
    project_id character varying(255) NOT NULL,
    workspace_id character varying(255) NOT NULL
);


--
-- Name: stored_files; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stored_files (
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
    project_id character varying(255) NOT NULL,
    workspace_id character varying(255) NOT NULL,
    CONSTRAINT stored_files_file_type_check CHECK (((file_type)::text = ANY ((ARRAY['IMG'::character varying, 'XML'::character varying, 'THUMB'::character varying])::text[]))),
    CONSTRAINT stored_files_status_check CHECK (((status)::text = ANY ((ARRAY['READY'::character varying, 'DELETED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: subtasks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.subtasks (
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
    title character varying(255) NOT NULL
);


--
-- Name: tag_set_meta_tags; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tag_set_meta_tags (
    tag character varying(255) NOT NULL,
    tag_set_id character varying(255) NOT NULL
);


--
-- Name: tag_sets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tag_sets (
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    description text,
    id character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    workspace_id character varying(255) NOT NULL,
    definition jsonb NOT NULL
);


--
-- Name: task_activity_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.task_activity_logs (
    created timestamp(6) without time zone NOT NULL,
    activity_type character varying(255) NOT NULL,
    details text,
    id character varying(255) NOT NULL,
    new_value text,
    old_value text,
    task_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    CONSTRAINT task_activity_logs_activity_type_check CHECK (((activity_type)::text = ANY ((ARRAY['CREATED'::character varying, 'TITLE_CHANGED'::character varying, 'DESCRIPTION_CHANGED'::character varying, 'STATUS_CHANGED'::character varying, 'PRIORITY_CHANGED'::character varying, 'DUE_DATE_CHANGED'::character varying, 'ASSIGNEES_CHANGED'::character varying, 'COMMENT_ADDED'::character varying, 'COMMENT_UPDATED'::character varying, 'COMMENT_DELETED'::character varying, 'SUBTASK_ADDED'::character varying, 'SUBTASK_COMPLETED'::character varying, 'SUBTASK_DELETED'::character varying, 'LINK_ADDED'::character varying, 'LINK_REMOVED'::character varying])::text[])))
);


--
-- Name: task_assignees; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.task_assignees (
    task_id character varying(255) NOT NULL,
    user_id character varying(255)
);


--
-- Name: task_comment_mentions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.task_comment_mentions (
    comment_id character varying(255) NOT NULL,
    user_id character varying(255)
);


--
-- Name: task_comments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.task_comments (
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    content text NOT NULL,
    id character varying(255) NOT NULL,
    task_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL
);


--
-- Name: task_page_links; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.task_page_links (
    created timestamp(6) without time zone NOT NULL,
    created_by_user_id character varying(255),
    id character varying(255) NOT NULL,
    link_type character varying(255) NOT NULL,
    page_id character varying(255) NOT NULL,
    tag_filter character varying(255),
    task_id character varying(255) NOT NULL,
    CONSTRAINT task_page_links_link_type_check CHECK (((link_type)::text = ANY ((ARRAY['MANUAL'::character varying, 'BY_TAG'::character varying])::text[])))
);


--
-- Name: task_project_links; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.task_project_links (
    created timestamp(6) without time zone NOT NULL,
    created_by_user_id character varying(255),
    id character varying(255) NOT NULL,
    project_id character varying(255) NOT NULL,
    task_id character varying(255) NOT NULL
);


--
-- Name: task_reminders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.task_reminders (
    sent boolean NOT NULL,
    created timestamp(6) without time zone NOT NULL,
    reminder_time timestamp(6) without time zone NOT NULL,
    id character varying(255) NOT NULL,
    task_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL
);


--
-- Name: tasks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tasks (
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
    CONSTRAINT tasks_priority_check CHECK (((priority)::text = ANY ((ARRAY['LOW'::character varying, 'MEDIUM'::character varying, 'HIGH'::character varying, 'URGENT'::character varying])::text[]))),
    CONSTRAINT tasks_status_check CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'IN_PROGRESS'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: team_workspaces; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.team_workspaces (
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
    validation_ruleset_id character varying(255)
);


--
-- Name: upload_session_files; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.upload_session_files (
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
    CONSTRAINT upload_session_files_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'UPLOADING'::character varying, 'UPLOADED'::character varying, 'PROCESSING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'CONFLICT'::character varying, 'SKIPPED'::character varying])::text[])))
);


--
-- Name: upload_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.upload_sessions (
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
    CONSTRAINT upload_sessions_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'UPLOADING'::character varying, 'PROCESSING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: validation_ruleset_tags; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.validation_ruleset_tags (
    tag character varying(255) NOT NULL,
    validation_ruleset_id character varying(255) NOT NULL
);


--
-- Name: validation_rulesets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.validation_rulesets (
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    description text,
    id character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    rules_json text NOT NULL,
    workspace_id character varying(255) NOT NULL
);


--
-- Name: virtual_keyboard_tags; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.virtual_keyboard_tags (
    tag character varying(255) NOT NULL,
    virtual_keyboard_id character varying(255) NOT NULL
);


--
-- Name: virtual_keyboards; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.virtual_keyboards (
    cols integer NOT NULL,
    rows integer NOT NULL,
    description text,
    id character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    user_id character varying(255),
    workspace_id character varying(255) NOT NULL
);


--
-- Name: workspace_members; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.workspace_members (
    created timestamp(6) without time zone NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    id character varying(255) NOT NULL,
    invitation_status character varying(255) NOT NULL,
    role character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    workspace_id character varying(255) NOT NULL,
    CONSTRAINT workspace_members_invitation_status_check CHECK (((invitation_status)::text = ANY ((ARRAY['PENDING'::character varying, 'ACCEPTED'::character varying, 'DECLINED'::character varying])::text[]))),
    CONSTRAINT workspace_members_role_check CHECK (((role)::text = ANY ((ARRAY['CURATOR'::character varying, 'EDITOR'::character varying, 'ADMINISTRATOR'::character varying, 'MEMBER'::character varying])::text[])))
);


--
-- Name: workspace_storage_quotas; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.workspace_storage_quotas (
    is_custom boolean NOT NULL,
    created timestamp(6) without time zone NOT NULL,
    current_usage_bytes bigint NOT NULL,
    quota_limit_bytes bigint NOT NULL,
    reserved_bytes bigint NOT NULL,
    updated timestamp(6) without time zone NOT NULL,
    id character varying(255) NOT NULL,
    workspace_id character varying(255) NOT NULL
);


--
-- Name: action_audit_events action_audit_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.action_audit_events
    ADD CONSTRAINT action_audit_events_pkey PRIMARY KEY (id);


--
-- Name: action_processor_assignments action_processor_assignments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.action_processor_assignments
    ADD CONSTRAINT action_processor_assignments_pkey PRIMARY KEY (id);


--
-- Name: action_processor_definitions action_processor_definitions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.action_processor_definitions
    ADD CONSTRAINT action_processor_definitions_pkey PRIMARY KEY (id);


--
-- Name: action_processor_definitions action_processor_definitions_processor_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.action_processor_definitions
    ADD CONSTRAINT action_processor_definitions_processor_key_key UNIQUE (processor_key);


--
-- Name: action_processor_workspace_availability action_processor_workspace_availability_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.action_processor_workspace_availability
    ADD CONSTRAINT action_processor_workspace_availability_pkey PRIMARY KEY (id);


--
-- Name: action_run_dismissals action_run_dismissals_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.action_run_dismissals
    ADD CONSTRAINT action_run_dismissals_pkey PRIMARY KEY (id);


--
-- Name: action_run_log_events action_run_log_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.action_run_log_events
    ADD CONSTRAINT action_run_log_events_pkey PRIMARY KEY (id);


--
-- Name: action_runs action_runs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.action_runs
    ADD CONSTRAINT action_runs_pkey PRIMARY KEY (id);


--
-- Name: admin_user_audit_logs admin_user_audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_user_audit_logs
    ADD CONSTRAINT admin_user_audit_logs_pkey PRIMARY KEY (id);


--
-- Name: codecs codecs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.codecs
    ADD CONSTRAINT codecs_pkey PRIMARY KEY (id);


--
-- Name: controlled_dictionaries controlled_dictionaries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.controlled_dictionaries
    ADD CONSTRAINT controlled_dictionaries_pkey PRIMARY KEY (id);


--
-- Name: controlled_dictionary_entries controlled_dictionary_entries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.controlled_dictionary_entries
    ADD CONSTRAINT controlled_dictionary_entries_pkey PRIMARY KEY (id);


--
-- Name: dataset_item_copy_files dataset_item_copy_files_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dataset_item_copy_files
    ADD CONSTRAINT dataset_item_copy_files_pkey PRIMARY KEY (id);


--
-- Name: dataset_item_copy_xml_versions dataset_item_copy_xml_versions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dataset_item_copy_xml_versions
    ADD CONSTRAINT dataset_item_copy_xml_versions_pkey PRIMARY KEY (id);


--
-- Name: dataset_items dataset_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dataset_items
    ADD CONSTRAINT dataset_items_pkey PRIMARY KEY (id);


--
-- Name: dataset_releases dataset_releases_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dataset_releases
    ADD CONSTRAINT dataset_releases_pkey PRIMARY KEY (id);


--
-- Name: dataset_releases dataset_releases_share_public_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dataset_releases
    ADD CONSTRAINT dataset_releases_share_public_id_key UNIQUE (share_public_id);


--
-- Name: datasets datasets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.datasets
    ADD CONSTRAINT datasets_pkey PRIMARY KEY (id);


--
-- Name: editor_preferences editor_preferences_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.editor_preferences
    ADD CONSTRAINT editor_preferences_pkey PRIMARY KEY (id);


--
-- Name: editor_preferences editor_preferences_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.editor_preferences
    ADD CONSTRAINT editor_preferences_user_id_key UNIQUE (user_id);


--
-- Name: error_events error_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.error_events
    ADD CONSTRAINT error_events_pkey PRIMARY KEY (id);


--
-- Name: iiif_import_jobs iiif_import_jobs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iiif_import_jobs
    ADD CONSTRAINT iiif_import_jobs_pkey PRIMARY KEY (id);


--
-- Name: import_jobs import_jobs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.import_jobs
    ADD CONSTRAINT import_jobs_pkey PRIMARY KEY (id);


--
-- Name: keyboard_items keyboard_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.keyboard_items
    ADD CONSTRAINT keyboard_items_pkey PRIMARY KEY (id);


--
-- Name: label_sets label_sets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.label_sets
    ADD CONSTRAINT label_sets_pkey PRIMARY KEY (id);


--
-- Name: libraries libraries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.libraries
    ADD CONSTRAINT libraries_pkey PRIMARY KEY (id);


--
-- Name: libraries libraries_workspace_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.libraries
    ADD CONSTRAINT libraries_workspace_id_key UNIQUE (workspace_id);


--
-- Name: normalization_profile_replacement_rules normalization_profile_replacement_rules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.normalization_profile_replacement_rules
    ADD CONSTRAINT normalization_profile_replacement_rules_pkey PRIMARY KEY (rule_order, normalization_profile_id);


--
-- Name: normalization_profiles normalization_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.normalization_profiles
    ADD CONSTRAINT normalization_profiles_pkey PRIMARY KEY (id);


--
-- Name: notification_preferences notification_preferences_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_preferences
    ADD CONSTRAINT notification_preferences_pkey PRIMARY KEY (id);


--
-- Name: notification_preferences notification_preferences_user_id_notification_type_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_preferences
    ADD CONSTRAINT notification_preferences_user_id_notification_type_key UNIQUE (user_id, notification_type);


--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);


--
-- Name: page_confidence_index page_confidence_index_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.page_confidence_index
    ADD CONSTRAINT page_confidence_index_pkey PRIMARY KEY (id);


--
-- Name: page_images page_images_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.page_images
    ADD CONSTRAINT page_images_pkey PRIMARY KEY (id);


--
-- Name: page_label_index page_label_index_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.page_label_index
    ADD CONSTRAINT page_label_index_pkey PRIMARY KEY (id);


--
-- Name: page_text_content page_text_content_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.page_text_content
    ADD CONSTRAINT page_text_content_pkey PRIMARY KEY (id);


--
-- Name: page_xml_versions page_xml_versions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.page_xml_versions
    ADD CONSTRAINT page_xml_versions_pkey PRIMARY KEY (id);


--
-- Name: page_xmls page_xmls_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.page_xmls
    ADD CONSTRAINT page_xmls_pkey PRIMARY KEY (id);


--
-- Name: pages pages_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pages
    ADD CONSTRAINT pages_pkey PRIMARY KEY (id);


--
-- Name: personal_workspaces personal_workspaces_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.personal_workspaces
    ADD CONSTRAINT personal_workspaces_pkey PRIMARY KEY (id);


--
-- Name: project_package_releases project_package_releases_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_package_releases
    ADD CONSTRAINT project_package_releases_pkey PRIMARY KEY (id);


--
-- Name: project_package_releases project_package_releases_share_public_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_package_releases
    ADD CONSTRAINT project_package_releases_share_public_id_key UNIQUE (share_public_id);


--
-- Name: project_stars project_stars_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_stars
    ADD CONSTRAINT project_stars_pkey PRIMARY KEY (id);


--
-- Name: project_transfer_requests project_transfer_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_transfer_requests
    ADD CONSTRAINT project_transfer_requests_pkey PRIMARY KEY (id);


--
-- Name: projects projects_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT projects_pkey PRIMARY KEY (id);


--
-- Name: recent_projects recent_projects_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recent_projects
    ADD CONSTRAINT recent_projects_pkey PRIMARY KEY (id);


--
-- Name: resource_transfer_requests resource_transfer_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.resource_transfer_requests
    ADD CONSTRAINT resource_transfer_requests_pkey PRIMARY KEY (id);


--
-- Name: search_lexicon_entries search_lexicon_entries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.search_lexicon_entries
    ADD CONSTRAINT search_lexicon_entries_pkey PRIMARY KEY (id);


--
-- Name: stored_files stored_files_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stored_files
    ADD CONSTRAINT stored_files_pkey PRIMARY KEY (uuid);


--
-- Name: stored_files stored_files_storage_path_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stored_files
    ADD CONSTRAINT stored_files_storage_path_key UNIQUE (storage_path);


--
-- Name: subtasks subtasks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subtasks
    ADD CONSTRAINT subtasks_pkey PRIMARY KEY (id);


--
-- Name: tag_sets tag_sets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tag_sets
    ADD CONSTRAINT tag_sets_pkey PRIMARY KEY (id);


--
-- Name: task_activity_logs task_activity_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.task_activity_logs
    ADD CONSTRAINT task_activity_logs_pkey PRIMARY KEY (id);


--
-- Name: task_comments task_comments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.task_comments
    ADD CONSTRAINT task_comments_pkey PRIMARY KEY (id);


--
-- Name: task_page_links task_page_links_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.task_page_links
    ADD CONSTRAINT task_page_links_pkey PRIMARY KEY (id);


--
-- Name: task_page_links task_page_links_task_id_page_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.task_page_links
    ADD CONSTRAINT task_page_links_task_id_page_id_key UNIQUE (task_id, page_id);


--
-- Name: task_project_links task_project_links_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.task_project_links
    ADD CONSTRAINT task_project_links_pkey PRIMARY KEY (id);


--
-- Name: task_project_links task_project_links_task_id_project_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.task_project_links
    ADD CONSTRAINT task_project_links_task_id_project_id_key UNIQUE (task_id, project_id);


--
-- Name: task_reminders task_reminders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.task_reminders
    ADD CONSTRAINT task_reminders_pkey PRIMARY KEY (id);


--
-- Name: tasks tasks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tasks
    ADD CONSTRAINT tasks_pkey PRIMARY KEY (id);


--
-- Name: team_workspaces team_workspaces_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.team_workspaces
    ADD CONSTRAINT team_workspaces_name_key UNIQUE (name);


--
-- Name: team_workspaces team_workspaces_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.team_workspaces
    ADD CONSTRAINT team_workspaces_pkey PRIMARY KEY (id);


--
-- Name: action_processor_assignments uk_action_assignment_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.action_processor_assignments
    ADD CONSTRAINT uk_action_assignment_scope UNIQUE (processor_definition_id, workspace_id, project_id);


--
-- Name: action_run_dismissals uk_action_run_dismissals_run_user; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.action_run_dismissals
    ADD CONSTRAINT uk_action_run_dismissals_run_user UNIQUE (run_id, user_id);


--
-- Name: action_processor_workspace_availability uk_action_workspace_availability; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.action_processor_workspace_availability
    ADD CONSTRAINT uk_action_workspace_availability UNIQUE (processor_definition_id, workspace_id);


--
-- Name: codecs uk_codec_name_library; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.codecs
    ADD CONSTRAINT uk_codec_name_library UNIQUE (name, library_id);


--
-- Name: dataset_items uk_dataset_item_source_page; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dataset_items
    ADD CONSTRAINT uk_dataset_item_source_page UNIQUE (dataset_id, source_page_id);


--
-- Name: dataset_releases uk_dataset_release_version_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dataset_releases
    ADD CONSTRAINT uk_dataset_release_version_number UNIQUE (dataset_id, version_number);


--
-- Name: dataset_releases uk_dataset_release_version_tag; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dataset_releases
    ADD CONSTRAINT uk_dataset_release_version_tag UNIQUE (dataset_id, version_tag);


--
-- Name: datasets uk_dataset_workspace_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.datasets
    ADD CONSTRAINT uk_dataset_workspace_name UNIQUE (workspace_id, name);


--
-- Name: controlled_dictionaries uk_dictionary_name_library; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.controlled_dictionaries
    ADD CONSTRAINT uk_dictionary_name_library UNIQUE (name, library_id);


--
-- Name: label_sets uk_label_set_workspace_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.label_sets
    ADD CONSTRAINT uk_label_set_workspace_name UNIQUE (workspace_id, name);


--
-- Name: normalization_profiles uk_normalization_profile_workspace_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.normalization_profiles
    ADD CONSTRAINT uk_normalization_profile_workspace_name UNIQUE (workspace_id, name);


--
-- Name: pages uk_page_name_project; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pages
    ADD CONSTRAINT uk_page_name_project UNIQUE (name, project_id);


--
-- Name: projects uk_project_name_library; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT uk_project_name_library UNIQUE (name, library_id);


--
-- Name: project_package_releases uk_project_package_release_version_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_package_releases
    ADD CONSTRAINT uk_project_package_release_version_number UNIQUE (project_id, version_number);


--
-- Name: project_package_releases uk_project_package_release_version_tag; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_package_releases
    ADD CONSTRAINT uk_project_package_release_version_tag UNIQUE (project_id, version_tag);


--
-- Name: recent_projects uk_recent_user_project; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recent_projects
    ADD CONSTRAINT uk_recent_user_project UNIQUE (user_id, project_id);


--
-- Name: search_lexicon_entries uk_search_lexicon_workspace_project_token; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.search_lexicon_entries
    ADD CONSTRAINT uk_search_lexicon_workspace_project_token UNIQUE (workspace_id, project_id, normalized_token);


--
-- Name: project_stars uk_star_user_project; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_stars
    ADD CONSTRAINT uk_star_user_project UNIQUE (user_id, project_id);


--
-- Name: tag_sets uk_tag_set_workspace_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tag_sets
    ADD CONSTRAINT uk_tag_set_workspace_name UNIQUE (workspace_id, name);


--
-- Name: validation_rulesets uk_validation_ruleset_workspace_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.validation_rulesets
    ADD CONSTRAINT uk_validation_ruleset_workspace_name UNIQUE (workspace_id, name);


--
-- Name: virtual_keyboards uk_virtual_keyboard_workspace_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.virtual_keyboards
    ADD CONSTRAINT uk_virtual_keyboard_workspace_name UNIQUE (workspace_id, name);


--
-- Name: workspace_storage_quotas uk_workspace_storage_quota; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workspace_storage_quotas
    ADD CONSTRAINT uk_workspace_storage_quota UNIQUE (workspace_id);


--
-- Name: upload_session_files upload_session_files_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.upload_session_files
    ADD CONSTRAINT upload_session_files_pkey PRIMARY KEY (id);


--
-- Name: upload_sessions upload_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.upload_sessions
    ADD CONSTRAINT upload_sessions_pkey PRIMARY KEY (id);


--
-- Name: validation_rulesets validation_rulesets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.validation_rulesets
    ADD CONSTRAINT validation_rulesets_pkey PRIMARY KEY (id);


--
-- Name: virtual_keyboards virtual_keyboards_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.virtual_keyboards
    ADD CONSTRAINT virtual_keyboards_pkey PRIMARY KEY (id);


--
-- Name: workspace_members workspace_members_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workspace_members
    ADD CONSTRAINT workspace_members_pkey PRIMARY KEY (id);


--
-- Name: workspace_members workspace_members_workspace_id_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workspace_members
    ADD CONSTRAINT workspace_members_workspace_id_user_id_key UNIQUE (workspace_id, user_id);


--
-- Name: workspace_storage_quotas workspace_storage_quotas_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.workspace_storage_quotas
    ADD CONSTRAINT workspace_storage_quotas_pkey PRIMARY KEY (id);


--
-- Name: idx_action_assignment_project; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_action_assignment_project ON public.action_processor_assignments USING btree (project_id);


--
-- Name: idx_action_assignment_workspace; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_action_assignment_workspace ON public.action_processor_assignments USING btree (workspace_id);


--
-- Name: idx_action_audit_events_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_action_audit_events_created ON public.action_audit_events USING btree (created);


--
-- Name: idx_action_audit_events_definition; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_action_audit_events_definition ON public.action_audit_events USING btree (processor_definition_id);


--
-- Name: idx_action_audit_events_workspace_project; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_action_audit_events_workspace_project ON public.action_audit_events USING btree (workspace_id, project_id);


--
-- Name: idx_action_availability_definition; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_action_availability_definition ON public.action_processor_workspace_availability USING btree (processor_definition_id);


--
-- Name: idx_action_availability_workspace; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_action_availability_workspace ON public.action_processor_workspace_availability USING btree (workspace_id);


--
-- Name: idx_action_run_dismissals_run; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_action_run_dismissals_run ON public.action_run_dismissals USING btree (run_id);


--
-- Name: idx_action_run_dismissals_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_action_run_dismissals_user ON public.action_run_dismissals USING btree (user_id);


--
-- Name: idx_action_run_log_events_run_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_action_run_log_events_run_created ON public.action_run_log_events USING btree (run_id, created);


--
-- Name: idx_action_runs_project_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_action_runs_project_status ON public.action_runs USING btree (project_id, status);


--
-- Name: idx_action_runs_workspace_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_action_runs_workspace_status ON public.action_runs USING btree (workspace_id, status);


--
-- Name: idx_ds_copy_xml_ver_copy_file_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ds_copy_xml_ver_copy_file_id ON public.dataset_item_copy_xml_versions USING btree (copy_file_id);


--
-- Name: idx_ds_copy_xml_ver_copy_file_id_version; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ds_copy_xml_ver_copy_file_id_version ON public.dataset_item_copy_xml_versions USING btree (copy_file_id, version_number);


--
-- Name: idx_page_confidence_index_confidence; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_page_confidence_index_confidence ON public.page_confidence_index USING btree (confidence);


--
-- Name: idx_page_confidence_index_element_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_page_confidence_index_element_type ON public.page_confidence_index USING btree (element_type);


--
-- Name: idx_page_confidence_index_page_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_page_confidence_index_page_id ON public.page_confidence_index USING btree (page_id);


--
-- Name: idx_page_label_index_label_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_page_label_index_label_id ON public.page_label_index USING btree (label_id);


--
-- Name: idx_page_label_index_page_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_page_label_index_page_id ON public.page_label_index USING btree (page_id);


--
-- Name: idx_page_text_content_comment_entry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_page_text_content_comment_entry ON public.page_text_content USING btree (comment_entry);


--
-- Name: idx_page_text_content_normalized_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_page_text_content_normalized_trgm ON public.page_text_content USING gin (normalized_text public.gin_trgm_ops);


--
-- Name: idx_page_text_content_page_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_page_text_content_page_id ON public.page_text_content USING btree (page_id);


--
-- Name: idx_page_text_content_search_vector; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_page_text_content_search_vector ON public.page_text_content USING gin (search_vector);


--
-- Name: idx_page_text_content_text; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_page_text_content_text ON public.page_text_content USING btree (text_content);


--
-- Name: idx_page_xml_version_xml_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_page_xml_version_xml_id ON public.page_xml_versions USING btree (page_xml_id);


--
-- Name: idx_page_xml_version_xml_id_version; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_page_xml_version_xml_id_version ON public.page_xml_versions USING btree (page_xml_id, version_number);


--
-- Name: idx_search_lexicon_normalized_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_search_lexicon_normalized_trgm ON public.search_lexicon_entries USING gin (normalized_token public.gin_trgm_ops);


--
-- Name: idx_search_lexicon_workspace_project; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_search_lexicon_workspace_project ON public.search_lexicon_entries USING btree (workspace_id, project_id);


--
-- Name: idx_stored_files_checksum; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stored_files_checksum ON public.stored_files USING btree (checksum_sha256);


--
-- Name: idx_stored_files_ws_pr_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_stored_files_ws_pr_status ON public.stored_files USING btree (workspace_id, project_id, status);


--
-- Name: personal_workspaces fk13j5o86kea3vgouecm8dmouo5; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.personal_workspaces
    ADD CONSTRAINT fk13j5o86kea3vgouecm8dmouo5 FOREIGN KEY (tag_set_id) REFERENCES public.tag_sets(id);


--
-- Name: page_tags fk1k4tyfqy8sol5t7yfeghpx9d7; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.page_tags
    ADD CONSTRAINT fk1k4tyfqy8sol5t7yfeghpx9d7 FOREIGN KEY (page_id) REFERENCES public.pages(id);


--
-- Name: team_workspaces fk1o9v84cnuyj7a9al2u8ajrmbg; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.team_workspaces
    ADD CONSTRAINT fk1o9v84cnuyj7a9al2u8ajrmbg FOREIGN KEY (dictionary_id) REFERENCES public.controlled_dictionaries(id);


--
-- Name: tag_set_meta_tags fk28vxy8xatkltspk6acu0lfhbt; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tag_set_meta_tags
    ADD CONSTRAINT fk28vxy8xatkltspk6acu0lfhbt FOREIGN KEY (tag_set_id) REFERENCES public.tag_sets(id);


--
-- Name: projects fk291wrcfll60sp2jnpelfkc4fe; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT fk291wrcfll60sp2jnpelfkc4fe FOREIGN KEY (tag_set_id) REFERENCES public.tag_sets(id);


--
-- Name: normalization_profile_replacement_rules fk398dwj70km4gm1frtmaedj1g4; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.normalization_profile_replacement_rules
    ADD CONSTRAINT fk398dwj70km4gm1frtmaedj1g4 FOREIGN KEY (normalization_profile_id) REFERENCES public.normalization_profiles(id);


--
-- Name: task_comment_mentions fk3ec6o104g8q573ptmfyso0x5r; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.task_comment_mentions
    ADD CONSTRAINT fk3ec6o104g8q573ptmfyso0x5r FOREIGN KEY (comment_id) REFERENCES public.task_comments(id);


--
-- Name: projects fk3ppk95wobcbf2k3alkjb1efn6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT fk3ppk95wobcbf2k3alkjb1efn6 FOREIGN KEY (normalization_profile_id) REFERENCES public.normalization_profiles(id);


--
-- Name: dataset_item_source_images fk40averrc0rfqcxca6x82rxyro; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dataset_item_source_images
    ADD CONSTRAINT fk40averrc0rfqcxca6x82rxyro FOREIGN KEY (dataset_item_id) REFERENCES public.dataset_items(id);


--
-- Name: project_stars fk5ixxvajjut9jxwui4vku148ni; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_stars
    ADD CONSTRAINT fk5ixxvajjut9jxwui4vku148ni FOREIGN KEY (project_id) REFERENCES public.projects(id) ON DELETE CASCADE;


--
-- Name: action_runs fk5t43bap200rjy7368fssv9a41; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.action_runs
    ADD CONSTRAINT fk5t43bap200rjy7368fssv9a41 FOREIGN KEY (processor_definition_id) REFERENCES public.action_processor_definitions(id);


--
-- Name: dataset_item_tags fk61xbuj1bbb2ywvj18tlxty7o7; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dataset_item_tags
    ADD CONSTRAINT fk61xbuj1bbb2ywvj18tlxty7o7 FOREIGN KEY (dataset_item_id) REFERENCES public.dataset_items(id);


--
-- Name: keyboard_items fk78q8lmr0eipvqqoc1hqm4xm2f; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.keyboard_items
    ADD CONSTRAINT fk78q8lmr0eipvqqoc1hqm4xm2f FOREIGN KEY (virtual_keyboard_id) REFERENCES public.virtual_keyboards(id);


--
-- Name: action_run_log_events fk7u5ypat15hcygyio21hgyeoa4; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.action_run_log_events
    ADD CONSTRAINT fk7u5ypat15hcygyio21hgyeoa4 FOREIGN KEY (run_id) REFERENCES public.action_runs(id);


--
-- Name: projects fk8aroq4472uvwtstfpf0cf7tie; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT fk8aroq4472uvwtstfpf0cf7tie FOREIGN KEY (dictionary_id) REFERENCES public.controlled_dictionaries(id);


--
-- Name: recent_projects fk8d9tckw5a9y33orn53kstknn9; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recent_projects
    ADD CONSTRAINT fk8d9tckw5a9y33orn53kstknn9 FOREIGN KEY (project_id) REFERENCES public.projects(id) ON DELETE CASCADE;


--
-- Name: dataset_tags fk8o0wghadnunvptkaaviytf0uj; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dataset_tags
    ADD CONSTRAINT fk8o0wghadnunvptkaaviytf0uj FOREIGN KEY (dataset_id) REFERENCES public.datasets(id);


--
-- Name: pages fk9ao2h9hn2vi6f653wg9wwlinv; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pages
    ADD CONSTRAINT fk9ao2h9hn2vi6f653wg9wwlinv FOREIGN KEY (project_id) REFERENCES public.projects(id);


--
-- Name: dataset_item_copy_xml_versions fk9ggdnob9nn6tirh4td86jl6qc; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dataset_item_copy_xml_versions
    ADD CONSTRAINT fk9ggdnob9nn6tirh4td86jl6qc FOREIGN KEY (copy_file_id) REFERENCES public.dataset_item_copy_files(id) ON DELETE CASCADE;


--
-- Name: dataset_items fk9ngqhlbd9bf1iegiywp80yfi8; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dataset_items
    ADD CONSTRAINT fk9ngqhlbd9bf1iegiywp80yfi8 FOREIGN KEY (dataset_id) REFERENCES public.datasets(id) ON DELETE CASCADE;


--
-- Name: projects fk9wqr4tos539y8b5wklfle5ufh; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT fk9wqr4tos539y8b5wklfle5ufh FOREIGN KEY (library_id) REFERENCES public.libraries(id);


--
-- Name: team_workspaces fk9x3w4ojiknwaq4qsfto4snsus; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.team_workspaces
    ADD CONSTRAINT fk9x3w4ojiknwaq4qsfto4snsus FOREIGN KEY (tag_set_id) REFERENCES public.tag_sets(id);


--
-- Name: team_workspaces fka0b8hocc8f5m2lk57agjtoq8s; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.team_workspaces
    ADD CONSTRAINT fka0b8hocc8f5m2lk57agjtoq8s FOREIGN KEY (normalization_profile_id) REFERENCES public.normalization_profiles(id);


--
-- Name: action_processor_assignments fkb9usi4lwrro8hlslf10w4b81d; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.action_processor_assignments
    ADD CONSTRAINT fkb9usi4lwrro8hlslf10w4b81d FOREIGN KEY (processor_definition_id) REFERENCES public.action_processor_definitions(id);


--
-- Name: team_workspaces fkbafyfebr2usx0hdgmvql2r01h; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.team_workspaces
    ADD CONSTRAINT fkbafyfebr2usx0hdgmvql2r01h FOREIGN KEY (codec_id) REFERENCES public.codecs(id);


--
-- Name: codec_characters fkbktsgql1vp55r44rqf85g1i4c; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.codec_characters
    ADD CONSTRAINT fkbktsgql1vp55r44rqf85g1i4c FOREIGN KEY (codec_id) REFERENCES public.codecs(id);


--
-- Name: personal_workspaces fkce10w6rsdy6yunkleojcsui6d; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.personal_workspaces
    ADD CONSTRAINT fkce10w6rsdy6yunkleojcsui6d FOREIGN KEY (label_set_id) REFERENCES public.label_sets(id);


--
-- Name: project_package_releases fkdnk0of09up1wmf82pd4lbjf6a; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_package_releases
    ADD CONSTRAINT fkdnk0of09up1wmf82pd4lbjf6a FOREIGN KEY (project_id) REFERENCES public.projects(id) ON DELETE CASCADE;


--
-- Name: projects fkdp0h5yu85a4kgti9i39qps1c5; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT fkdp0h5yu85a4kgti9i39qps1c5 FOREIGN KEY (label_set_id) REFERENCES public.label_sets(id);


--
-- Name: upload_session_files fkdvaapsma841ok5t9vrfsfokr1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.upload_session_files
    ADD CONSTRAINT fkdvaapsma841ok5t9vrfsfokr1 FOREIGN KEY (session_id) REFERENCES public.upload_sessions(id);


--
-- Name: controlled_dictionary_tags fkg5o4d12y8agy0di6ll2t62l23; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.controlled_dictionary_tags
    ADD CONSTRAINT fkg5o4d12y8agy0di6ll2t62l23 FOREIGN KEY (dictionary_id) REFERENCES public.controlled_dictionaries(id);


--
-- Name: controlled_dictionary_entries fkgf2f2tsjbcg0hv9mbfdhm4scx; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.controlled_dictionary_entries
    ADD CONSTRAINT fkgf2f2tsjbcg0hv9mbfdhm4scx FOREIGN KEY (dictionary_id) REFERENCES public.controlled_dictionaries(id);


--
-- Name: validation_ruleset_tags fkh1evd0cf5kjbm9060fehy06fx; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.validation_ruleset_tags
    ADD CONSTRAINT fkh1evd0cf5kjbm9060fehy06fx FOREIGN KEY (validation_ruleset_id) REFERENCES public.validation_rulesets(id);


--
-- Name: personal_workspaces fki47xwqrys2nkjltqxddagrdrf; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.personal_workspaces
    ADD CONSTRAINT fki47xwqrys2nkjltqxddagrdrf FOREIGN KEY (codec_id) REFERENCES public.codecs(id);


--
-- Name: label_set_tags fkin0f832uynu76os7uo9isyisb; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.label_set_tags
    ADD CONSTRAINT fkin0f832uynu76os7uo9isyisb FOREIGN KEY (label_set_id) REFERENCES public.label_sets(id);


--
-- Name: project_transfer_requests fkjx13xpfj2u63uq4miw6kl909h; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_transfer_requests
    ADD CONSTRAINT fkjx13xpfj2u63uq4miw6kl909h FOREIGN KEY (project_id) REFERENCES public.projects(id) ON DELETE CASCADE;


--
-- Name: page_text_content fkk1avgqv49m6c7kww6u6wrcxpc; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.page_text_content
    ADD CONSTRAINT fkk1avgqv49m6c7kww6u6wrcxpc FOREIGN KEY (page_id) REFERENCES public.pages(id) ON DELETE CASCADE;


--
-- Name: page_label_index fkkk70oasah0mv6giwtww4i0ufx; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.page_label_index
    ADD CONSTRAINT fkkk70oasah0mv6giwtww4i0ufx FOREIGN KEY (page_id) REFERENCES public.pages(id) ON DELETE CASCADE;


--
-- Name: dataset_stratify_tags fkl28owy80np5c253ihpfjwwtsr; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dataset_stratify_tags
    ADD CONSTRAINT fkl28owy80np5c253ihpfjwwtsr FOREIGN KEY (dataset_id) REFERENCES public.datasets(id);


--
-- Name: team_workspaces fklf9h85xbo29hdvj8lctouhf6d; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.team_workspaces
    ADD CONSTRAINT fklf9h85xbo29hdvj8lctouhf6d FOREIGN KEY (validation_ruleset_id) REFERENCES public.validation_rulesets(id);


--
-- Name: codec_tags fkligpbqb7tiwr38y6q9xbsm3tg; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.codec_tags
    ADD CONSTRAINT fkligpbqb7tiwr38y6q9xbsm3tg FOREIGN KEY (codec_id) REFERENCES public.codecs(id);


--
-- Name: personal_workspaces fkln1kfe59tro1b9xm9ffgx6xrk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.personal_workspaces
    ADD CONSTRAINT fkln1kfe59tro1b9xm9ffgx6xrk FOREIGN KEY (validation_ruleset_id) REFERENCES public.validation_rulesets(id);


--
-- Name: projects fkmf4l87a7yw7daowr83f4hl1sr; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT fkmf4l87a7yw7daowr83f4hl1sr FOREIGN KEY (virtual_keyboard_id) REFERENCES public.virtual_keyboards(id);


--
-- Name: action_processor_workspace_availability fknfefnqhpkll57hydm0m9uv5vp; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.action_processor_workspace_availability
    ADD CONSTRAINT fknfefnqhpkll57hydm0m9uv5vp FOREIGN KEY (processor_definition_id) REFERENCES public.action_processor_definitions(id);


--
-- Name: personal_workspaces fknur63txuk9ok87f2msudy2bup; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.personal_workspaces
    ADD CONSTRAINT fknur63txuk9ok87f2msudy2bup FOREIGN KEY (normalization_profile_id) REFERENCES public.normalization_profiles(id);


--
-- Name: team_workspaces fkpcqwkdjghrg5t9vjw3129973b; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.team_workspaces
    ADD CONSTRAINT fkpcqwkdjghrg5t9vjw3129973b FOREIGN KEY (label_set_id) REFERENCES public.label_sets(id);


--
-- Name: virtual_keyboard_tags fkpnyggx3yhlr4f9oju8eg3oc6s; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.virtual_keyboard_tags
    ADD CONSTRAINT fkpnyggx3yhlr4f9oju8eg3oc6s FOREIGN KEY (virtual_keyboard_id) REFERENCES public.virtual_keyboards(id);


--
-- Name: controlled_dictionaries fkpx720pcupk3fxncmjale9hxa9; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.controlled_dictionaries
    ADD CONSTRAINT fkpx720pcupk3fxncmjale9hxa9 FOREIGN KEY (library_id) REFERENCES public.libraries(id);


--
-- Name: dataset_item_copy_files fkq2hl6ont9tcya3ef5bp2et87f; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dataset_item_copy_files
    ADD CONSTRAINT fkq2hl6ont9tcya3ef5bp2et87f FOREIGN KEY (dataset_item_id) REFERENCES public.dataset_items(id) ON DELETE CASCADE;


--
-- Name: personal_workspaces fkq6akapsit3n20oufy0gfo9xki; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.personal_workspaces
    ADD CONSTRAINT fkq6akapsit3n20oufy0gfo9xki FOREIGN KEY (dictionary_id) REFERENCES public.controlled_dictionaries(id);


--
-- Name: codecs fkqwe18yj3fm0759tow5oe13nw3; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.codecs
    ADD CONSTRAINT fkqwe18yj3fm0759tow5oe13nw3 FOREIGN KEY (library_id) REFERENCES public.libraries(id);


--
-- Name: page_images fkqwjoh1h3v04a3lf9unkyxamb3; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.page_images
    ADD CONSTRAINT fkqwjoh1h3v04a3lf9unkyxamb3 FOREIGN KEY (page_id) REFERENCES public.pages(id) ON DELETE CASCADE;


--
-- Name: project_tags fkra1vi3p19o2pqtm3c1geaose9; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_tags
    ADD CONSTRAINT fkra1vi3p19o2pqtm3c1geaose9 FOREIGN KEY (project_id) REFERENCES public.projects(id);


--
-- Name: normalization_profile_tags fkraoxcdl3kbcpjd6enka6mc4t; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.normalization_profile_tags
    ADD CONSTRAINT fkraoxcdl3kbcpjd6enka6mc4t FOREIGN KEY (normalization_profile_id) REFERENCES public.normalization_profiles(id);


--
-- Name: projects fkri3rj35o889tn6lm6hd1d4366; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT fkri3rj35o889tn6lm6hd1d4366 FOREIGN KEY (validation_ruleset_id) REFERENCES public.validation_rulesets(id);


--
-- Name: page_confidence_index fks033wea6nvsg38mboereo8htm; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.page_confidence_index
    ADD CONSTRAINT fks033wea6nvsg38mboereo8htm FOREIGN KEY (page_id) REFERENCES public.pages(id) ON DELETE CASCADE;


--
-- Name: task_assignees fks0jy5sv972lpa2wfx95m7xebb; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.task_assignees
    ADD CONSTRAINT fks0jy5sv972lpa2wfx95m7xebb FOREIGN KEY (task_id) REFERENCES public.tasks(id);


--
-- Name: page_xmls fksjbtwcgy4bhvrticwk3tf98hg; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.page_xmls
    ADD CONSTRAINT fksjbtwcgy4bhvrticwk3tf98hg FOREIGN KEY (page_id) REFERENCES public.pages(id) ON DELETE CASCADE;


--
-- Name: page_xml_versions fksw3lu90ato89wqmjdw754f8q4; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.page_xml_versions
    ADD CONSTRAINT fksw3lu90ato89wqmjdw754f8q4 FOREIGN KEY (page_xml_id) REFERENCES public.page_xmls(id) ON DELETE CASCADE;


--
-- Name: projects fkt3ak6j34eulslrwqxwrhon2kk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT fkt3ak6j34eulslrwqxwrhon2kk FOREIGN KEY (codec_id) REFERENCES public.codecs(id);


--
-- Name: dataset_releases fkt5cdjisipcedtgjfx5c8f0aid; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dataset_releases
    ADD CONSTRAINT fkt5cdjisipcedtgjfx5c8f0aid FOREIGN KEY (dataset_id) REFERENCES public.datasets(id) ON DELETE CASCADE;


--
-- Name: action_run_dismissals fkyjhsd5pbqc9uqwj2g1ylmsxi; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.action_run_dismissals
    ADD CONSTRAINT fkyjhsd5pbqc9uqwj2g1ylmsxi FOREIGN KEY (run_id) REFERENCES public.action_runs(id);


--
-- PostgreSQL database dump complete
--


