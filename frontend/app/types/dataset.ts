import type { DatasetCapabilities } from './capabilities'

export type DatasetSplitTemplate = 'TRAIN_VAL' | 'TRAIN_VAL_TEST'
export type DatasetSplitAlgorithm = 'RANDOM_SEEDED' | 'GROUP_BY_SOURCE_PROJECT' | 'MULTILABEL_STRATIFIED_BY_TAGS'
export type DatasetItemMode = 'LINK' | 'COPY'
export type DatasetItemSplit = 'TRAIN' | 'VAL' | 'TEST'
export type DatasetValidationStatus = 'NOT_VALIDATED' | 'VALID' | 'INVALID'
export type DatasetExportStatus = 'NEVER_EXPORTED' | 'READY' | 'FAILED'
export type DatasetItemStatus = 'READY' | 'BROKEN'
export type DatasetReleaseStatus = 'CREATING' | 'READY' | 'FAILED'

export interface DatasetStats {
  totalItems: number
  linkedItems: number
  copiedItems: number
  brokenItems: number
  countsBySplit: Record<string, number>
  countsBySourceProject: Record<string, number>
  countsByMode: Record<string, number>
  countsByTag: Record<string, number>
}

export interface DatasetSummary {
  id: string
  workspaceId: string
  name: string
  description: string | null
  tags: string[]
  created: string
  updated: string
  itemCount: number
  stats: DatasetStats
  lastValidationStatus: DatasetValidationStatus
  lastExportStatus: DatasetExportStatus
  lastValidationAt?: string | null
  lastExportedAt?: string | null
  capabilities?: DatasetCapabilities
}

export interface DatasetItem {
  id: string
  sourceProjectId: string
  sourceProjectName: string
  sourcePageId: string
  sourcePageName: string
  sourcePageTags: string[]
  mode: DatasetItemMode
  selectedSourceXmlId: string
  selectedSourceXmlFileName: string
  selectedSourceImageIds: string[]
  assignedSplit: DatasetItemSplit
  manualSplit: boolean
  status: DatasetItemStatus
  brokenReason?: string | null
  copiedAt?: string | null
  created: string
  updated: string
}

export interface DatasetDetail extends DatasetSummary {
  splitTemplate: DatasetSplitTemplate
  splitAlgorithm: DatasetSplitAlgorithm
  splitSeed: number
  trainPercentage: number
  valPercentage: number
  testPercentage: number
  stratifyTagIds: string[]
  lastValidationWarnings: string[]
  items: DatasetItem[]
  releases: DatasetRelease[]
}

export interface DatasetCreateOrUpdateRequest {
  name: string
  description?: string | null
  tags: string[]
  splitTemplate: DatasetSplitTemplate
  splitAlgorithm: DatasetSplitAlgorithm
  splitSeed: number
  trainPercentage: number
  valPercentage: number
  testPercentage: number
  stratifyTagIds: string[]
}

export interface DatasetAddItemRequest {
  sourceProjectId: string
  sourcePageId: string
  mode: DatasetItemMode
  sourceXmlId: string
  sourceImageIds: string[]
}

export interface DatasetValidateIssue {
  itemId: string
  sourcePageName: string
  reason: string
}

export interface DatasetRelease {
  id: string
  versionNumber: number
  versionTag: string
  notes?: string | null
  status: DatasetReleaseStatus
  validationStatus: DatasetValidationStatus
  failureReason?: string | null
  itemCount: number
  packageFileName?: string | null
  packageFileSize?: number | null
  packageChecksumSha256?: string | null
  manifestChecksumSha256?: string | null
  createdByUserId: string
  sourceDatasetUpdatedAt?: string | null
  shareEnabled: boolean
  shareSecretPrefix?: string | null
  shareCreatedAt?: string | null
  shareExpiresAt?: string | null
  shareRevokedAt?: string | null
  shareLastUsedAt?: string | null
  shareDownloadCount: number
  created: string
  updated: string
}

export interface DatasetCreateReleaseRequest {
  versionTag?: string | null
  notes?: string | null
}

export interface DatasetReleaseShareRequest {
  expiresAt: string
}

export interface DatasetReleaseShareResponse {
  downloadUrl: string
  secret: string
  expiresAt: string
  createdAt: string
}

export interface DatasetValidationResult {
  status: DatasetValidationStatus
  stats: DatasetStats
  warnings: string[]
  issues: DatasetValidateIssue[]
}

export interface DatasetEditorAnnotationContext {
  mode: 'PROJECT' | 'DATASET_LINK' | 'DATASET_COPY'
  basePath: string
  createAllowed: boolean
}

export interface DatasetEditorImageVariant {
  id: string
  fileName: string
  variant?: string | null
  label: string
  url: string
}

export interface DatasetEditorXmlFile {
  id: string
  fileName: string
  schema: 'PAGE_XML' | 'ALTO_XML' | 'UNKNOWN'
  schemaVersion?: string | null
  variant?: string | null
}

export interface DatasetEditorPage {
  itemId: string
  sourceProjectId: string
  sourceProjectName: string
  sourcePageId: string
  sourcePageName: string
  mode: DatasetItemMode
  sourcePageTags: string[]
  canEdit: boolean
  readOnlyReason?: string | null
  annotationContext: DatasetEditorAnnotationContext
  imageVariants: DatasetEditorImageVariant[]
  xmlFiles: DatasetEditorXmlFile[]
  thumbnailUrl?: string | null
}

export interface DatasetEditorProjectPages {
  projectId: string
  projectName: string
  pages: DatasetEditorPage[]
}

export interface DatasetEditorSkippedItem {
  itemId: string
  sourceProjectId: string
  sourcePageId: string
  sourcePageName: string
  reasonCode: string
  reason: string
}

export interface DatasetEditorOpenResponse {
  projects: DatasetEditorProjectPages[]
  skippedItems: DatasetEditorSkippedItem[]
}
