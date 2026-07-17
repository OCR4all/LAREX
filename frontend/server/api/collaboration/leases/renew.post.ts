import { backendFetch } from '#server/utils/backendFetch'
import { collaborationState, type CollaborationLeaseRenewalTarget } from '#server/utils/collaboration-state'
import type { CollaborationLeaseState } from '~/types/collaboration'

type LeaseRenewalResponse = {
  renewals: Array<{
    roomKey: string
    lease: CollaborationLeaseState
  }>
}

export default defineEventHandler(async (event) => {
  const body = await readBody(event).catch(() => null) as {
    instanceId?: string
    targets?: CollaborationLeaseRenewalTarget[]
  } | null

  if (!body?.instanceId || !Array.isArray(body.targets) || body.targets.length === 0 || body.targets.length > 100) {
    throw createError({ statusCode: 400, statusMessage: 'Invalid collaboration lease renewal batch' })
  }

  const response = await backendFetch(event, '/collaboration/leases/renew', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      instanceId: body.instanceId,
      targets: body.targets
    })
  })

  const data = await response.json().catch(() => null) as LeaseRenewalResponse | null
  if (!response.ok || !data) {
    throw createError({ statusCode: response.status, statusMessage: response.statusText })
  }

  for (const renewal of data.renewals) {
    collaborationState.syncLeaseState(renewal.roomKey, renewal.lease, 'lease-heartbeat')
  }

  return data
})
