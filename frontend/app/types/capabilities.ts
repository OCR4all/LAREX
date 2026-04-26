export interface WorkspaceCapabilities {
  canAdminWorkspace: boolean
  canManageMembers: boolean
  canEditWorkspace: boolean
  canEditWorkspaceTextIndexDefaults: boolean
  canManageProjects: boolean
  canManageTasks: boolean
  canManageUtilities: boolean
  canSetPresets: boolean
}

export interface ProjectCapabilities {
  canEdit: boolean
  canShare: boolean
  canDelete: boolean
  canDeletePages: boolean
  canUpload: boolean
  canExportPackage: boolean
}

export interface DatasetCapabilities {
  canEdit: boolean
  canDelete: boolean
  canManageItems: boolean
  canGenerateSplit: boolean
  canExportPackage: boolean
}

export interface TaskCapabilities {
  canEdit: boolean
  canDelete: boolean
  canAssignOthers: boolean
  canUpdateStatus: boolean
}

export interface ResourceCapabilities {
  canEdit: boolean
  canShare: boolean
  canDelete: boolean
}

export const DEFAULT_WORKSPACE_CAPABILITIES: WorkspaceCapabilities = {
  canAdminWorkspace: false,
  canManageMembers: false,
  canEditWorkspace: false,
  canEditWorkspaceTextIndexDefaults: false,
  canManageProjects: false,
  canManageTasks: false,
  canManageUtilities: false,
  canSetPresets: false
}

export const DEFAULT_PROJECT_CAPABILITIES: ProjectCapabilities = {
  canEdit: false,
  canShare: false,
  canDelete: false,
  canDeletePages: false,
  canUpload: false,
  canExportPackage: false
}

export const DEFAULT_DATASET_CAPABILITIES: DatasetCapabilities = {
  canEdit: false,
  canDelete: false,
  canManageItems: false,
  canGenerateSplit: false,
  canExportPackage: false
}

export const DEFAULT_TASK_CAPABILITIES: TaskCapabilities = {
  canEdit: false,
  canDelete: false,
  canAssignOthers: false,
  canUpdateStatus: false
}

export const DEFAULT_RESOURCE_CAPABILITIES: ResourceCapabilities = {
  canEdit: false,
  canShare: false,
  canDelete: false
}
