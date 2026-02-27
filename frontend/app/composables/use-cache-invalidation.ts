/**
 * Composable to handle cache invalidation events from WebSocket.
 * Listens for CACHE_INVALIDATION messages and triggers appropriate refetches.
 */
export function useCacheInvalidation() {
  const wsConnection = useState<WebSocket | null>('cache-invalidation.ws', () => null)
  const reconnectTimeout = useState<ReturnType<typeof setTimeout> | null>('cache-invalidation.reconnectTimeout', () => null)
  const shouldReconnect = useState<boolean>('cache-invalidation.shouldReconnect', () => true)
  const lifecycleBound = useState<boolean>('cache-invalidation.lifecycleBound', () => false)
  const isConnected = ref(false)

  const callbacks = new Map<string, Set<(payload: CacheInvalidationPayload) => void>>()

  interface CacheInvalidationPayload {
    cacheType: 'PAGE_LIST' | 'PAGE_METADATA' | 'PROJECT'
    projectId: string
    pageId?: string
    timestamp: string
  }

  function clearReconnectTimeout() {
    if (reconnectTimeout.value) {
      clearTimeout(reconnectTimeout.value)
      reconnectTimeout.value = null
    }
  }

  function closeActiveConnection(code = 1000, reason = 'client disconnect') {
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
      isConnected.value = false
    }
  }

  function bindLifecycleHandlers() {
    if (import.meta.server || lifecycleBound.value) return

    const handlePageHide = () => {
      shouldReconnect.value = false
      clearReconnectTimeout()
      closeActiveConnection(1001, 'page unload')
    }

    const handlePageShow = () => {
      shouldReconnect.value = true
    }

    window.addEventListener('pagehide', handlePageHide)
    window.addEventListener('pageshow', handlePageShow)
    lifecycleBound.value = true
  }

  /**
   * Register a callback for cache invalidation events
   */
  function onInvalidation(
    cacheType: string,
    callback: (payload: CacheInvalidationPayload) => void
  ): () => void {
    if (!callbacks.has(cacheType)) {
      callbacks.set(cacheType, new Set())
    }
    callbacks.get(cacheType)!.add(callback)

    return () => {
      callbacks.get(cacheType)?.delete(callback)
    }
  }

  /**
   * Handle incoming WebSocket message
   */
  function handleMessage(message: { type: string, payload: unknown }) {
    if (message.type !== 'CACHE_INVALIDATION') return

    const payload = message.payload as CacheInvalidationPayload
    console.log('[CacheInvalidation] Received:', payload)

    const typeCallbacks = callbacks.get(payload.cacheType)
    if (typeCallbacks) {
      for (const callback of typeCallbacks) {
        try {
          callback(payload)
        } catch (error) {
          console.error('[CacheInvalidation] Callback error:', error)
        }
      }
    }

    const allCallbacks = callbacks.get('ALL')
    if (allCallbacks) {
      for (const callback of allCallbacks) {
        try {
          callback(payload)
        } catch (error) {
          console.error('[CacheInvalidation] Callback error:', error)
        }
      }
    }
  }

  /**
   * Connect to WebSocket (reuses existing connection from notifications)
   */
  function connect() {
    if (import.meta.server) return
    bindLifecycleHandlers()
    if (wsConnection.value?.readyState === WebSocket.OPEN || wsConnection.value?.readyState === WebSocket.CONNECTING) {
      isConnected.value = true
      return
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const wsUrl = `${protocol}//${window.location.host}/_ws`

    try {
      shouldReconnect.value = true
      clearReconnectTimeout()
      const ws = new WebSocket(wsUrl)

      ws.onopen = () => {
        console.log('[CacheInvalidation] WebSocket connected')
        isConnected.value = true
      }

      ws.onmessage = (event) => {
        try {
          const message = JSON.parse(event.data)
          handleMessage(message)
        } catch {
          // Ignore malformed messages.
        }
      }

      ws.onclose = () => {
        console.log('[CacheInvalidation] WebSocket disconnected')
        isConnected.value = false
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
        console.error('[CacheInvalidation] WebSocket error:', error)
      }

      wsConnection.value = ws
    } catch (error) {
      console.error('[CacheInvalidation] Failed to connect:', error)
    }
  }

  /**
   * Disconnect WebSocket
   */
  function disconnect() {
    shouldReconnect.value = false
    clearReconnectTimeout()
    closeActiveConnection()
  }

  return {
    isConnected: readonly(isConnected),
    connect,
    disconnect,
    onInvalidation
  }
}
