import { websocketUtils, startHealthBroadcast } from '#server/utils/websocket'
import { collaborationState } from '#server/utils/collaboration-state'
import {
  collaborationTokenBelongsToUser,
  verifyCollaborationRoomToken
} from '#server/utils/collaboration-token'
import { isExpectedDisconnectError } from '#server/utils/disconnect-error'
import type { Peer } from 'crossws'

const leaseRenewalsInFlight = new Set<string>()

async function resolveSessionUserId(peer: Peer): Promise<string | null> {
  const cookie = peer.request?.headers?.get('cookie')
  if (!cookie) return null

  try {
    const response = await useNitroApp().localFetch('/api/session', {
      headers: { cookie }
    })
    if (!response.ok) return null

    const session = await response.json() as {
      loggedIn?: boolean
      user?: { id?: unknown }
    }
    return session.loggedIn && typeof session.user?.id === 'string'
      ? session.user.id
      : null
  } catch (error) {
    console.warn(`Failed to authenticate WebSocket peer ${peer.id}:`, error)
    return null
  }
}

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

async function renewLeases(peer: Peer, payload: unknown) {
  if (leaseRenewalsInFlight.has(peer.id)) return

  const renewal = payload as { roomKeys?: unknown, instanceId?: unknown } | null
  const roomKeys = Array.isArray(renewal?.roomKeys)
    ? renewal.roomKeys.filter((value): value is string => typeof value === 'string' && Boolean(value)).slice(0, 100)
    : []
  const instanceId = typeof renewal?.instanceId === 'string' ? renewal.instanceId.trim() : ''
  if (roomKeys.length === 0 || !instanceId) {
    sendJson(peer, {
      type: 'COLLAB_LEASE_RENEWAL_FAILED',
      payload: { message: 'Invalid lease renewal payload' }
    })
    return
  }

  const targets = collaborationState.getLeaseRenewalTargets(peer.id, roomKeys)
  if (targets.length === 0) return

  const cookie = peer.request?.headers?.get('cookie')
  if (!cookie) {
    sendJson(peer, {
      type: 'COLLAB_LEASE_RENEWAL_FAILED',
      payload: { message: 'Missing authenticated session' }
    })
    return
  }

  leaseRenewalsInFlight.add(peer.id)
  try {
    const response = await useNitroApp().localFetch('/api/collaboration/leases/renew', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        cookie
      },
      body: JSON.stringify({ instanceId, targets })
    })

    if (!response.ok) {
      throw new Error(`Lease renewal failed with status ${response.status}`)
    }

    const data = await response.json() as { renewals?: Array<{ roomKey?: string }> }
    sendJson(peer, {
      type: 'COLLAB_LEASE_RENEWED',
      payload: {
        roomKeys: (data.renewals ?? []).map(item => item.roomKey).filter(Boolean),
        renewedAt: new Date().toISOString()
      }
    })
  } catch (error) {
    console.warn(`Failed to renew collaboration leases for peer ${peer.id}:`, error)
    sendJson(peer, {
      type: 'COLLAB_LEASE_RENEWAL_FAILED',
      payload: { message: 'Lease renewal failed' }
    })
  } finally {
    leaseRenewalsInFlight.delete(peer.id)
  }
}

/**
 * WebSocket route handler
 *
 * Handles real-time notifications, cache invalidation, and collaboration events.
 * Clients connect to /_ws to receive push notifications.
 */
export default defineWebSocketHandler({
  async open(peer) {
    console.log(`WebSocket connected: ${peer.id}`)

    const userId = await resolveSessionUserId(peer)
    if (!userId) {
      console.warn(`Rejected unauthenticated WebSocket peer ${peer.id}`)
      peer.close(4401, 'Authentication required')
      return
    }
    if (peer.websocket.readyState !== 1) return

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

        // Older clients sent their user ID here. Identity is now resolved
        // exclusively from the session cookie during the server handshake.
        case 'AUTH':
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
          if (!collaborationTokenBelongsToUser(payload, websocketUtils.getPeerUserId(peer.id))) {
            sendJson(peer, {
              type: 'COLLAB_ERROR',
              payload: { message: 'Collaboration room token does not belong to this session' }
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

        case 'LEASE_RENEW':
          void renewLeases(peer, data.payload)
          return

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
    leaseRenewalsInFlight.delete(peerId)
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
