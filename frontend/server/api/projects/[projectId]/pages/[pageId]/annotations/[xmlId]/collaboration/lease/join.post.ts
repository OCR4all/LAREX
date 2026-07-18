import {
  projectCollaborationTarget,
  proxyCollaborationLease
} from '#server/utils/collaboration-proxy'
import type { CollaborationLeaseResponse } from '~/types/collaboration'

export default defineEventHandler(async (event) => {
  const body = await readBody(event).catch(() => null) as { instanceId?: string } | null
  return proxyCollaborationLease<CollaborationLeaseResponse>(
    event,
    projectCollaborationTarget(event),
    'join',
    { instanceId: body?.instanceId ?? null },
    'lease-claimed'
  )
})
