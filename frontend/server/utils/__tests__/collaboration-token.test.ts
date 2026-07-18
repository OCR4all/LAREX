import { describe, expect, it } from 'vitest'
import {
  collaborationTokenBelongsToUser,
  signCollaborationRoomToken,
  verifyCollaborationRoomToken,
  type CollaborationRoomTokenPayload
} from '../collaboration-token'

const SECRET = 'collaboration-test-secret'

function payload(overrides: Partial<CollaborationRoomTokenPayload> = {}): CollaborationRoomTokenPayload {
  return {
    sub: 'user-1',
    username: 'user',
    displayName: 'User',
    workspaceId: 'workspace-1',
    projectId: 'project-1',
    pageId: 'page-1',
    xmlId: 'xml-1',
    roomKey: 'project-1:page-1:xml-1',
    canEdit: true,
    canForceTakeover: false,
    persistedRevision: 'revision-1',
    annotationRoute: {
      scope: 'PROJECT',
      projectId: 'project-1',
      pageId: 'page-1'
    },
    exp: Date.now() + 60_000,
    ...overrides
  }
}

describe('collaboration room tokens', () => {
  it('round-trips the authoritative annotation route', () => {
    const tokenPayload = payload()
    const token = signCollaborationRoomToken(tokenPayload, SECRET)

    expect(verifyCollaborationRoomToken(token, SECRET)).toEqual(tokenPayload)
  })

  it('rejects a signed token without a renewal route', () => {
    const invalidPayload = { ...payload(), annotationRoute: undefined }
    const token = signCollaborationRoomToken(
      invalidPayload as unknown as CollaborationRoomTokenPayload,
      SECRET
    )

    expect(verifyCollaborationRoomToken(token, SECRET)).toBeNull()
  })

  it('accepts dataset renewal routes', () => {
    const tokenPayload = payload({
      annotationRoute: {
        scope: 'DATASET',
        workspaceId: 'workspace-1',
        datasetId: 'dataset-1',
        itemId: 'item-1'
      }
    })

    const token = signCollaborationRoomToken(tokenPayload, SECRET)
    expect(verifyCollaborationRoomToken(token, SECRET)?.annotationRoute).toEqual(tokenPayload.annotationRoute)
  })

  it('binds a room token to the authenticated WebSocket user', () => {
    const tokenPayload = payload({ sub: 'user-1' })

    expect(collaborationTokenBelongsToUser(tokenPayload, 'user-1')).toBe(true)
    expect(collaborationTokenBelongsToUser(tokenPayload, 'user-2')).toBe(false)
    expect(collaborationTokenBelongsToUser(tokenPayload, null)).toBe(false)
  })
})
