import type { Peer } from 'crossws'

interface WebSocketMessage {
  type: string
  payload: unknown
  timestamp: string
  userId?: string
  requestId?: string
}

function logSendFailure(peerId: string, error: unknown, action: 'message' | 'broadcast') {
  if (isExpectedDisconnectError(error)) {
    console.debug(`WebSocket closed for peer ${peerId}`)
    return
  }

  if (action === 'message') {
    console.warn(`Failed to send message to peer ${peerId}:`, error)
    return
  }

  console.warn(`Failed to send broadcast message to peer ${peerId}:`, error)
}

const activePeers = new Map<string, Peer>()
const peerToUser = new Map<string, string>()

export const websocketUtils = {
  registerPeer(peer: Peer, userId?: string) {
    activePeers.set(peer.id, peer)
    if (userId) {
      peerToUser.set(peer.id, userId)
    }
    console.log(`Registered peer ${peer.id}${userId ? ` for user ${userId}` : ''}`)
  },

  unregisterPeer(peerId: string) {
    activePeers.delete(peerId)
    const userId = peerToUser.get(peerId)
    if (userId) {
      peerToUser.delete(peerId)
    }
    console.log(`Unregistered peer ${peerId}${userId ? ` for user ${userId}` : ''}`)

    if (activePeers.size === 0) {
      stopHealthBroadcast()
    }
  },

  sendToPeer(peerId: string, message: Partial<WebSocketMessage>) {
    const peer = activePeers.get(peerId)
    if (!peer) {
      return false
    }

    const fullMessage: WebSocketMessage = {
      type: message.type || 'UNKNOWN',
      payload: message.payload || {},
      timestamp: new Date().toISOString(),
      ...message
    }

    try {
      peer.send(JSON.stringify(fullMessage))
      return true
    } catch (error) {
      logSendFailure(peerId, error, 'message')
      this.unregisterPeer(peerId)
      return false
    }
  },

  sendToUser(userId: string, message: Partial<WebSocketMessage>) {
    for (const [peerId, storedUserId] of peerToUser.entries()) {
      if (storedUserId === userId) {
        return this.sendToPeer(peerId, message)
      }
    }
    return false
  },

  broadcast(message: Partial<WebSocketMessage>) {
    const fullMessage: WebSocketMessage = {
      type: message.type || 'BROADCAST',
      payload: message.payload || {},
      timestamp: new Date().toISOString(),
      ...message
    }

    let sentCount = 0
    const payload = JSON.stringify(fullMessage)

    for (const [peerId, peer] of activePeers.entries()) {
      try {
        peer.send(payload)
        sentCount++
      } catch (error) {
        logSendFailure(peerId, error, 'broadcast')
        this.unregisterPeer(peerId)
      }
    }

    if (sentCount > 0) {
      console.log(`Broadcast message sent to ${sentCount} peers`)
    }
    return sentCount
  },

  getStats() {
    return {
      totalConnections: activePeers.size,
      authenticatedUsers: peerToUser.size,
      peers: Array.from(activePeers.keys()),
      users: Array.from(new Set(peerToUser.values()))
    }
  }
}

let healthBroadcastInterval: NodeJS.Timeout | null = null

export const startHealthBroadcast = (intervalMs = 30000) => {
  if (healthBroadcastInterval) return

  healthBroadcastInterval = setInterval(async () => {
    try {
      const isHealthy = await checkBackendHealth()

      websocketUtils.broadcast({
        type: 'BACKEND_STATUS',
        payload: {
          status: isHealthy ? 'UP' : 'DOWN',
          timestamp: new Date().toISOString()
        }
      })
    } catch (error) {
      console.error('Health broadcast failed:', error)
    }
  }, intervalMs)

  console.log(`Started health broadcast every ${intervalMs}ms`)
}

export const stopHealthBroadcast = () => {
  if (healthBroadcastInterval) {
    clearInterval(healthBroadcastInterval)
    healthBroadcastInterval = null
    console.log('Stopped health broadcast')
  }
}

async function checkBackendHealth(): Promise<boolean> {
  try {
    const response = await $fetch<{ status?: string }>('/api/health/backend', {
      timeout: 5000
    })
    return response.status === 'UP'
  } catch {
    return false
  }
}
