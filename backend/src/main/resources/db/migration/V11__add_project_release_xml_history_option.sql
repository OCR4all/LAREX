ALTER TABLE project_package_releases
    ADD COLUMN include_xml_history BOOLEAN NOT NULL DEFAULT TRUE;
