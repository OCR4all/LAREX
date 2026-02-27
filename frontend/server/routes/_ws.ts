import { websocketUtils, startHealthBroadcast } from '../utils/websocket'
import type { Peer } from 'crossws'

function sendJson(peer: Peer, data: unknown) {
  const peerId = peer?.id ?? 'unknown'

  try {
    peer.send(JSON.stringify(data))
  } catch (error) {
    if (isExpectedDisconnectError(error)) {
      console.debug(`WebSocket closed for peer ${peerId}`)
      return
    }
    console.warn(`Failed to send WebSocket message to peer ${peerId}:`, error)
  }
}

/**
 * WebSocket route handler
 *
 * Handles real-time notifications and health status broadcasts.
 * Clients connect to /_ws to receive push notifications.
 */
export default defineWebSocketHandler({
  open(peer) {
    console.log(`WebSocket connected: ${peer.id}`)

    const userId = peer.request?.headers?.get('x-user-id') || undefined

    websocketUtils.registerPeer(peer, userId)

    startHealthBroadcast(30000)

    sendJson(peer, {
      type: 'CONNECTED',
      payload: {
        peerId: peer.id,
        timestamp: new Date().toISOString()
      }
    })
  },

  message(peer, message) {
    try {
      const rawMessage = message.text ? message.text() : String(message)
      const data = JSON.parse(rawMessage)

      switch (data.type) {
        case 'PING':
          sendJson(peer, {
            type: 'PONG',
            payload: { timestamp: new Date().toISOString() }
          })
          break

        case 'AUTH':
          if (data.payload?.userId) {
            websocketUtils.registerPeer(peer, data.payload.userId)
            sendJson(peer, {
              type: 'AUTH_ACK',
              payload: { authenticated: true }
            })
          }
          break

        default:
          console.log(`Unknown message type: ${data.type}`)
      }
    } catch (error) {
      console.error('Failed to parse WebSocket message:', error)
    }
  },

  close(peer) {
    const peerId = peer?.id
    if (!peerId) return

    console.log(`WebSocket disconnected: ${peerId}`)
    websocketUtils.unregisterPeer(peerId)
  },

  error(peer, error) {
    const peerId = peer?.id ?? 'unknown'
    if (isExpectedDisconnectError(error)) {
      console.debug(`WebSocket closed for peer ${peerId}`)
      return
    }
    console.error(`WebSocket error for peer ${peerId}:`, error)
  }
})
