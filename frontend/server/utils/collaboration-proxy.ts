import type { H3Event } from 'h3'
import type {
  CollaborationLeaseResponse,
  CollaborationRoomBootstrap
} from '~/types/collaboration'
import { backendFetch } from './backendFetch'
import { collaborationState } from './collaboration-state'
import {
  signCollaborationRoomToken,
  type CollaborationRoomTokenPayload
} from './collaboration-token'

type LeaseOperation = 'join' | 'heartbeat' | 'request' | 'respond' | 'release'

interface CollaborationProxyTarget {
  basePath: string
  annotationRoute: CollaborationRoomTokenPayload['annotationRoute']
}

export function projectCollaborationTarget(event: H3Event): CollaborationProxyTarget {
  const projectId = getRouterParam(event, 'projectId')
  const pageId = getRouterParam(event, 'pageId')
  const xmlId = getRouterParam(event, 'xmlId')

  if (!projectId || !pageId || !xmlId) {
    throw createError({ statusCode: 400, statusMessage: 'Missing collaboration route parameters' })
  }

  return {
    basePath: `/projects/${projectId}/pages/${pageId}/annotations/${xmlId}/collaboration`,
    annotationRoute: { scope: 'PROJECT', projectId, pageId }
  }
}

export function datasetCollaborationTarget(event: H3Event): CollaborationProxyTarget {
  const workspaceId = getRouterParam(event, 'workspaceId')
  const datasetId = getRouterParam(event, 'datasetId')
  const itemId = getRouterParam(event, 'itemId')
  const xmlId = getRouterParam(event, 'xmlId')

  if (!workspaceId || !datasetId || !itemId || !xmlId) {
    throw createError({ statusCode: 400, statusMessage: 'Missing collaboration route parameters' })
  }

  return {
    basePath: `/workspaces/${workspaceId}/datasets/${datasetId}/items/${itemId}/annotations/${xmlId}/collaboration`,
    annotationRoute: { scope: 'DATASET', workspaceId, datasetId, itemId }
  }
}

export async function proxyCollaborationBootstrap(
  event: H3Event,
  target: CollaborationProxyTarget
): Promise<CollaborationRoomBootstrap> {
  const response = await backendFetch(event, `${target.basePath}/bootstrap`)
  const bootstrap = await response.json().catch(() => null) as
    | Omit<CollaborationRoomBootstrap, 'token'>
    | null

  if (!response.ok || !bootstrap) {
    throw createError({ statusCode: response.status, statusMessage: response.statusText })
  }

  const runtimeConfig = useRuntimeConfig(event)
  const tokenPayload: CollaborationRoomTokenPayload = {
    sub: bootstrap.user.id,
    username: bootstrap.user.username,
    displayName: bootstrap.user.displayName,
    avatar: bootstrap.user.avatar ?? null,
    workspaceId: bootstrap.workspaceId,
    projectId: bootstrap.projectId,
    pageId: bootstrap.pageId,
    xmlId: bootstrap.xmlId,
    roomKey: bootstrap.roomKey,
    canEdit: bootstrap.canEdit,
    canForceTakeover: bootstrap.canForceTakeover,
    persistedRevision: bootstrap.persistedRevision,
    annotationRoute: target.annotationRoute,
    exp: Date.now() + (60 * 60 * 1000)
  }

  return {
    ...bootstrap,
    token: signCollaborationRoomToken(tokenPayload, runtimeConfig.collaborationSecret)
  }
}

export async function proxyCollaborationLease<T extends CollaborationLeaseResponse>(
  event: H3Event,
  target: CollaborationProxyTarget,
  operation: LeaseOperation,
  body: Record<string, unknown>,
  reason: string
): Promise<T> {
  const response = await backendFetch(event, `${target.basePath}/lease/${operation}`, {
    method: 'POST',
    body: JSON.stringify(body),
    headers: { 'Content-Type': 'application/json' }
  })

  const data = await response.json().catch(() => null) as T | null
  if (!response.ok || !data) {
    throw createError({ statusCode: response.status, statusMessage: response.statusText })
  }

  collaborationState.syncLeaseState(data.roomKey, data.lease, reason)
  return data
}
