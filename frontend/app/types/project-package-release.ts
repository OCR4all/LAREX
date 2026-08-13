export type ProjectPackageReleaseStatus = 'CREATING' | 'READY' | 'FAILED'
export type ProjectReleaseExportFormat = 'ALTO_XML' | 'TXT' | 'PDF' | 'DOCX' | 'TEI' | 'CSV' | 'XLSX'
export type ProjectReleaseTextLevel = 'PAGE' | 'REGION' | 'TEXT_LINE'
export type ProjectReleaseSpreadsheetProfile = 'PAGE_METADATA' | 'TAGS' | 'REGIONS'
export type ProjectReleasePdfProfile = 'SEARCHABLE' | 'IMAGES_ONLY' | 'TEXT_PAGES' | 'PDFA_SEARCHABLE'
export type ProjectReleaseTeiProfile = 'STANDARD' | 'LAYOUT'

export interface ProjectReleaseDocxOptions {
  preserveLineBreaks?: boolean | null
  forcePageBreaks?: boolean | null
  includeImageNames?: boolean | null
  markUnclearWords?: boolean | null
  unclearConfidenceThreshold?: number | null
}

export interface ProjectPackageEmbeddedOutputRequest {
  format: ProjectReleaseExportFormat
  includePageDelimiters?: boolean | null
  textLevel?: ProjectReleaseTextLevel | null
  textVariantIndex?: number | null
  pdfProfile?: ProjectReleasePdfProfile | null
  teiProfile?: ProjectReleaseTeiProfile | null
  spreadsheetProfiles?: ProjectReleaseSpreadsheetProfile[] | null
  docxOptions?: ProjectReleaseDocxOptions | null
  imageVariantSelection?: {
    mode: 'GLOBAL' | 'PER_PAGE'
    variant?: string
    pageVariants?: Record<string, string>
    fallbackImage: boolean
  } | null
}

export interface ProjectPackageCreateReleaseRequest {
  versionTag?: string | null
  notes?: string | null
  targetPageXmlVersion?: string | null
  embeddedOutputs?: ProjectPackageEmbeddedOutputRequest[] | null
  includeXmlHistory?: boolean | null
}

export interface ProjectPackageRelease {
  id: string
  versionNumber: number
  versionTag: string
  notes?: string | null
  status: ProjectPackageReleaseStatus
  pageCount: number
  targetPageXmlVersion?: string | null
  includeXmlHistory: boolean
  embeddedOutputs: ProjectPackageEmbeddedOutputRequest[]
  failureReason?: string | null
  packageFileName?: string | null
  packageFileSize?: number | null
  packageChecksumSha256?: string | null
  manifestChecksumSha256?: string | null
  createdByUserId: string
  sourceProjectUpdatedAt?: string | null
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

export interface ProjectPackageReleaseShareRequest {
  expiresAt: string
}

export interface ProjectPackageReleaseShareResponse {
  downloadUrl: string
  secret: string
  expiresAt: string
  createdAt: string
}
