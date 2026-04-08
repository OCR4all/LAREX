export interface CollaborationUserIdentity {
  id: string
  username: string
  displayName: string
  avatar?: string | null
}

export interface CollaborationViewport {
  zoom: number
  offsetX: number
  offsetY: number
}

export interface CollaborationCursor {
  x: number
  y: number
}

export interface CollaborationPresence {
  projectId: string
  pageId: string
  xmlId: string
  panelId?: string | null
  canvasId?: string | null
  variantId?: string | null
  uiMode?: string | null
  selectionId?: string | null
  selectionKind?: 'region' | 'baseline' | null
  viewport?: CollaborationViewport | null
  cursor?: CollaborationCursor | null
  active?: boolean
  updatedAt: string
}

export interface CollaborationRoomMember {
  peerId: string
  user: CollaborationUserIdentity
  presence: CollaborationPresence | null
  joinedAt: string
  lastSeenAt: string
}

export interface CollaborationLeaseOwner {
  user: CollaborationUserIdentity
  acquiredAt: string
}

export interface CollaborationTakeoverRequest {
  requester: CollaborationUserIdentity
  requestedAt: string
  force: boolean
}

export interface CollaborationRoomIdentity {
  roomKey: string
  workspaceId: string
  projectId: string
  pageId: string
  xmlId: string
  token: string
  canEdit: boolean
  canForceTakeover: boolean
  user: CollaborationUserIdentity
}

export interface CollaborationLeaseState {
  editor: CollaborationLeaseOwner | null
  pendingTakeover: CollaborationTakeoverRequest | null
  leaseOwner: boolean
  leaseEpoch: number
  expiresAt?: string | null
}

export interface CollaborationPresenceState {
  members: CollaborationRoomMember[]
}

export interface CollaborationViewerSyncState {
  persistedRevision: string
  latestPersistedRevision?: string | null
  resyncRequired: boolean
  snapshotReady: boolean
}

export interface CollaborationRoomSession {
  identity: CollaborationRoomIdentity
  lease: CollaborationLeaseState
  presence: CollaborationPresenceState
  viewerSync: CollaborationViewerSyncState
}

export interface CollaborationPageSummary {
  projectId: string
  pageId: string
  editor: CollaborationLeaseOwner | null
  viewerCount: number
  collaboratorCount: number
  hasPendingTakeover: boolean
  isLive: boolean
  isIdle: boolean
}

export type CollaborationProjectSummaryState = Record<string, Record<string, CollaborationPageSummary>>

export interface CollaborationRoomBootstrap {
  token: string
  roomKey: string
  workspaceId: string
  projectId: string
  pageId: string
  xmlId: string
  persistedRevision: string
  canEdit: boolean
  canForceTakeover: boolean
  user: CollaborationUserIdentity
  lease: CollaborationLeaseState
}

export interface CollaborationLeaseResponse {
  roomKey: string
  lease: CollaborationLeaseState
}

export interface CollaborationRevisionResponse {
  workspaceId: string
  projectId: string
  pageId: string
  xmlId: string
  persistedRevision: string
  updated: string | null
}

export interface CollaborationRoomStateMessage {
  roomKey: string
  members: CollaborationRoomMember[]
  lease: CollaborationLeaseState
}

export interface CollaborationPresenceMessage {
  roomKey: string
  peerId: string
  presence: CollaborationPresence | null
  lastSeenAt: string
}

const COLLABORATION_COLORS = [
  '#6366f1',
  '#ec4899',
  '#10b981',
  '#0ea5e9',
  '#f59e0b',
  '#8b5cf6',
  '#14b8a6',
  '#f97316'
]

export function getCollaborationColor(seed: string): string {
  if (!seed) return COLLABORATION_COLORS[0]!

  let hash = 0
  for (let index = 0; index < seed.length; index++) {
    hash = ((hash << 5) - hash) + seed.charCodeAt(index)
    hash |= 0
  }

  return COLLABORATION_COLORS[Math.abs(hash) % COLLABORATION_COLORS.length]!
}
