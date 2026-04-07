import type { Peer } from 'crossws'
import type { CollaborationLeaseState } from '../../app/types/collaboration'
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
  },

  leaveRoom(peerId: string, roomKey: string) {
    const room = rooms.get(roomKey)
    if (!room) return

    const member = room.members.get(peerId) ?? null
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
      return
    }

    if (room.members.size === 0) {
      rooms.delete(roomKey)
      roomSnapshots.delete(roomKey)
      return
    }

    broadcastRoom(roomKey)
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

  syncLeaseState(roomKey: string, lease: CollaborationLeaseState, reason = 'lease-updated') {
    const room = getOrCreateRoom(roomKey)
    const previousEditorId = room.lease.editor?.user.id ?? null
    const nextEditorId = lease.editor?.user.id ?? null

    room.lease = {
      editor: lease.editor ?? null,
      pendingTakeover: lease.pendingTakeover ?? null,
      leaseOwner: lease.leaseOwner ?? false,
      leaseEpoch: lease.leaseEpoch ?? 0,
      expiresAt: lease.expiresAt ?? null
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
  },

  markPersistedReloadRequired(roomKey: string, reason = 'persisted-change') {
    const room = rooms.get(roomKey)
    if (!room) return

    roomSnapshots.delete(roomKey)
    broadcastReload(roomKey, reason, room.lease.editor?.user.id ?? null, room.lease.editor?.user.id ?? null)
  }
}
