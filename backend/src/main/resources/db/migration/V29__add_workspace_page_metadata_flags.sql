ALTER TABLE team_workspaces ADD COLUMN IF NOT EXISTS allow_editors_edit_page_name boolean NOT NULL DEFAULT false;
ALTER TABLE team_workspaces ADD COLUMN IF NOT EXISTS allow_editors_edit_page_description boolean NOT NULL DEFAULT false;
ALTER TABLE team_workspaces ADD COLUMN IF NOT EXISTS allow_editors_edit_page_tags boolean NOT NULL DEFAULT false;

ALTER TABLE personal_workspaces ADD COLUMN IF NOT EXISTS allow_editors_edit_page_name boolean NOT NULL DEFAULT false;
ALTER TABLE personal_workspaces ADD COLUMN IF NOT EXISTS allow_editors_edit_page_description boolean NOT NULL DEFAULT false;
ALTER TABLE personal_workspaces ADD COLUMN IF NOT EXISTS allow_editors_edit_page_tags boolean NOT NULL DEFAULT false;
