import { backendFetch } from '#server/utils/backendFetch'
import { collaborationState } from '#server/utils/collaboration-state'

type CollaborationLeaseResponse = {
  roomKey: string
  lease: {
    editor: {
      user: { id: string }
      acquiredAt: string
    } | null
    pendingTakeover: {
      requester: { id: string }
      requestedAt: string
      force: boolean
    } | null
    leaseOwner: boolean
    leaseEpoch: number
  }
}

export default defineEventHandler(async (event) => {
  const projectId = getRouterParam(event, 'projectId')
  const pageId = getRouterParam(event, 'pageId')
  const xmlId = getRouterParam(event, 'xmlId')

  if (!projectId || !pageId || !xmlId) {
    throw createError({ statusCode: 400, statusMessage: 'Missing collaboration route parameters' })
  }

  const body = await readBody(event).catch(() => null) as { instanceId?: string } | null
  const response = await backendFetch(
    event,
    `/projects/${projectId}/pages/${pageId}/annotations/${xmlId}/collaboration/lease/release`,
    {
      method: 'POST',
      body: JSON.stringify({ instanceId: body?.instanceId ?? null }),
      headers: { 'Content-Type': 'application/json' }
    }
  )

  const data = await response.json().catch(() => null) as CollaborationLeaseResponse | null
  if (!response.ok || !data) {
    throw createError({ statusCode: response.status, statusMessage: response.statusText })
  }

  collaborationState.syncLeaseState(data.roomKey, data.lease, 'lease-released')
  return data
})
