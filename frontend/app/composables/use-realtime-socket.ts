type RealtimeConnectionStatus = 'idle' | 'connecting' | 'connected' | 'disconnected' | 'error'

type RealtimeMessage = {
  type?: string
  payload?: unknown
}

const realtimeSubscribers = new Set<(message: RealtimeMessage) => void>()

export const useRealtimeSocket = () => {
  const wsConnection = useState<WebSocket | null>('realtime.ws', () => null)
  const reconnectTimeout = useState<ReturnType<typeof setTimeout> | null>('realtime.reconnectTimeout', () => null)
  const shouldReconnect = useState<boolean>('realtime.shouldReconnect', () => true)
  const lifecycleBound = useState<boolean>('realtime.lifecycleBound', () => false)
  const connectionStatus = useState<RealtimeConnectionStatus>('realtime.connectionStatus', () => 'idle')
  const isPageVisible = useState<boolean>('realtime.isPageVisible', () => true)

  const clearReconnectTimeout = () => {
    if (reconnectTimeout.value) {
      clearTimeout(reconnectTimeout.value)
      reconnectTimeout.value = null
    }
  }

  const closeActiveConnection = (code = 1000, reason = 'client disconnect') => {
    const ws = wsConnection.value
    if (!ws) return

    try {
      if (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING) {
        ws.close(code, reason)
      }
    } catch {
      // Browser may already be tearing down the socket during unload.
    } finally {
      wsConnection.value = null
      connectionStatus.value = 'idle'
    }
  }

  const bindLifecycleHandlers = () => {
    if (import.meta.server || lifecycleBound.value) return
    isPageVisible.value = document.visibilityState === 'visible'

    const handleVisibilityChange = () => {
      isPageVisible.value = document.visibilityState === 'visible'
    }

    const handlePageHide = () => {
      isPageVisible.value = false
      shouldReconnect.value = false
      clearReconnectTimeout()
      closeActiveConnection(1001, 'page unload')
    }

    const handlePageShow = () => {
      isPageVisible.value = document.visibilityState === 'visible'
      shouldReconnect.value = true
      const { loggedIn } = useUserSession()
      if (loggedIn.value) {
        connect()
      }
    }

    window.addEventListener('pagehide', handlePageHide)
    window.addEventListener('pageshow', handlePageShow)
    document.addEventListener('visibilitychange', handleVisibilityChange)
    lifecycleBound.value = true
  }

  const dispatch = (message: RealtimeMessage) => {
    for (const subscriber of realtimeSubscribers) {
      try {
        subscriber(message)
      } catch (error) {
        console.error('[realtime-socket] Subscriber error:', error)
      }
    }
  }

  const connect = () => {
    if (import.meta.server) return
    bindLifecycleHandlers()

    const existing = wsConnection.value
    if (existing?.readyState === WebSocket.OPEN || existing?.readyState === WebSocket.CONNECTING) {
      return
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const wsUrl = `${protocol}//${window.location.host}/_ws`

    try {
      shouldReconnect.value = true
      clearReconnectTimeout()
      connectionStatus.value = 'connecting'

      const ws = new WebSocket(wsUrl)

      ws.onmessage = (event) => {
        try {
          const message = JSON.parse(event.data) as RealtimeMessage
          if (message.type === 'CONNECTED') {
            connectionStatus.value = 'connected'
          }
          dispatch(message)
        } catch (error) {
          console.error('[realtime-socket] Failed to parse message:', error)
        }
      }

      ws.onclose = () => {
        connectionStatus.value = 'disconnected'
        if (wsConnection.value === ws) {
          wsConnection.value = null
        }

        if (!shouldReconnect.value) {
          return
        }

        reconnectTimeout.value = setTimeout(() => {
          reconnectTimeout.value = null
          const { loggedIn } = useUserSession()
          if (loggedIn.value) {
            connect()
          }
        }, 5000)
      }

      ws.onerror = (error) => {
        console.error('[realtime-socket] WebSocket error:', error)
        connectionStatus.value = 'error'
      }

      wsConnection.value = ws
    } catch (error) {
      console.error('[realtime-socket] Failed to connect:', error)
      connectionStatus.value = 'error'
    }
  }

  const disconnect = () => {
    shouldReconnect.value = false
    clearReconnectTimeout()
    closeActiveConnection()
  }

  const send = (message: Record<string, unknown>) => {
    const ws = wsConnection.value
    if (!ws || ws.readyState !== WebSocket.OPEN) {
      return false
    }

    ws.send(JSON.stringify(message))
    return true
  }

  const subscribe = (listener: (message: RealtimeMessage) => void) => {
    realtimeSubscribers.add(listener)
    return () => {
      realtimeSubscribers.delete(listener)
    }
  }

  return {
    connectionStatus: readonly(connectionStatus),
    isPageVisible: readonly(isPageVisible),
    connect,
    disconnect,
    send,
    subscribe
  }
}
