export const useResilientFetch = <T>(
  url: string | (() => string),
  options: any = {}
) => {
  const { isHealthy } = useBackendHealth()

  const retryFetch = async (
    fetchUrl: string,
    fetchOptions: any = {},
    maxRetries = 3,
    baseDelay = 1000
  ): Promise<{ data: T | null, error: any }> => {
    let lastError: any

    for (let attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        const response = await $fetch<T>(fetchUrl, {
          timeout: 10000,
          ...fetchOptions
        })
        return { data: response, error: null }
      } catch (error: any) {
        lastError = error

        const isRetryableError = (
          !error.status // Network error
          || error.status >= 500 // Server error
          || error.status === 0 // Connection refused
        )

        if (!isRetryableError || attempt >= maxRetries) {
          break
        }

        const delay = baseDelay * Math.pow(1.5, attempt)
        console.warn(`API call failed, retrying in ${delay}ms... (attempt ${attempt + 1}/${maxRetries + 1})`)
        await new Promise(resolve => setTimeout(resolve, delay))
      }
    }

    return { data: null, error: lastError }
  }

  const fetchFn = () => typeof url === 'function' ? url() : url

  const { data, error, status, refresh, pending } = useLazyFetch<T>(fetchFn, {
    default: () => null as T,
    ...options
  })

  const retryWithBackoff = async () => {
    const currentUrl = fetchFn()
    if (!currentUrl) return

    const { data: retryData, error: retryError } = await retryFetch(
      currentUrl,
      options,
      5, // More retries for manual retry
      2000
    )

    if (!retryError) {
      await refresh()
    }

    return !retryError
  }

  return {
    data,
    error,
    status,
    pending,
    refresh,
    retryWithBackoff,
    isBackendHealthy: isHealthy
  }
}
