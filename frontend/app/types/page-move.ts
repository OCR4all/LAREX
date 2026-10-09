export type PageMovePolicy = 'SKIP' | 'OVERWRITE' | 'RENAME'

export type PageMoveRequest = {
  pageIds: string[]
  destinationProjectId: string
  conflictPolicy: PageMovePolicy
  prefix?: string
  suffix?: string
  fingerprint?: string
}

export type PageMovePreview = {
  items: Array<{
    pageId: string
    sourceName: string
    resultingName: string
    outcome: 'MOVE' | 'SKIP' | 'OVERWRITE' | 'RENAME'
    overwrittenPageId: string | null
    blockers: string[]
  }>
  blockers: string[]
  movedCount: number
  skippedCount: number
  overwrittenCount: number
  renamedCount: number
  fingerprint: string
}

export type PageMoveResult = PageMovePreview & { destinationProjectId: string }
