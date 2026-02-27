export default defineEventHandler(async (event) => {
  const path = getRouterParam(event, 'path') || ''

  if (!path) {
    throw createError({
      statusCode: 400,
      statusMessage: 'Image path required'
    })
  }

  const { user, secure } = await getUserSession(event)

  if (!user || !secure?.accessToken) {
    throw createError({
      statusCode: 401,
      statusMessage: 'Unauthorized'
    })
  }

  try {
    await refreshTokenIfExpired(event, { user, secure })
  } catch {
    throw createError({
      statusCode: 401,
      statusMessage: 'Token refresh failed'
    })
  }

  const updatedSession = await getUserSession(event)
  const config = useRuntimeConfig(event)
  const backendUrl = `${config.apiBaseInternal}/profile/images/${path}`

  const response = await fetch(backendUrl, {
    headers: {
      Authorization: `Bearer ${updatedSession.secure?.accessToken}`
    }
  })

  if (!response.ok) {
    throw createError({
      statusCode: response.status,
      statusMessage: response.statusText
    })
  }

  const contentType = response.headers.get('content-type')
  const contentLength = response.headers.get('content-length')
  const cacheControl = response.headers.get('cache-control')

  if (contentType) {
    setHeader(event, 'Content-Type', contentType)
  }
  if (contentLength) {
    setHeader(event, 'Content-Length', contentLength)
  }
  if (cacheControl) {
    setHeader(event, 'Cache-Control', cacheControl)
  }

  try {
    await sendStream(event, response.body!)
    return
  } catch (error: unknown) {
    if (isExpectedDisconnectError(error)) {
      console.debug(`[api/profile/images] Client disconnected while streaming ${path}`)
      return
    }
    throw error
  }
})
