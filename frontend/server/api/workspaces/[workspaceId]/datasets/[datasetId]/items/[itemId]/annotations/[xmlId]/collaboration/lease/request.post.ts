import {
  datasetCollaborationTarget,
  proxyCollaborationLease
} from '#server/utils/collaboration-proxy'
import type { CollaborationLeaseActionResponse } from '~/types/collaboration'
import { parseTakeoverRequestBody } from '#server/utils/collaboration-lease-action'

export default defineEventHandler(async (event) => {
  const body = parseTakeoverRequestBody(await readBody(event).catch(() => null))
  return proxyCollaborationLease<CollaborationLeaseActionResponse>(
    event,
    datasetCollaborationTarget(event),
    'request',
    body,
    body.force ? 'force-takeover' : 'takeover-requested'
  )
})
