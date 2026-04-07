/**
 * Composable to handle cache invalidation events from WebSocket.
 * Listens for CACHE_INVALIDATION messages and triggers appropriate refetches.
 */
export function useCacheInvalidation() {
  const isConnected = ref(false)
  const realtime = useRealtimeSocket()

  const callbacks = new Map<string, Set<(payload: CacheInvalidationPayload) => void>>()

  interface CacheInvalidationPayload {
    cacheType: 'PAGE_LIST' | 'PAGE_METADATA' | 'PROJECT'
    projectId: string
    pageId?: string
    timestamp: string
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
    realtime.connect()
  }

  /**
   * Legacy no-op for API compatibility. The shared realtime socket is app-scoped.
   */
  function disconnect() {
    return
  }

  watch(() => realtime.connectionStatus.value, (status) => {
    isConnected.value = status === 'connected'
  }, { immediate: true })

  onScopeDispose(realtime.subscribe((message) => {
    if (!message.type) return
    handleMessage(message as { type: string, payload: unknown })
  }))

  return {
    isConnected: readonly(isConnected),
    connect,
    disconnect,
    onInvalidation
  }
}
