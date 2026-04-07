import { websocketUtils, startHealthBroadcast } from '#server/utils/websocket'
import { collaborationState } from '#server/utils/collaboration-state'
import { verifyCollaborationRoomToken } from '#server/utils/collaboration-token'
import { isExpectedDisconnectError } from '#server/utils/disconnect-error'
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
 * Handles real-time notifications, cache invalidation, and collaboration events.
 * Clients connect to /_ws to receive push notifications.
 */
export default defineWebSocketHandler({
  open(peer) {
    console.log(`WebSocket connected: ${peer.id}`)

    const userId = peer.request?.headers?.get('x-user-id') || undefined

    websocketUtils.registerPeer(peer, userId)
    collaborationState.registerPeer(peer)

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

        case 'JOIN_ROOM': {
          const token = typeof data.payload?.token === 'string' ? data.payload.token : null
          if (!token) {
            sendJson(peer, {
              type: 'COLLAB_ERROR',
              payload: { message: 'Missing collaboration room token' }
            })
            return
          }

          const secret = useRuntimeConfig().collaborationSecret
          const payload = verifyCollaborationRoomToken(token, secret)
          if (!payload) {
            sendJson(peer, {
              type: 'COLLAB_ERROR',
              payload: { message: 'Invalid or expired collaboration room token' }
            })
            return
          }

          collaborationState.joinRoom(peer, payload)
          return
        }

        case 'UPDATE_PRESENCE': {
          const roomKey = typeof data.payload?.roomKey === 'string' ? data.payload.roomKey : null
          const presence = data.payload?.presence
          if (!roomKey || typeof presence !== 'object' || presence === null) {
            return
          }

          collaborationState.updatePresence(peer.id, roomKey, presence as Record<string, unknown>)
          return
        }

        case 'LEAVE_ROOM': {
          const roomKey = typeof data.payload?.roomKey === 'string' ? data.payload.roomKey : null
          if (!roomKey) return
          collaborationState.leaveRoom(peer.id, roomKey)
          return
        }

        case 'SNAPSHOT_UPDATE': {
          const roomKey = typeof data.payload?.roomKey === 'string' ? data.payload.roomKey : null
          const snapshot = data.payload?.snapshot
          if (!roomKey || !snapshot) return

          collaborationState.applySnapshotUpdate(peer.id, roomKey, snapshot)
          return
        }

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
    collaborationState.unregisterPeer(peerId)
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
