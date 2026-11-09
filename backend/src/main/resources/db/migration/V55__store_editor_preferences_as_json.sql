-- Editor preferences are UI-only state the backend never queries, so they live in one
-- jsonb object instead of one column per setting. Keys match the frontend's camelCase
-- names; unset settings are absent rather than null.
ALTER TABLE editor_preferences
    ADD COLUMN preferences jsonb NOT NULL DEFAULT '{}'::jsonb;

UPDATE editor_preferences ep
SET preferences = (
    SELECT coalesce(jsonb_object_agg(entry.key, entry.value), '{}'::jsonb)
    FROM jsonb_each(jsonb_build_object(
        'backgroundColor', ep.background_color,
        'backgroundOpacity', ep.background_opacity,
        'toolbarLayout', ep.toolbar_layout,
        'toolbarCompact', ep.toolbar_compact,
        'pageFocusMode', ep.page_focus_mode,
        'autoOpenJobsPanel', ep.auto_open_jobs_panel,
        'leftCollapsed', ep.left_collapsed,
        'rightCollapsed', ep.right_collapsed,
        'leftWidthPx', ep.left_width_px,
        'rightWidthPx', ep.right_width_px,
        'constrainToImage', ep.constrain_to_image,
        'constrainToParent', ep.constrain_to_parent,
        'autoSelect', ep.auto_select,
        'showPolygonLabelFill', ep.show_polygon_label_fill,
        'preventOverlapOnCreate', ep.prevent_overlap_on_create,
        'openRegionTypeMenuOnCreate', ep.open_region_type_menu_on_create,
        'moveWithChildren', ep.move_with_children,
        'cutMinAreaThreshold', ep.cut_min_area_threshold,
        'defaultLineWidth', ep.default_line_width,
        'defaultVertexSize', ep.default_vertex_size,
        'textViewFontSize', ep.text_view_font_size,
        'textViewPadding', ep.text_view_padding,
        'textViewCutoutHeight', ep.text_view_cutout_height,
        'textItemLayout', ep.text_item_layout,
        'textViewAutoSelectFirstLine', ep.text_view_auto_select_first_line,
        'canvasTextCorrectionOverlaySnapToLine', ep.canvas_text_correction_overlay_snap_to_line,
        'canvasTextCorrectionOverlayXRatio', ep.canvas_text_correction_overlay_x_ratio,
        'canvasTextCorrectionOverlayYRatio', ep.canvas_text_correction_overlay_y_ratio,
        'canvasTextCorrectionZoom', ep.canvas_text_correction_zoom,
        'textModeSubmode', ep.text_mode_submode,
        'highlightUnknownCodecChars', ep.highlight_unknown_codec_chars,
        'textlineSortOrder', ep.textline_sort_order,
        'shortcutBindings', ep.shortcut_bindings,
        'tableColumnVisibility', ep.table_column_visibility,
        'tableSorting', ep.table_sorting,
        'tablePageSizes', ep.table_page_sizes,
        'onboardingDashboardTourVersion', ep.onboarding_dashboard_tour_version,
        'onboardingEditorTourVersion', ep.onboarding_editor_tour_version,
        'onboardingTourCompletion', ep.onboarding_tour_completion,
        'onboardingToursOptedOut', ep.onboarding_tours_opted_out
    )) AS entry
    WHERE entry.value <> 'null'::jsonb
);

-- The per-setting columns are no longer read or written. They are kept for one release
-- so the data can be recovered if this migration needs to be revisited, and are dropped
-- in a later migration.
