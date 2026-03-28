export interface AdminErrorSummary {
  windowDays: number
  totalEvents: number
  serverErrors: number
  actionableClientErrors: number
  distinctUsers: number
  distinctWorkspaces: number
}

export interface AdminErrorEventSummary {
  id: string
  created: string | null
  status: number
  severity: 'WARN' | 'ERROR'
  code?: string | null
  error: string
  message: string
  path: string
  method: string
  userId?: string | null
  username?: string | null
  workspaceId?: string | null
}

export interface AdminErrorEventPage {
  items: AdminErrorEventSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface AdminErrorEventDetail extends AdminErrorEventSummary {
  exceptionClass?: string | null
  detailsJson?: string | null
  stackTrace?: string | null
}
