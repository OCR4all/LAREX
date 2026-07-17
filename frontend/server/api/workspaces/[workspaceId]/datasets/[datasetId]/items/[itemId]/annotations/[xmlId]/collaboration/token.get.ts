import { backendFetch } from '#server/utils/backendFetch'
import { signCollaborationRoomToken, type CollaborationRoomTokenPayload } from '#server/utils/collaboration-token'

type BackendBootstrapResponse = {
  roomKey: string
  workspaceId: string
  projectId: string
  pageId: string
  xmlId: string
  persistedRevision: string
  canEdit: boolean
  canForceTakeover: boolean
  user: {
    id: string
    username: string
    displayName: string
    avatar?: string | null
  }
  lease: {
    editor: {
      user: {
        id: string
        username: string
        displayName: string
        avatar?: string | null
      }
      acquiredAt: string
    } | null
    pendingTakeover: {
      requester: {
        id: string
        username: string
        displayName: string
        avatar?: string | null
      }
      requestedAt: string
      force: boolean
    } | null
    leaseOwner: boolean
    leaseEpoch: number
    expiresAt?: string | null
  }
}

export default defineEventHandler(async (event) => {
  const workspaceId = getRouterParam(event, 'workspaceId')
  const datasetId = getRouterParam(event, 'datasetId')
  const itemId = getRouterParam(event, 'itemId')
  const xmlId = getRouterParam(event, 'xmlId')

  if (!workspaceId || !datasetId || !itemId || !xmlId) {
    throw createError({
      statusCode: 400,
      statusMessage: 'Missing collaboration route parameters'
    })
  }

  const response = await backendFetch(
    event,
    `/workspaces/${workspaceId}/datasets/${datasetId}/items/${itemId}/annotations/${xmlId}/collaboration/bootstrap`
  )

  if (!response.ok) {
    throw createError({
      statusCode: response.status,
      statusMessage: response.statusText
    })
  }

  const bootstrap = await response.json() as BackendBootstrapResponse
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
    annotationRoute: {
      scope: 'DATASET',
      workspaceId,
      datasetId,
      itemId
    },
    exp: Date.now() + (60 * 60 * 1000)
  }

  return {
    ...bootstrap,
    token: signCollaborationRoomToken(tokenPayload, runtimeConfig.collaborationSecret)
  }
})
