export interface ActionOutputFile {
  id: string
  pageId: string | null
  fileName: string
  mimeType: string
  sizeBytes: number
  checksumSha256: string
  created: string
}

export interface ActionOutput {
  id: string
  sourceRunId: string
  processorDefinitionId: string
  processorKey: string
  processorName: string
  createdByUserId: string
  fileCount: number
  totalSizeBytes: number
  retentionDays: number | null
  expiresAt: string | null
  completedAt: string
  shareEnabled: boolean
  shareSecretPrefix: string | null
  shareCreatedAt: string | null
  shareExpiresAt: string | null
  shareRevokedAt: string | null
  shareLastUsedAt: string | null
  shareDownloadCount: number
  files: ActionOutputFile[]
  created: string
  updated: string
}

export interface ActionOutputShareRequest {
  expiresAt: string
}

export interface ActionOutputShareResponse {
  downloadUrl: string
  secret: string
  expiresAt: string
  createdAt: string
}
