export type TakeoverDecision = 'accept' | 'decline'
export type TakeoverHandoffMode = 'save' | 'discard'

export interface TakeoverResponseBody {
  decision: TakeoverDecision
  handoffMode: TakeoverHandoffMode
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
