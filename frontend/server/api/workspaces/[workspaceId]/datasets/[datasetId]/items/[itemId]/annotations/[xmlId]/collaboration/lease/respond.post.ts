import { parseTakeoverResponseBody } from '#server/utils/collaboration-lease-action'
import {
  datasetCollaborationTarget,
  proxyCollaborationLease
} from '#server/utils/collaboration-proxy'
import type { CollaborationLeaseActionResponse } from '~/types/collaboration'

export default defineEventHandler(async (event) => {
  const { decision, handoffMode } = parseTakeoverResponseBody(
    await readBody(event).catch(() => null)
  )

  return proxyCollaborationLease<CollaborationLeaseActionResponse>(
    event,
    datasetCollaborationTarget(event),
    'respond',
    { decision, handoffMode },
    decision === 'accept'
      ? (handoffMode === 'discard' ? 'transfer-after-discard' : 'transfer-after-save')
      : 'takeover-declined'
  )
})
