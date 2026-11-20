ALTER TABLE personal_workspaces
    ADD COLUMN record_page_xml_authors boolean DEFAULT true NOT NULL,
    ADD COLUMN page_xml_creator_from_user boolean DEFAULT true NOT NULL;

ALTER TABLE team_workspaces
    ADD COLUMN record_page_xml_authors boolean DEFAULT true NOT NULL,
    ADD COLUMN page_xml_creator_from_user boolean DEFAULT true NOT NULL;
