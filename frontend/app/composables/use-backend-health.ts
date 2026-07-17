let healthCheckInterval: ReturnType<typeof setInterval> | null = null
let healthRealtimeUnsubscribe: (() => void) | null = null
let healthConnectionStop: (() => void) | null = null

export const useBackendHealth = () => {
  const isHealthy = useState<boolean>('backend.health.isHealthy', () => false)
  const isChecking = useState<boolean>('backend.health.isChecking', () => false)
  const lastCheckTime = useState<Date | null>('backend.health.lastCheckTime', () => null)
  const consecutiveFailures = useState<number>('backend.health.consecutiveFailures', () => 0)
  const realtime = useRealtimeSocket()

  const applyHealthStatus = (status: string) => {
    lastCheckTime.value = new Date()
    if (status === 'UP') {
      isHealthy.value = true
      consecutiveFailures.value = 0
      return
    }

    isHealthy.value = false
    consecutiveFailures.value++
  }

  const checkHealth = async (silent = false): Promise<boolean> => {
    if (import.meta.server) return false

    if (!silent) isChecking.value = true

    try {
      const response = await $fetch<{ status?: string }>('/api/health/backend', {
        timeout: 5000,
        retry: false
      })

      if (response.status === 'UP') {
        isHealthy.value = true
        consecutiveFailures.value = 0
        lastCheckTime.value = new Date()
        return true
      }

      consecutiveFailures.value++
      isHealthy.value = false
      if (!silent) {
        console.warn(`Backend health check reported non-UP status: ${response.status || 'UNKNOWN'}`)
      }
    } catch (error) {
      consecutiveFailures.value++
      isHealthy.value = false
      if (!silent) {
        console.warn(`Backend health check failed (${consecutiveFailures.value} consecutive failures):`, error)
      }
    } finally {
      if (!silent) isChecking.value = false
    }

    return false
  }

  const startMonitoring = (intervalMs = 30000) => {
    if (import.meta.server) return

    if (!healthRealtimeUnsubscribe) {
      healthRealtimeUnsubscribe = realtime.subscribe((message) => {
        if (message.type !== 'BACKEND_STATUS') return
        const status = (message.payload as { status?: unknown } | null)?.status
        if (typeof status === 'string') {
          applyHealthStatus(status)
        }
      })
    }

    if (!healthConnectionStop) {
      healthConnectionStop = watch([
        () => realtime.connectionStatus.value,
        () => realtime.isPageVisible.value
      ], ([status, pageVisible]) => {
        if (status === 'connected' || !pageVisible) {
          if (healthCheckInterval) {
            clearInterval(healthCheckInterval)
            healthCheckInterval = null
          }
          return
        }

        if (!healthCheckInterval) {
          healthCheckInterval = setInterval(() => {
            void checkHealth(true)
          }, intervalMs)
        }
        if (status === 'disconnected' || status === 'error') {
          void checkHealth(true)
        }
      }, { immediate: true })
    }
  }

  const stopMonitoring = () => {
    if (healthCheckInterval) {
      clearInterval(healthCheckInterval)
      healthCheckInterval = null
    }
    healthRealtimeUnsubscribe?.()
    healthRealtimeUnsubscribe = null
    healthConnectionStop?.()
    healthConnectionStop = null
  }

  const retryConnection = async () => {
    if (import.meta.server) return false

    isChecking.value = true
    return await checkHealth()
  }

  return {
    isHealthy: readonly(isHealthy),
    isChecking: readonly(isChecking),
    lastCheckTime: readonly(lastCheckTime),
    consecutiveFailures: readonly(consecutiveFailures),
    checkHealth,
    startMonitoring,
    stopMonitoring,
    retryConnection
  }
}
