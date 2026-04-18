import { backendFetch } from '#server/utils/backendFetch'
import { collaborationState } from '#server/utils/collaboration-state'

type CollaborationLeaseResponse = {
  roomKey: string
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
    throw createError({ statusCode: 400, statusMessage: 'Missing collaboration route parameters' })
  }

  const body = await readBody(event).catch(() => null) as { force?: boolean } | null
  const response = await backendFetch(
    event,
    `/workspaces/${workspaceId}/datasets/${datasetId}/items/${itemId}/annotations/${xmlId}/collaboration/lease/request`,
    {
      method: 'POST',
      body: JSON.stringify({ force: Boolean(body?.force) }),
      headers: { 'Content-Type': 'application/json' }
    }
  )

  const data = await response.json().catch(() => null) as CollaborationLeaseResponse | null
  if (!response.ok || !data) {
    throw createError({ statusCode: response.status, statusMessage: response.statusText })
  }

  collaborationState.syncLeaseState(data.roomKey, data.lease, 'takeover-requested')
  return data
})
