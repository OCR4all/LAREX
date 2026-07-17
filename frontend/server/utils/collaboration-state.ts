import type { Peer } from 'crossws'
import { websocketUtils } from './websocket'
import type { CollaborationLeaseState, CollaborationPageSummary } from '../../app/types/collaboration'
import type { CollaborationRoomTokenPayload } from './collaboration-token'

type CollaborationUser = {
  id: string
  username: string
  displayName: string
  avatar?: string | null
}

type RoomMember = {
  peerId: string
  joinedAt: string
  lastSeenAt: string
  token: CollaborationRoomTokenPayload
  presence: Record<string, unknown> | null
}

type RoomRecord = {
  members: Map<string, RoomMember>
  lease: CollaborationLeaseState
}

type RoomStateMessage = {
  type: 'COLLAB_ROOM_STATE'
  payload: {
    roomKey: string
    members: Array<{
      peerId: string
      joinedAt: string
      lastSeenAt: string
      user: CollaborationUser
      presence: Record<string, unknown> | null
    }>
    lease: CollaborationLeaseState
  }
}

type PresenceMessage = {
  type: 'COLLAB_MEMBER_PRESENCE'
  payload: {
    roomKey: string
    peerId: string
    lastSeenAt: string
    presence: Record<string, unknown> | null
  }
}

type GenericMessage = {
  type: string
  payload: Record<string, unknown>
}

export type CollaborationLeaseRenewalTarget = CollaborationRoomTokenPayload['annotationRoute'] & {
  xmlId: string
}

const rooms = new Map<string, RoomRecord>()
const peers = new Map<string, Peer>()
const peerRooms = new Map<string, Set<string>>()
const roomSnapshots = new Map<string, unknown>()

function send(peer: Peer, message: RoomStateMessage | PresenceMessage | GenericMessage) {
  try {
    peer.send(JSON.stringify(message))
  } catch (error) {
    console.warn('[collaboration] Failed to send room update:', error)
  }
}

function toUser(token: CollaborationRoomTokenPayload): CollaborationUser {
  return {
    id: token.sub,
    username: token.username,
    displayName: token.displayName,
    avatar: token.avatar ?? null
  }
}

function emptyLeaseState(): CollaborationLeaseState {
  return {
    editor: null,
    pendingTakeover: null,
    leaseOwner: false,
    leaseEpoch: 0,
    expiresAt: null
  }
}

function parseRoomKey(roomKey: string): { projectId: string, pageId: string, xmlId: string } | null {
  const [projectId, pageId, xmlId] = roomKey.split(':')
  if (!projectId || !pageId || !xmlId) {
    return null
  }
  return { projectId, pageId, xmlId }
}

function getOrCreateRoom(roomKey: string): RoomRecord {
  let room = rooms.get(roomKey)
  if (room) return room

  room = {
    members: new Map<string, RoomMember>(),
    lease: emptyLeaseState()
  }
  rooms.set(roomKey, room)
  return room
}

function toRoomState(roomKey: string): RoomStateMessage {
  const room = rooms.get(roomKey)

  return {
    type: 'COLLAB_ROOM_STATE',
    payload: {
      roomKey,
      members: room
        ? Array.from(room.members.values()).map(member => ({
            peerId: member.peerId,
            joinedAt: member.joinedAt,
            lastSeenAt: member.lastSeenAt,
            user: toUser(member.token),
            presence: member.presence
          }))
        : [],
      lease: room?.lease ?? emptyLeaseState()
    }
  }
}

function broadcastRoom(roomKey: string) {
  const room = rooms.get(roomKey)
  if (!room) return

  const state = toRoomState(roomKey)
  for (const peerId of room.members.keys()) {
    const peer = peers.get(peerId)
    if (!peer) continue
    send(peer, state)
  }
}

function broadcastPresence(roomKey: string, updatedPeerId: string, member: RoomMember) {
  const room = rooms.get(roomKey)
  if (!room) return

  const message: PresenceMessage = {
    type: 'COLLAB_MEMBER_PRESENCE',
    payload: {
      roomKey,
      peerId: updatedPeerId,
      lastSeenAt: member.lastSeenAt,
      presence: member.presence
    }
  }

  for (const peerId of room.members.keys()) {
    if (peerId === updatedPeerId) continue
    const peer = peers.get(peerId)
    if (!peer) continue
    send(peer, message)
  }
}

function broadcastReload(roomKey: string, reason: string, previousEditorId: string | null, nextEditorId: string | null) {
  const room = rooms.get(roomKey)
  if (!room) return

  for (const peerId of room.members.keys()) {
    const peer = peers.get(peerId)
    if (!peer) continue
    send(peer, {
      type: 'COLLAB_RELOAD_REQUIRED',
      payload: {
        roomKey,
        reason,
        previousEditorId,
        nextEditorId
      }
    })
  }
}

function buildPageSummary(projectId: string, pageId: string): CollaborationPageSummary | null {
  const matchingRooms = Array.from(rooms.entries())
    .filter(([roomKey]) => {
      const parsed = parseRoomKey(roomKey)
      return parsed?.projectId === projectId && parsed.pageId === pageId
    })
    .map(([, room]) => room)

  if (matchingRooms.length === 0) {
    return null
  }

  let editor = null as CollaborationPageSummary['editor']
  let hasPendingTakeover = false
  let isLive = false
  const collaboratorIds = new Set<string>()
  const viewerIds = new Set<string>()

  for (const room of matchingRooms) {
    if (room.lease.editor && !editor) {
      editor = room.lease.editor
    }
    hasPendingTakeover ||= Boolean(room.lease.pendingTakeover)

    const editorId = room.lease.editor?.user.id ?? null
    if (editorId) {
      collaboratorIds.add(editorId)
    }

    for (const member of room.members.values()) {
      collaboratorIds.add(member.token.sub)
      if (!editorId || member.token.sub !== editorId) {
        viewerIds.add(member.token.sub)
      }
      if (editorId && member.token.sub === editorId && member.presence?.active === true) {
        isLive = true
      }
    }
  }

  if (!editor && viewerIds.size === 0 && !hasPendingTakeover) {
    return null
  }

  return {
    projectId,
    pageId,
    editor,
    viewerCount: viewerIds.size,
    collaboratorCount: collaboratorIds.size,
    hasPendingTakeover,
    isLive,
    isIdle: Boolean(editor) && !isLive
  }
}

function broadcastPageSummary(projectId: string, pageId: string) {
  websocketUtils.broadcast({
    type: 'COLLAB_PAGE_SUMMARY_UPDATED',
    payload: {
      projectId,
      pageId,
      summary: buildPageSummary(projectId, pageId)
    }
  })
}

export const collaborationState = {
  registerPeer(peer: Peer) {
    peers.set(peer.id, peer)
  },

  unregisterPeer(peerId: string) {
    const roomKeys = Array.from(peerRooms.get(peerId) ?? [])
    for (const roomKey of roomKeys) {
      this.leaveRoom(peerId, roomKey)
    }

    peerRooms.delete(peerId)
    peers.delete(peerId)
  },

  joinRoom(peer: Peer, token: CollaborationRoomTokenPayload) {
    peers.set(peer.id, peer)

    const room = getOrCreateRoom(token.roomKey)
    const now = new Date().toISOString()
    room.members.set(peer.id, {
      peerId: peer.id,
      joinedAt: now,
      lastSeenAt: now,
      token,
      presence: {
        projectId: token.projectId,
        pageId: token.pageId,
        xmlId: token.xmlId,
        updatedAt: now,
        active: true
      }
    })

    const joinedRooms = peerRooms.get(peer.id) ?? new Set<string>()
    joinedRooms.add(token.roomKey)
    peerRooms.set(peer.id, joinedRooms)

    send(peer, {
      type: 'COLLAB_JOINED',
      payload: {
        roomKey: token.roomKey,
        projectId: token.projectId,
        pageId: token.pageId,
        xmlId: token.xmlId
      }
    })

    send(peer, {
      type: 'COLLAB_SNAPSHOT_SYNC',
      payload: {
        roomKey: token.roomKey,
        snapshot: roomSnapshots.get(token.roomKey) ?? null
      }
    })

    broadcastRoom(token.roomKey)
    broadcastPageSummary(token.projectId, token.pageId)
  },

  updatePresence(peerId: string, roomKey: string, presence: Record<string, unknown>) {
    const room = rooms.get(roomKey)
    const member = room?.members.get(peerId)
    if (!room || !member) return

    const timestamp = new Date().toISOString()
    member.presence = {
      ...(member.presence ?? {}),
      ...presence,
      projectId: member.token.projectId,
      pageId: member.token.pageId,
      xmlId: member.token.xmlId,
      updatedAt: timestamp
    }
    member.lastSeenAt = timestamp

    broadcastPresence(roomKey, peerId, member)
    broadcastPageSummary(member.token.projectId, member.token.pageId)
  },

  leaveRoom(peerId: string, roomKey: string) {
    const room = rooms.get(roomKey)
    if (!room) return

    const member = room.members.get(peerId) ?? null
    const parsedRoom = parseRoomKey(roomKey)
    room.members.delete(peerId)

    const joinedRooms = peerRooms.get(peerId)
    joinedRooms?.delete(roomKey)
    if (joinedRooms && joinedRooms.size === 0) {
      peerRooms.delete(peerId)
    }

    if (!member) {
      if (room.members.size === 0) {
        rooms.delete(roomKey)
        roomSnapshots.delete(roomKey)
      }
      if (parsedRoom) {
        broadcastPageSummary(parsedRoom.projectId, parsedRoom.pageId)
      }
      return
    }

    if (room.members.size === 0) {
      rooms.delete(roomKey)
      roomSnapshots.delete(roomKey)
      broadcastPageSummary(member.token.projectId, member.token.pageId)
      return
    }

    broadcastRoom(roomKey)
    broadcastPageSummary(member.token.projectId, member.token.pageId)
  },

  applySnapshotUpdate(peerId: string, roomKey: string, snapshot: unknown) {
    const room = rooms.get(roomKey)
    const member = room?.members.get(peerId)
    if (!room || !member) return
    if (room.lease.editor?.user.id !== member.token.sub) return

    roomSnapshots.set(roomKey, snapshot)

    for (const memberPeerId of room.members.keys()) {
      if (memberPeerId === peerId) continue
      const peer = peers.get(memberPeerId)
      if (!peer) continue
      send(peer, {
        type: 'COLLAB_SNAPSHOT_UPDATE',
        payload: {
          roomKey,
          snapshot
        }
      })
    }
  },

  getLeaseRenewalTargets(peerId: string, requestedRoomKeys: string[]): CollaborationLeaseRenewalTarget[] {
    const joinedRoomKeys = peerRooms.get(peerId)
    if (!joinedRoomKeys) return []

    const targets: CollaborationLeaseRenewalTarget[] = []
    for (const roomKey of [...new Set(requestedRoomKeys)].slice(0, 100)) {
      if (!joinedRoomKeys.has(roomKey)) continue

      const member = rooms.get(roomKey)?.members.get(peerId)
      if (!member?.token.canEdit) continue

      targets.push({
        ...member.token.annotationRoute,
        xmlId: member.token.xmlId
      })
    }
    return targets
  },

  syncLeaseState(roomKey: string, lease: CollaborationLeaseState, reason = 'lease-updated') {
    const room = getOrCreateRoom(roomKey)
    const previousEditorId = room.lease.editor?.user.id ?? null
    const nextEditorId = lease.editor?.user.id ?? null
    const parsedRoom = parseRoomKey(roomKey)

    room.lease = {
      editor: lease.editor ?? null,
      pendingTakeover: lease.pendingTakeover ?? null,
      leaseOwner: lease.leaseOwner ?? false,
      leaseEpoch: lease.leaseEpoch ?? 0,
      expiresAt: lease.expiresAt ?? null
    }

    if (
      room.members.size === 0
      && !room.lease.editor
      && !room.lease.pendingTakeover
    ) {
      rooms.delete(roomKey)
      roomSnapshots.delete(roomKey)
      if (parsedRoom) {
        broadcastPageSummary(parsedRoom.projectId, parsedRoom.pageId)
      }
      return
    }

    if (previousEditorId !== nextEditorId) {
      roomSnapshots.delete(roomKey)
    }

    if (room.members.size > 0) {
      broadcastRoom(roomKey)
      if (previousEditorId !== nextEditorId) {
        broadcastReload(roomKey, reason, previousEditorId, nextEditorId)
      }
    }
    if (parsedRoom) {
      broadcastPageSummary(parsedRoom.projectId, parsedRoom.pageId)
    }
  },

  markPersistedReloadRequired(roomKey: string, reason = 'persisted-change') {
    const room = rooms.get(roomKey)
    if (!room) return

    roomSnapshots.delete(roomKey)
    broadcastReload(roomKey, reason, room.lease.editor?.user.id ?? null, room.lease.editor?.user.id ?? null)
  },

  markPersistedRevision(
    roomKey: string,
    persistedRevision: string,
    options?: { reason?: string, sourceUserId?: string | null, reloadRequired?: boolean }
  ) {
    const room = rooms.get(roomKey)
    if (!room) return

    if (options?.reloadRequired) {
      roomSnapshots.delete(roomKey)
    }

    for (const peerId of room.members.keys()) {
      const peer = peers.get(peerId)
      if (!peer) continue
      send(peer, {
        type: 'COLLAB_REVISION_CHANGED',
        payload: {
          roomKey,
          persistedRevision,
          reason: options?.reason ?? 'persisted-change',
          sourceUserId: options?.sourceUserId ?? null,
          reloadRequired: options?.reloadRequired === true
        }
      })
    }
  },

  getProjectPageSummaries(projectId: string): CollaborationPageSummary[] {
    const pageIds = new Set<string>()
    for (const roomKey of rooms.keys()) {
      const parsed = parseRoomKey(roomKey)
      if (parsed?.projectId === projectId) {
        pageIds.add(parsed.pageId)
      }
    }

    return Array.from(pageIds)
      .map(pageId => buildPageSummary(projectId, pageId))
      .filter((summary): summary is CollaborationPageSummary => Boolean(summary))
  }
}
