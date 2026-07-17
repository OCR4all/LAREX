import { createHmac, timingSafeEqual } from 'node:crypto'
import { websocketUtils } from '../../utils/websocket'

function normalizeAllowedIps(value: string | undefined): string[] {
  return (value ?? '')
    .split(',')
    .map(entry => entry.trim())
    .filter(Boolean)
}

function isPrivateOrLoopbackIp(ip: string): boolean {
  const normalized = ip.replace(/^::ffff:/, '')

  if (normalized === '127.0.0.1' || normalized === '::1') {
    return true
  }

  if (normalized.startsWith('10.') || normalized.startsWith('192.168.')) {
    return true
  }

  const match = normalized.match(/^172\.(\d{1,3})\./)
  if (!match) {
    return false
  }

  const secondOctet = Number(match[1])
  return Number.isFinite(secondOctet) && secondOctet >= 16 && secondOctet <= 31
}

function signBridgePayload(secret: string, timestamp: string, payload: string): string {
  return createHmac('sha256', secret)
    .update(`${timestamp}.${payload}`)
    .digest('hex')
}

function signaturesMatch(expected: string, received: string): boolean {
  if (!expected || !received || expected.length !== received.length) {
    return false
  }

  return timingSafeEqual(Buffer.from(expected, 'utf8'), Buffer.from(received, 'utf8'))
}

export default defineEventHandler(async (event) => {
  const runtimeConfig = useRuntimeConfig(event)
  const timestamp = getHeader(event, 'x-larex-notification-bridge-timestamp')
  const signature = getHeader(event, 'x-larex-notification-bridge-signature')
  const rawBody = await readRawBody(event, 'utf8')
  const requestIp = getRequestIP(event, { xForwardedFor: true }) ?? null

  if (!rawBody) {
    throw createError({ statusCode: 400, statusMessage: 'Missing notification bridge payload' })
  }

  const allowedIps = normalizeAllowedIps(runtimeConfig.notificationBridgeAllowedIps)
  if (allowedIps.length > 0) {
    if (!requestIp || !allowedIps.includes(requestIp)) {
      console.warn('[notification-bridge] Rejected request from non-allowlisted IP', { requestIp })
      throw createError({ statusCode: 403, statusMessage: 'Forbidden notification bridge source' })
    }
  } else if (runtimeConfig.notificationBridgeRequirePrivateIp && (!requestIp || !isPrivateOrLoopbackIp(requestIp))) {
    console.warn('[notification-bridge] Rejected request from non-private IP', { requestIp })
    throw createError({ statusCode: 403, statusMessage: 'Forbidden notification bridge source' })
  }

  if (!timestamp || !signature) {
    throw createError({ statusCode: 401, statusMessage: 'Missing notification bridge signature' })
  }

  const timestampMs = Number(timestamp)
  const now = Date.now()
  if (!Number.isFinite(timestampMs) || Math.abs(now - timestampMs) > runtimeConfig.notificationBridgeMaxSkewMs) {
    console.warn('[notification-bridge] Rejected request with stale timestamp', { requestIp, timestamp })
    throw createError({ statusCode: 401, statusMessage: 'Stale notification bridge request' })
  }

  const expectedSignature = signBridgePayload(runtimeConfig.notificationBridgeSecret, timestamp, rawBody)
  if (!signaturesMatch(expectedSignature, signature)) {
    console.warn('[notification-bridge] Rejected request with invalid signature', { requestIp })
    throw createError({ statusCode: 401, statusMessage: 'Unauthorized notification bridge request' })
  }

  let body: {
    userId?: string
    notification?: Record<string, unknown>
    event?: {
      type?: string
      payload?: Record<string, unknown>
    }
    source?: string
  } | null

  try {
    body = JSON.parse(rawBody) as {
      userId?: string
      notification?: Record<string, unknown>
      event?: {
        type?: string
        payload?: Record<string, unknown>
      }
      source?: string
    } | null
  } catch {
    throw createError({ statusCode: 400, statusMessage: 'Invalid notification bridge payload' })
  }

  const eventType = body?.event?.type
  const eventPayload = body?.event?.payload
  if (eventType === 'ACTION_RUN_UPDATED' || eventType === 'ACTION_PAGE_RESULT_IMPORTED') {
    if (!eventPayload || typeof eventPayload !== 'object') {
      throw createError({ statusCode: 400, statusMessage: 'Invalid action event bridge payload' })
    }
    const delivered = websocketUtils.broadcast({
      type: eventType,
      payload: eventPayload
    })
    return {
      success: true,
      delivered,
      source: body?.source ?? null
    }
  }

  if (eventType === 'JOB_UPDATED') {
    if (!eventPayload || typeof eventPayload !== 'object') {
      throw createError({ statusCode: 400, statusMessage: 'Invalid job event bridge payload' })
    }
    const message = {
      type: eventType,
      payload: eventPayload
    }
    const delivered = body?.userId
      ? websocketUtils.sendToUser(body.userId, { ...message, userId: body.userId })
      : websocketUtils.broadcast(message)
    return {
      success: true,
      delivered,
      source: body?.source ?? null
    }
  }

  if (!body?.userId || !body.notification || typeof body.notification !== 'object') {
    throw createError({ statusCode: 400, statusMessage: 'Invalid notification bridge payload' })
  }

  const delivered = websocketUtils.sendToUser(body.userId, {
    type: 'NOTIFICATION',
    payload: body.notification,
    userId: body.userId
  })

  return {
    success: true,
    delivered,
    source: body.source ?? null
  }
})
