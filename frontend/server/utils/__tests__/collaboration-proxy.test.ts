import { afterEach, expect, it, vi } from 'vitest'
import type { H3Event } from 'h3'
import { proxyCollaborationBootstrap } from '../collaboration-proxy'
import { verifyCollaborationRoomToken } from '../collaboration-token'
import type { CollaborationAnnotationRoute } from '~/types/collaboration'

const backendFetch = vi.hoisted(() => vi.fn())
vi.mock('../backendFetch', () => ({ backendFetch }))
afterEach(() => vi.unstubAllGlobals())

it.each([
  { scope: 'PROJECT', projectId: 'source-project', pageId: 'source-page' },
  { scope: 'DATASET', workspaceId: 'workspace', datasetId: 'dataset', itemId: 'item' }
] satisfies CollaborationAnnotationRoute[])('exposes the authoritative $scope renewal route to the browser', async (annotationRoute) => {
  vi.stubGlobal('useRuntimeConfig', () => ({ collaborationSecret: 'test-secret' }))
  backendFetch.mockResolvedValue(new Response(JSON.stringify({
    workspaceId: 'workspace', projectId: 'source-project', pageId: 'source-page', xmlId: 'xml',
    roomKey: 'room', persistedRevision: 'revision', canEdit: true, canForceTakeover: true,
    user: { id: 'user', username: 'user', displayName: 'User' },
    lease: { editor: null, pendingTakeover: null, leaseEpoch: '1', expiresAt: null }
  })))
  const bootstrap = await proxyCollaborationBootstrap({} as H3Event, { basePath: '/annotation/collaboration', annotationRoute })
  expect(bootstrap.annotationRoute).toEqual(annotationRoute)
  expect(verifyCollaborationRoomToken(bootstrap.token, 'test-secret')?.annotationRoute).toEqual(annotationRoute)
})
