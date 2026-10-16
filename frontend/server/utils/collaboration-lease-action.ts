import type { PageDto } from '~/types/page-dto'

export type TakeoverDecision = 'accept' | 'decline'
export type TakeoverHandoffMode = 'save' | 'discard'

export interface TakeoverResponseBody {
  decision: TakeoverDecision
  handoffMode: TakeoverHandoffMode
}

export function parseTakeoverRequestBody(value: unknown) {
  const body = value as { force?: boolean, instanceId?: string, annotations?: PageDto, expectedLeaseEpoch?: string } | null
  const force = body?.force === true
  const instanceId = typeof body?.instanceId === 'string' ? body.instanceId.trim() : ''
  if (!instanceId) {
    throw createError({ statusCode: 400, statusMessage: 'Missing collaboration instance ID' })
  }
  if (body?.annotations != null) {
    if (!force || typeof body.expectedLeaseEpoch !== 'string' || !/^\d+$/.test(body.expectedLeaseEpoch)) {
      throw createError({ statusCode: 400, statusMessage: 'Saving before takeover requires force and expectedLeaseEpoch' })
    }
    return { force, instanceId, annotations: body.annotations, expectedLeaseEpoch: body.expectedLeaseEpoch }
  }
  return { force, instanceId }
}

export function parseTakeoverResponseBody(value: unknown): TakeoverResponseBody {
  if (!value || typeof value !== 'object') {
    throw createError({ statusCode: 400, statusMessage: 'Missing takeover response body' })
  }

  const body = value as Record<string, unknown>
  if (body.decision !== 'accept' && body.decision !== 'decline') {
    throw createError({ statusCode: 400, statusMessage: 'decision must be accept or decline' })
  }
  if (body.handoffMode !== 'save' && body.handoffMode !== 'discard') {
    throw createError({ statusCode: 400, statusMessage: 'handoffMode must be save or discard' })
  }

  return {
    decision: body.decision,
    handoffMode: body.handoffMode
  }
}
