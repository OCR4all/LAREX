export type ProjectData = {
  id: string
  name: string
  description: string
  tags: string[]
  created: string
  updated: string
  pageCount: number
  completedPageCount: number
  completionPercentage: number
  isStarred: boolean
  storageUsedBytes: number
  storageUsedFormatted: string
  locked: boolean
  lockedReason: string | null
  codecId?: string | null
  labelSetId?: string | null
  dictionaryId?: string | null
  tagSetId?: string | null
  normalizationProfileId?: string | null
  validationRulesetId?: string | null
  virtualKeyboardId?: string | null
  allowCodecOverride?: boolean
  allowDictionaryOverride?: boolean
  allowVirtualKeyboardOverride?: boolean
  allowLabelSetOverride?: boolean
  allowTagSetOverride?: boolean
  allowNormalizationProfileOverride?: boolean
  allowValidationRulesetOverride?: boolean
  defaultGtIndex?: number | null
  defaultRecognitionIndices?: number[] | null
  outputRetentionDays?: number | null
  capabilities?: {
    canEdit: boolean
    canShare: boolean
    canDelete: boolean
    canDeletePages: boolean
    canUpload: boolean
    canExportPackage: boolean
    canExecuteActions: boolean
    canManageActions: boolean
    canChangePageState: boolean
  }
}

export type ResolvedTag = {
  id: string
  label: string
  color: string | null
}

export type ConflictInfo = {
  conflictId: string
  conflictType: 'IMAGE_VARIANT_EXISTS' | 'XML_FILE_EXISTS'
  existingFileName: string
  newFileName: string
  existingFilePath: string
  newFilePath: string | null
  conflictTimestamp: string
  pageId: string
  pageName: string
  details: {
    existingFileSize: string | null
    newFileSize: string | null
    existingFileModified: string | null
    newFileModified: string | null
  }
}

export type PageIndexingStatus = 'NOT_APPLICABLE' | 'UNINDEXED' | 'INDEXING' | 'INDEXED'
export type PageWorkflowState = 'OPEN' | 'IN_PROGRESS' | 'DONE'

export type TextConfidenceStats = {
  min: number
  max: number
  mean: number
  median: number
  count: number
}

export type Page = {
  id: string
  name: string
  description: string
  tags: string[]
  resolvedTags: ResolvedTag[] | null
  created: string
  updated: string
  xmlFileCount: number
  imageCount: number
  workflowState: PageWorkflowState
  locked?: boolean
  lockedReason?: string | null
  thumbnailUrl?: string | null
  indexingStatus?: PageIndexingStatus
  sortOrder?: number | null
  projectOrderPosition?: number
  textConfidence?: TextConfidenceStats | null
  imageVariants?: Array<{
    id: string
    fileName: string
    variant?: string | null
  }>
}

export type ExportFormat = 'PAGE_XML' | 'ALTO_XML' | 'TXT' | 'PDF' | 'DOCX' | 'TEI' | 'CSV' | 'XLSX'
export type TextLevel = 'PAGE' | 'REGION' | 'TEXT_LINE'
export type SpreadsheetProfile = 'PAGE_METADATA' | 'TAGS' | 'REGIONS'
export type PdfProfile = 'SEARCHABLE' | 'IMAGES_ONLY' | 'TEXT_PAGES' | 'PDFA_SEARCHABLE'
export type TeiProfile = 'STANDARD' | 'LAYOUT'
export type ExportDialogMode = 'page' | 'project' | 'basic' | 'package'
export type ProjectActionScope = 'all' | 'selection'

export type DocxOptions = {
  preserveLineBreaks: boolean
  forcePageBreaks: boolean
  includeImageNames: boolean
  markUnclearWords: boolean
}

export type ExportDialogResult = {
  format: ExportFormat | null
  targetPageXmlVersion: string
  includePageDelimiters: boolean
  textLevel: TextLevel
  textVariantIndex: number
  pdfProfile: PdfProfile
  teiProfile: TeiProfile
  spreadsheetProfiles: SpreadsheetProfile[]
  docxOptions: DocxOptions
  includeXmlHistory: boolean
  embeddedOutputs: Array<{
    format: Exclude<ExportFormat, 'PAGE_XML'>
    includePageDelimiters?: boolean
    textLevel?: TextLevel
    textVariantIndex?: number
    pdfProfile?: PdfProfile
    teiProfile?: TeiProfile
    spreadsheetProfiles?: SpreadsheetProfile[]
    docxOptions?: DocxOptions
  }>
}
