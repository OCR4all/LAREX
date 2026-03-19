export type WorkspaceTextSearchHit = {
  workspaceId: string
  projectId: string
  projectName: string
  pageId: string
  pageName: string
  textLineId?: string | null
  regionId?: string | null
  snippetHtml: string
  fullText: string
  score: number
  matchKind: string
  previewUrl?: string | null
}

export type WorkspaceTextSearchProjectGroup = {
  workspaceId: string
  projectId: string
  projectName: string
  hitCount: number
  topScore: number
  hits: WorkspaceTextSearchHit[]
}

export type WorkspaceTextSearchPageCluster = {
  pageId: string
  pageName: string
  hitCount: number
  topScore: number
  hits: WorkspaceTextSearchHit[]
}

export type WorkspaceTextSearchClusterGroup = {
  workspaceId: string
  projectId: string
  projectName: string
  hitCount: number
  topScore: number
  pages: WorkspaceTextSearchPageCluster[]
}

export type WorkspaceTextSearchResponse = {
  workspaceId: string
  query: string
  matchMode: string
  view: string
  limit: number
  offset: number
  totalHits: number
  totalProjectCount: number
  fuzzyExpanded: boolean
  suggestedQuery?: string | null
  hits: WorkspaceTextSearchHit[]
  projects: WorkspaceTextSearchProjectGroup[]
}
