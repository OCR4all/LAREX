import type { Peer } from 'crossws'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { collaborationState } from '../collaboration-state'
import type { CollaborationRoomTokenPayload } from '../collaboration-token'

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
})
