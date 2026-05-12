export const useBackendHealth = () => {
  const isHealthy = useState<boolean>('backend.health.isHealthy', () => false)
  const isChecking = useState<boolean>('backend.health.isChecking', () => false)
  const lastCheckTime = useState<Date | null>('backend.health.lastCheckTime', () => null)
  const consecutiveFailures = useState<number>('backend.health.consecutiveFailures', () => 0)

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

  let healthCheckInterval: NodeJS.Timeout | null = null

  const startMonitoring = (intervalMs = 30000) => {
    if (import.meta.server) return

    if (healthCheckInterval) return

    healthCheckInterval = setInterval(() => {
      checkHealth(true)
    }, intervalMs)
  }

  const stopMonitoring = () => {
    if (healthCheckInterval) {
      clearInterval(healthCheckInterval)
      healthCheckInterval = null
    }
  }

  const retryConnection = async () => {
    if (import.meta.server) return false

    isChecking.value = true
    return await checkHealth()
  }

  if (!import.meta.server) {
    const instance = getCurrentInstance()
    if (instance) {
      onBeforeUnmount(() => {
        stopMonitoring()
      })
    }
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
