import { useWebSocket } from '@vueuse/core'

type RealtimeConnectionStatus = 'idle' | 'connecting' | 'connected' | 'disconnected' | 'error'

type RealtimeMessage = {
  type?: string
  payload?: unknown
}

const realtimeSockets = new WeakMap<ReturnType<typeof useNuxtApp>, ReturnType<typeof createRealtimeSocket>>()

export function useRealtimeSocket() {
  const app = useNuxtApp()
  let realtime = realtimeSockets.get(app)
  if (!realtime) {
    realtime = createRealtimeSocket()
    realtimeSockets.set(app, realtime)
  }
  return realtime
}

function createRealtimeSocket() {
  const subscribers = new Set<(message: RealtimeMessage) => void>()
  const status = ref<RealtimeConnectionStatus>('idle')
  const isPageVisible = ref(true)
  const loggedIn = import.meta.server ? ref(false) : useUserSession().loggedIn
  let lifecycleBound = false

  const dispatch = (message: RealtimeMessage) => {
    for (const subscriber of subscribers) {
      try {
        subscriber(message)
      } catch (error) {
        console.error('[realtime-socket] Subscriber error:', error)
      }
    }
  }

  // VueUse reads the URL when dialing, including after the reconnect delay.
  const transport = useWebSocket(() => {
    if (!loggedIn.value) return undefined
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    return `${protocol}//${window.location.host}/_ws`
  }, {
    immediate: false,
    autoConnect: false,
    autoClose: false,
    autoReconnect: { delay: 5000 },
    onConnected: () => {
      status.value = 'connecting'
    },
    onMessage: (_, event) => {
      try {
        const message = JSON.parse(event.data) as RealtimeMessage
        if (message.type === 'CONNECTED') {
          status.value = 'connected'
        }
        dispatch(message)
      } catch (error) {
        console.error('[realtime-socket] Failed to parse message:', error)
      }
    },
    onDisconnected: (socket) => {
      if (transport.ws.value === socket) {
        status.value = 'disconnected'
      }
    },
    onError: (socket, error) => {
      if (transport.ws.value === socket) {
        console.error('[realtime-socket] WebSocket error:', error)
        status.value = 'error'
      }
    }
  })

  const connectionStatus = computed<RealtimeConnectionStatus>(() => {
    return transport.status.value === 'CONNECTING' && status.value !== 'error'
      ? 'connecting'
      : status.value
  })

  const closeActiveConnection = (reason = 'client disconnect') => {
    transport.close(1000, reason)
    status.value = 'idle'
  }

  const bindLifecycleHandlers = () => {
    if (lifecycleBound) return
    isPageVisible.value = document.visibilityState === 'visible'

    const handleVisibilityChange = () => {
      isPageVisible.value = document.visibilityState === 'visible'
    }

    const handlePageHide = () => {
      isPageVisible.value = false
      closeActiveConnection('page unload')
    }

    const handlePageShow = () => {
      isPageVisible.value = document.visibilityState === 'visible'
      connect()
    }

    window.addEventListener('pagehide', handlePageHide)
    window.addEventListener('pageshow', handlePageShow)
    document.addEventListener('visibilitychange', handleVisibilityChange)
    lifecycleBound = true
  }

  const connect = () => {
    if (import.meta.server || !loggedIn.value) return
    bindLifecycleHandlers()

    if (transport.status.value !== 'CLOSED') return

    try {
      status.value = 'connecting'
      transport.open()
    } catch (error) {
      console.error('[realtime-socket] Failed to connect:', error)
      status.value = 'error'
    }
  }

  const disconnect = () => {
    closeActiveConnection()
  }

  const send = (message: Record<string, unknown>) => {
    const socket = transport.ws.value
    if (!socket || socket.readyState !== WebSocket.OPEN) return false
    return transport.send(JSON.stringify(message), false)
  }

  const subscribe = (listener: (message: RealtimeMessage) => void) => {
    subscribers.add(listener)
    return () => {
      subscribers.delete(listener)
    }
  }

  return {
    connectionStatus,
    isPageVisible: readonly(isPageVisible),
    connect,
    disconnect,
    send,
    subscribe
  }
}
