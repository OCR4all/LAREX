import type { Peer } from 'crossws'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { collaborationState } from '../collaboration-state'
import type { CollaborationRoomTokenPayload } from '../collaboration-token'
import type { CollaborationLeaseState } from '../../../app/types/collaboration'

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

function lease(editorId: string, leaseEpoch: number, expiresAt = '2099-01-01T00:00:00Z'): CollaborationLeaseState {
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
    leaseOwner: false,
    leaseEpoch,
    expiresAt
  }
}

afterEach(() => {
  for (const peerId of peerIds.splice(0)) {
    collaborationState.unregisterPeer(peerId)
  }
})

describe('collaboration state renewal protocol', () => {
  it('returns renewal targets only for editable rooms joined by the peer', () => {
    const { peer } = createPeer('peer-renewal')
    collaborationState.joinRoom(peer, token('project-1:page-1:xml-1'))
    collaborationState.joinRoom(peer, token('project-1:page-2:xml-2', false))

    expect(collaborationState.getLeaseRenewalTargets(peer.id, [
      'project-1:page-1:xml-1',
      'project-1:page-2:xml-2',
      'project-1:page-other:xml-other'
    ])).toEqual([{
      scope: 'PROJECT',
      projectId: 'project-1',
      pageId: 'page-1',
      xmlId: 'xml-1'
    }])
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

    collaborationState.syncLeaseState(roomKey, lease('new-editor', 4), 'force-takeover')
    send.mockClear()

    collaborationState.syncLeaseState(roomKey, lease('previous-editor', 3), 'lease-heartbeat')

    expect(send).not.toHaveBeenCalled()
  })

  it('still broadcasts heartbeat updates from the current ownership epoch', () => {
    const { peer, send } = createPeer('peer-current-lease')
    const roomKey = 'project-4:page-4:xml-4'
    collaborationState.joinRoom(peer, token(roomKey))

    collaborationState.syncLeaseState(roomKey, lease('editor', 5), 'lease-claimed')
    send.mockClear()

    collaborationState.syncLeaseState(
      roomKey,
      lease('editor', 5, '2099-01-01T00:00:10Z'),
      'lease-heartbeat'
    )

    expect(send).toHaveBeenCalledOnce()
    expect(JSON.parse(send.mock.calls[0]![0] as string)).toMatchObject({
      type: 'COLLAB_ROOM_STATE',
      payload: {
        lease: {
          editor: {
            user: {
              id: 'editor'
            }
          },
          leaseEpoch: 5,
          expiresAt: '2099-01-01T00:00:10Z'
        }
      }
    })
  })
})
