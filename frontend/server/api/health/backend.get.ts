export default defineEventHandler(async (event) => {
  const config = useRuntimeConfig(event)

  try {
    const backendUrl = config.apiBaseInternal || 'http://app:8080/api/v1'
    const healthUrl = backendUrl.replace('/api/v1', '') + '/actuator/health'

    const session = await getUserSession(event)
    const accessToken = session.secure?.accessToken
    const headers = new Headers({
      'Content-Type': 'application/json'
    })

    if (accessToken) {
      headers.set('Authorization', `Bearer ${accessToken}`)
    }

    const response = await fetch(healthUrl, {
      method: 'GET',
      headers,
      signal: AbortSignal.timeout(5000)
    })

    // Actuator can be protected although the backend is reachable and serving API calls.
    if (response.status === 401 || response.status === 403) {
      return {
        status: 'UP',
        timestamp: new Date().toISOString(),
        details: {
          protected: true,
          httpStatus: response.status
        }
      }
    }

    if (!response.ok) {
      return {
        status: 'DOWN',
        timestamp: new Date().toISOString(),
        error: `HTTP ${response.status}`
      }
    }

    const data = await response.json()

    return {
      status: data?.status || 'UNKNOWN',
      timestamp: new Date().toISOString(),
      details: data
    }
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : String(error)
    return {
      status: 'DOWN',
      timestamp: new Date().toISOString(),
      error: message || 'Connection failed'
    }
  }
})
