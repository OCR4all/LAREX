import {
  projectCollaborationTarget,
  proxyCollaborationLease
} from '#server/utils/collaboration-proxy'
import type { CollaborationLeaseActionResponse } from '~/types/collaboration'

export default defineEventHandler(async (event) => {
  const body = await readBody(event).catch(() => null) as { force?: boolean } | null
  const force = body?.force === true
  return proxyCollaborationLease<CollaborationLeaseActionResponse>(
    event,
    projectCollaborationTarget(event),
    'request',
    { force },
    force ? 'force-takeover' : 'takeover-requested'
  )
})
