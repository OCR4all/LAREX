import type { Peer } from 'crossws'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { collaborationState } from '../collaboration-state'
import type { CollaborationRoomTokenPayload } from '../collaboration-token'
import type { CollaborationLeaseState } from '~/types/collaboration'
import { websocketUtils } from '../websocket'

const peerIds: string[] = []

function createPeer(id: string) {
  const send = vi.fn()
  const peer = { id, send } as unknown as Peer
  peerIds.push(id)
  collaborationState.registerPeer(peer)
  return { peer, send }
}

function token(roomKey: string, canEdit = true): CollaborationRoomTokenPayload {
  const [projectId, pageId, xmlId] = roomKey.split(':') as [string, string, string]
  return {
    sub: 'user-1',
    username: 'user',
    displayName: 'User',
    workspaceId: 'workspace-1',
    projectId,
    pageId,
    xmlId,
    roomKey,
    canEdit,
    canForceTakeover: false,
    persistedRevision: 'revision-1',
    annotationRoute: { scope: 'PROJECT', projectId, pageId },
    exp: Date.now() + 60_000
  }
}

function lease(editorId: string, leaseEpoch: string, expiresAt = '2099-01-01T00:00:00Z'): CollaborationLeaseState {
  return {
    editor: {
      user: {
        id: editorId,
        username: editorId,
        displayName: editorId
      },
      acquiredAt: '2026-01-01T00:00:00Z'
    },
    pendingTakeover: null,
    leaseEpoch,
    expiresAt
  }
}

afterEach(() => {
  for (const peerId of peerIds.splice(0)) {
    collaborationState.unregisterPeer(peerId)
  }
  vi.restoreAllMocks()
})

describe('collaboration state renewal protocol', () => {
  it('only replays snapshots for the persisted revision in the joining token', () => {
    const roomKey = 'snapshot-revision:page:xml'
    const { peer } = createPeer('snapshot-editor')
    collaborationState.joinRoom(peer, token(roomKey))
    collaborationState.syncLeaseState(roomKey, lease('user-1', '2'))
    collaborationState.applySnapshotUpdate(peer.id, roomKey, { custom: 'old XML' }, 'revision-1', '2')
    const viewer = createPeer('snapshot-viewer')
    collaborationState.joinRoom(viewer.peer, { ...token(roomKey), persistedRevision: 'revision-2' })
    expect(viewer.send.mock.calls.map(([raw]) => JSON.parse(raw as string))).toContainEqual({
      type: 'COLLAB_SNAPSHOT_SYNC',
      payload: { roomKey, snapshot: null, persistedRevision: 'revision-2', leaseEpoch: '2' }
    })
  })

  it('rejects delayed snapshots from an older lease even when the same user owns both leases', () => {
    const roomKey = 'snapshot-lease:page:xml'
    const { peer } = createPeer('snapshot-lease-editor')
    collaborationState.joinRoom(peer, token(roomKey))
    collaborationState.syncLeaseState(roomKey, lease('user-1', '3'))
    collaborationState.applySnapshotUpdate(peer.id, roomKey, { custom: 'current' }, 'revision-1', '3')
    collaborationState.applySnapshotUpdate(peer.id, roomKey, { custom: 'stale' }, 'revision-1', '2')
    const viewer = createPeer('snapshot-lease-viewer')
    collaborationState.joinRoom(viewer.peer, token(roomKey))
    expect(viewer.send.mock.calls.map(([raw]) => JSON.parse(raw as string))).toContainEqual({
      type: 'COLLAB_SNAPSHOT_SYNC',
      payload: { roomKey, snapshot: { custom: 'current' }, persistedRevision: 'revision-1', leaseEpoch: '3' }
    })
  })

  it('broadcasts snapshots with the persisted revision and lease they belong to', () => {
    const roomKey = 'snapshot-broadcast:page:xml'
    const { peer } = createPeer('snapshot-broadcast-editor')
    const viewer = createPeer('snapshot-broadcast-viewer')
    collaborationState.joinRoom(peer, token(roomKey))
    collaborationState.joinRoom(viewer.peer, token(roomKey, false))
    collaborationState.syncLeaseState(roomKey, lease('user-1', '2'))
    viewer.send.mockClear()
    collaborationState.applySnapshotUpdate(peer.id, roomKey, { custom: 'live edits' }, 'revision-1', '2')
    expect(JSON.parse(viewer.send.mock.calls[0]![0] as string)).toEqual({
      type: 'COLLAB_SNAPSHOT_UPDATE',
      payload: { roomKey, snapshot: { custom: 'live edits' }, persistedRevision: 'revision-1', leaseEpoch: '2' }
    })
  })

  it('does not cache a delayed snapshot from a superseded persisted revision', () => {
    const roomKey = 'snapshot-delayed-revision:page:xml'
    const { peer } = createPeer('snapshot-delayed-editor')
    collaborationState.joinRoom(peer, token(roomKey))
    collaborationState.syncLeaseState(roomKey, lease('user-1', '2'))
    collaborationState.applySnapshotUpdate(peer.id, roomKey, { custom: 'new XML' }, 'revision-2', '2')
    collaborationState.applySnapshotUpdate(peer.id, roomKey, { custom: 'old XML' }, 'revision-1', '2')
    const viewer = createPeer('snapshot-delayed-viewer')
    collaborationState.joinRoom(viewer.peer, { ...token(roomKey), persistedRevision: 'revision-2' })
    expect(viewer.send.mock.calls.map(([raw]) => JSON.parse(raw as string))).toContainEqual({
      type: 'COLLAB_SNAPSHOT_SYNC',
      payload: { roomKey, snapshot: { custom: 'new XML' }, persistedRevision: 'revision-2', leaseEpoch: '2' }
    })
  })

  it('does not shorten a renewed lease when an older heartbeat arrives in the same epoch', () => {
    const { peer, send } = createPeer('peer-delayed-heartbeat')
    const roomKey = 'project-heartbeat:page:xml'
    collaborationState.joinRoom(peer, token(roomKey))
    collaborationState.syncLeaseState(roomKey, lease('curator', '5', '2026-10-14T12:01:21Z'))
    send.mockClear()
    collaborationState.syncLeaseState(roomKey, lease('curator', '5', '2026-10-14T12:00:45Z'))
    expect(send).not.toHaveBeenCalled()
  })

  it('accepts a newer owner even when its expiry is earlier than the previous deadline', () => {
    const { peer } = createPeer('peer-new-owner-deadline')
    const roomKey = 'project-new-owner:page:xml'
    collaborationState.joinRoom(peer, token(roomKey))
    collaborationState.syncLeaseState(roomKey, lease('previous-editor', '5', '2026-10-14T12:01:21Z'))
    collaborationState.syncLeaseState(roomKey, lease('curator', '6', '2026-10-14T12:00:45Z'))
    expect(collaborationState.getProjectPageSummaries('project-new-owner')[0]?.editor?.user.id).toBe('curator')
  })

  it('compares decimal-string epochs numerically across digit boundaries', () => {
    const { peer, send } = createPeer('peer-decimal-epochs')
    const roomKey = 'project-decimal:page:xml'
    collaborationState.joinRoom(peer, token(roomKey))
    collaborationState.syncLeaseState(roomKey, lease('previous-editor', '9'))
    send.mockClear()
    collaborationState.syncLeaseState(roomKey, lease('new-editor', '10'))
    expect(send).toHaveBeenCalled()
    expect(collaborationState.getProjectPageSummaries('project-decimal')[0]?.editor?.user.id).toBe('new-editor')
  })

  it('rejects delayed epochs beyond JavaScript number precision', () => {
    const { peer, send } = createPeer('peer-large-epochs')
    const roomKey = 'project-large:page:xml'
    collaborationState.joinRoom(peer, token(roomKey))
    collaborationState.syncLeaseState(roomKey, lease('new-editor', '9007199254740993'))
    send.mockClear()
    collaborationState.syncLeaseState(roomKey, lease('previous-editor', '9007199254740992'))
    expect(send).not.toHaveBeenCalled()
    expect(collaborationState.getProjectPageSummaries('project-large')[0]?.editor?.user.id).toBe('new-editor')
  })

  it('pushes persisted revision changes to room members', () => {
    const { peer, send } = createPeer('peer-revision')
    const roomKey = 'project-2:page-2:xml-2'
    collaborationState.joinRoom(peer, token(roomKey))
    send.mockClear()

    collaborationState.markPersistedRevision(roomKey, 'revision-2', {
      reason: 'annotation-saved',
      sourceUserId: 'user-1'
    })

    expect(send).toHaveBeenCalledOnce()
    expect(JSON.parse(send.mock.calls[0]![0] as string)).toMatchObject({
      type: 'COLLAB_REVISION_CHANGED',
      payload: {
        roomKey,
        persistedRevision: 'revision-2',
        sourceUserId: 'user-1',
        reloadRequired: false
      }
    })
  })

  it('ignores delayed lease states from an older ownership epoch', () => {
    const { peer, send } = createPeer('peer-stale-lease')
    const roomKey = 'project-3:page-3:xml-3'
    collaborationState.joinRoom(peer, token(roomKey))

    collaborationState.syncLeaseState(roomKey, lease('new-editor', '4'), 'force-takeover')
    send.mockClear()

    collaborationState.syncLeaseState(roomKey, lease('previous-editor', '3'), 'lease-heartbeat')

    expect(send).not.toHaveBeenCalled()
  })

  it('still broadcasts heartbeat updates from the current ownership epoch', () => {
    const globalBroadcast = vi.spyOn(websocketUtils, 'broadcast')
    const { peer, send } = createPeer('peer-current-lease')
    const roomKey = 'project-4:page-4:xml-4'
    collaborationState.joinRoom(peer, token(roomKey))

    collaborationState.syncLeaseState(roomKey, lease('editor', '5'), 'lease-claimed')
    send.mockClear()
    globalBroadcast.mockClear()

    collaborationState.syncLeaseState(
      roomKey,
      lease('editor', '5', '2099-01-01T00:00:10Z'),
      'lease-heartbeat'
    )

    expect(send).toHaveBeenCalledOnce()
    expect(globalBroadcast).not.toHaveBeenCalled()
    expect(JSON.parse(send.mock.calls[0]![0] as string)).toMatchObject({
      type: 'COLLAB_ROOM_STATE',
      payload: {
        lease: {
          editor: {
            user: {
              id: 'editor'
            }
          },
          leaseEpoch: '5',
          expiresAt: '2099-01-01T00:00:10Z'
        }
      }
    })
  })

  it('broadcasts lease state once per material change and ignores identical updates', () => {
    const globalBroadcast = vi.spyOn(websocketUtils, 'broadcast')
    const { peer, send } = createPeer('peer-material-lease')
    const roomKey = 'project-5:page-5:xml-5'
    collaborationState.joinRoom(peer, token(roomKey))

    collaborationState.syncLeaseState(roomKey, lease('editor', '5'), 'lease-claimed')
    send.mockClear()
    globalBroadcast.mockClear()

    collaborationState.syncLeaseState(roomKey, lease('editor', '5'), 'lease-heartbeat')

    expect(send).not.toHaveBeenCalled()
    expect(globalBroadcast).not.toHaveBeenCalled()

    collaborationState.syncLeaseState(
      roomKey,
      lease('editor', '5', '2099-01-01T00:00:10Z'),
      'lease-heartbeat'
    )

    expect(send).toHaveBeenCalledOnce()
    expect(globalBroadcast).not.toHaveBeenCalled()
    send.mockClear()

    collaborationState.syncLeaseState(
      roomKey,
      lease('editor', '6', '2099-01-01T00:00:10Z'),
      'lease-heartbeat'
    )

    expect(send).toHaveBeenCalledOnce()
    expect(globalBroadcast).not.toHaveBeenCalled()
    send.mockClear()

    collaborationState.syncLeaseState(roomKey, {
      ...lease('editor', '6', '2099-01-01T00:00:10Z'),
      pendingTakeover: {
        requester: {
          id: 'requester',
          username: 'requester',
          displayName: 'Requester'
        },
        requestedAt: '2026-01-01T00:00:10Z',
        force: false
      }
    }, 'takeover-requested')

    expect(send).toHaveBeenCalledOnce()
    expect(JSON.parse(send.mock.calls[0]![0] as string)).toMatchObject({
      type: 'COLLAB_ROOM_STATE'
    })
    expect(globalBroadcast).toHaveBeenCalledOnce()
  })

  it('does not globally rebroadcast page summaries for routine presence heartbeats', () => {
    const globalBroadcast = vi.spyOn(websocketUtils, 'broadcast')
    const { peer } = createPeer('peer-presence-heartbeat')
    const roomKey = 'project-6:page-6:xml-6'
    collaborationState.joinRoom(peer, token(roomKey))
    collaborationState.syncLeaseState(roomKey, lease('user-1', '1'), 'lease-claimed')
    globalBroadcast.mockClear()

    collaborationState.updatePresence(peer.id, roomKey, {
      active: true,
      cursor: { x: 12, y: 24 }
    })

    expect(globalBroadcast).not.toHaveBeenCalled()

    collaborationState.updatePresence(peer.id, roomKey, { active: false })

    expect(globalBroadcast).toHaveBeenCalledOnce()
  })
})
