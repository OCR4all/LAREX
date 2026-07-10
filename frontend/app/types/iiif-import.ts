export type IiifImportJobStatus = 'PENDING' | 'IMPORTING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'

export interface IiifManifestSummary {
  id: string | null
  sourceUrl: string | null
  sourceType: string | null
  sourceName: string | null
  resourceType: string | null
  label: string | null
  provider: string | null
  thumbnailUrl: string | null
  presentationVersion: string | null
  manifestCount: number
}

export interface IiifImportItemResult {
  canvasId: string
  canvasLabel: string
  index: number
  requestedPageName: string
  finalPageName: string
  action: string
  status: string
  pageId: string | null
  message: string
}

export interface IiifImportJob {
  id: string
  projectId: string
  projectName: string
  workspaceId: string
  sourceType: string | null
  sourceReference: string | null
  status: IiifImportJobStatus
  queuePosition: number | null
  totalCanvases: number
  processedCanvases: number
  skippedCanvases: number
  failedCanvases: number
  progressPercent: number
  estimatedStorageBytes: number
  manifest: IiifManifestSummary | null
  warnings: string[]
  results: IiifImportItemResult[]
  errorMessage: string | null
  created: string
  updated: string
  completedAt: string | null
}

export interface DismissIiifImportJobsResponse {
  dismissedCount: number
}
