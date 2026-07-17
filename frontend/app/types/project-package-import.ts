export type ProjectPackageToolkitType
  = | 'CODEC'
    | 'DICTIONARY'
    | 'LABEL_SET'
    | 'TAG_SET'
    | 'NORMALIZATION_PROFILE'
    | 'VALIDATION_RULESET'
    | 'VIRTUAL_KEYBOARD'

export type ProjectPackageResourceImportAction
  = | 'AUTO'
    | 'REUSE'
    | 'REPLACE'
    | 'RENAME'
    | 'SKIP'

export type ProjectPackageProjectImportAction
  = | 'AUTO'
    | 'REPLACE'
    | 'RENAME'
    | 'SKIP'

export type ProjectPackageResourcePreview = {
  type: ProjectPackageToolkitType
  name: string
  existingId: string | null
  existingName: string | null
  identical: boolean
  replaceAllowed: boolean
}

export type ProjectPackageImportPreview = {
  previewToken: string
  projectName: string
  projectDescription: string | null
  existingProjectId: string | null
  suggestedProjectName?: string | null
  pageNames: string[]
  imageCount: number
  xmlCount: number
  xmlVersionCount: number
  includesXmlHistory: boolean
  resources: ProjectPackageResourcePreview[]
  warnings: string[]
}

export type ProjectPackageImportOptions = {
  previewToken: string
  projectAction: ProjectPackageProjectImportAction
  renamedProjectName: string | null
  importResources: boolean
  resourceActions: Partial<Record<ProjectPackageToolkitType, ProjectPackageResourceImportAction>>
}

export type ProjectPackageImportResult = {
  workspaceId: string
  projectId: string | null
  projectName: string
  pageCount: number
  imageCount: number
  xmlCount: number
  xmlVersionCount: number
  warnings: string[]
  toolkitTargetIds: Partial<Record<ProjectPackageToolkitType, string>>
}
