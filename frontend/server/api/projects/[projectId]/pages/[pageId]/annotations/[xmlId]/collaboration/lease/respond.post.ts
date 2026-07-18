import { backendFetch } from '#server/utils/backendFetch'
import { collaborationState } from '#server/utils/collaboration-state'
import { parseTakeoverResponseBody } from '#server/utils/collaboration-lease-action'
import type { CollaborationLeaseActionResponse } from '~/types/collaboration'

export default defineEventHandler(async (event) => {
  const projectId = getRouterParam(event, 'projectId')
  const pageId = getRouterParam(event, 'pageId')
  const xmlId = getRouterParam(event, 'xmlId')

  if (!projectId || !pageId || !xmlId) {
    throw createError({ statusCode: 400, statusMessage: 'Missing collaboration route parameters' })
  }

  const { decision, handoffMode } = parseTakeoverResponseBody(
    await readBody(event).catch(() => null)
  )
  const response = await backendFetch(
    event,
    `/projects/${projectId}/pages/${pageId}/annotations/${xmlId}/collaboration/lease/respond`,
    {
      method: 'POST',
      body: JSON.stringify({ decision, handoffMode }),
      headers: { 'Content-Type': 'application/json' }
    }
  )

  const data = await response.json().catch(() => null) as CollaborationLeaseActionResponse | null
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
  return data
})
