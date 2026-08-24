import {
  projectCollaborationTarget,
  proxyCollaborationLease
} from '#server/utils/collaboration-proxy'
import type { CollaborationLeaseActionResponse } from '~/types/collaboration'

export default defineEventHandler(async (event) => {
  const body = await readBody(event).catch(() => null) as { force?: boolean, instanceId?: string } | null
  const force = body?.force === true
  const instanceId = body?.instanceId?.trim()
  if (!instanceId) {
    throw createError({ statusCode: 400, statusMessage: 'Missing collaboration instance ID' })
  }
  return proxyCollaborationLease<CollaborationLeaseActionResponse>(
    event,
    projectCollaborationTarget(event),
    'request',
    { force, instanceId },
    force ? 'force-takeover' : 'takeover-requested'
  )
})
