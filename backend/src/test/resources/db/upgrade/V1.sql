INSERT INTO team_workspaces (id, name, owner_user_id, description, default_gt_index, default_recognition_indices,
                             allow_editors_edit_page_name, default_review_required, created, updated)
VALUES ('upgrade-team-workspace', 'Upgrade Team', 'upgrade-owner', 'Seeded on 1.0.0', 0, '1,2',
        true, true, '2026-11-01 09:00:00', '2026-11-02 09:00:00');

INSERT INTO personal_workspaces (id, owner_user_id, description, created, updated)
VALUES ('upgrade-personal-workspace', 'upgrade-owner', 'Personal space on 1.0.0',
        '2026-11-01 09:00:00', '2026-11-01 09:00:00');

INSERT INTO workspace_members (id, workspace_id, user_id, role, invitation_status, created, updated)
VALUES ('upgrade-member', 'upgrade-team-workspace', 'upgrade-editor', 'EDITOR', 'ACCEPTED',
        '2026-11-01 09:00:00', '2026-11-01 09:00:00');

INSERT INTO libraries (id, name, workspace_id, created, updated)
VALUES ('upgrade-team-library', 'Upgrade Team Library', 'upgrade-team-workspace',
        '2026-11-01 09:00:00', '2026-11-01 09:00:00'),
       ('upgrade-personal-library', 'Personal Library', 'upgrade-personal-workspace',
        '2026-11-01 09:00:00', '2026-11-01 09:00:00');

INSERT INTO projects (id, name, library_id, description, locked,
                      allow_codec_override, allow_dictionary_override, allow_label_set_override,
                      allow_normalization_profile_override, allow_tag_set_override,
                      allow_validation_ruleset_override, allow_virtual_keyboard_override,
                      review_required, primary_image_variant, created, updated)
VALUES ('upgrade-project', 'Upgrade Project', 'upgrade-team-library', 'Ground truth from 1.0.0', false,
        true, true, true, true, true, true, true,
        true, 'original', '2026-11-01 09:00:00', '2026-11-03 09:00:00');

INSERT INTO pages (id, name, project_id, sort_order, locked, workflow_state, changes_requested, created, updated)
VALUES ('upgrade-page-1', '0001', 'upgrade-project', 0, false, 'DONE', false,
        '2026-11-01 09:00:00', '2026-11-03 09:00:00'),
       ('upgrade-page-2', '0002', 'upgrade-project', 1, false, 'IN_REVIEW', false,
        '2026-11-01 09:00:00', '2026-11-03 09:00:00');

INSERT INTO page_images (id, page_id, base_name, file_name, file_path, mime_type, variant, image_type, file_size,
                         created, updated)
VALUES ('upgrade-image-1', 'upgrade-page-1', '0001', '0001.png', 'upgrade/0001.png', 'image/png', 'original',
        'COLOR', 1024, '2026-11-01 09:00:00', '2026-11-01 09:00:00');

INSERT INTO page_xmls (id, page_id, base_name, file_name, file_path, mime_type, schema, schema_version, variant,
                       file_size, created, updated)
VALUES ('upgrade-xml-1', 'upgrade-page-1', '0001', '0001.xml', 'upgrade/0001.xml', 'application/xml', 'PAGE_XML',
        '2019-07-15', 'original', 2048, '2026-11-01 09:00:00', '2026-11-03 09:00:00');
