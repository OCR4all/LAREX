import type { Peer } from 'crossws'
import { collaborationState } from '#server/utils/collaboration-state'
import { verifyCollaborationRoomToken } from '#server/utils/collaboration-token'
import { isExpectedDisconnectError } from '#server/utils/disconnect-error'

function sendJson(peer: Peer, data: unknown) {
  try {
    peer.send(JSON.stringify(data))
  } catch (error) {
    if (isExpectedDisconnectError(error)) {
      return
    }
    console.warn(`[collaboration] Failed to send message to peer ${peer.id}:`, error)
  }
}

export default defineWebSocketHandler({
  open(peer) {
    collaborationState.registerPeer(peer)
    sendJson(peer, {
      type: 'COLLAB_CONNECTED',
      payload: {
        peerId: peer.id,
        timestamp: new Date().toISOString()
      }
    })
  },

  message(peer, message) {
    try {
      const rawMessage = message.text ? message.text() : String(message)
      const data = JSON.parse(rawMessage) as {
        type?: string
        payload?: Record<string, unknown>
      }

      switch (data.type) {
        case 'PING':
          sendJson(peer, {
            type: 'PONG',
            payload: { timestamp: new Date().toISOString() }
          })
          return

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
          return
      }
    } catch (error) {
      console.error('[collaboration] Failed to process WebSocket message:', error)
    }
  },

  close(peer) {
    collaborationState.unregisterPeer(peer.id)
  },

  error(peer, error) {
    if (isExpectedDisconnectError(error)) {
      return
    }
    console.error(`[collaboration] WebSocket error for peer ${peer.id}:`, error)
  }
})
