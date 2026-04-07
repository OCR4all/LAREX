import { websocketUtils } from '../../utils/websocket'

export default defineEventHandler(async (event) => {
  const runtimeConfig = useRuntimeConfig(event)
  const secret = getHeader(event, 'x-larex-notification-bridge-secret')

  if (!secret || secret !== runtimeConfig.notificationBridgeSecret) {
    throw createError({ statusCode: 401, statusMessage: 'Unauthorized notification bridge request' })
  }

  const body = await readBody(event) as {
    userId?: string
    notification?: Record<string, unknown>
    source?: string
  } | null

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
