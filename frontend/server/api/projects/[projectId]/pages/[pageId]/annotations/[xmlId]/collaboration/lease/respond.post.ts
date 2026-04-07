import { backendFetch } from '#server/utils/backendFetch'
import { collaborationState } from '#server/utils/collaboration-state'
import { websocketUtils } from '#server/utils/websocket'

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

  const body = await readBody(event)
  const decision = body?.decision === 'decline' ? 'decline' : 'accept'
  const handoffMode = body?.handoffMode === 'discard' ? 'discard' : 'save'
  const response = await backendFetch(
    event,
    `/projects/${projectId}/pages/${pageId}/annotations/${xmlId}/collaboration/lease/respond`,
    {
      method: 'POST',
      body: JSON.stringify({ decision, handoffMode }),
      headers: { 'Content-Type': 'application/json' }
    }
  )

  const data = await response.json().catch(() => null) as CollaborationLeaseResponse | null
  if (!response.ok || !data) {
    throw createError({ statusCode: response.status, statusMessage: response.statusText })
  }

  collaborationState.syncLeaseState(
    data.roomKey,
    data.lease,
    decision === 'accept'
      ? (handoffMode === 'discard' ? 'transfer-after-discard' : 'transfer-after-save')
      : 'takeover-declined'
  )
  websocketUtils.broadcast({ type: 'REFRESH_NOTIFICATIONS', payload: { scope: 'collaboration' } })
  return data
})
