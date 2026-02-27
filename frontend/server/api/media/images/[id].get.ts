export default defineEventHandler(async (event) => {
  const imageId = getRouterParam(event, 'id')
  const { projectId } = getQuery(event)

  if (!imageId) {
    throw createError({ statusCode: 400, statusMessage: 'Image ID required' })
  }

  if (!projectId) {
    throw createError({ statusCode: 400, statusMessage: 'Project ID required' })
  }

  const config = useRuntimeConfig()

  const { user, secure } = await getUserSession(event)
  if (!user || !secure?.accessToken) {
    throw createError({ statusCode: 401, statusMessage: 'Unauthorized' })
  }

  try {
    await refreshTokenIfExpired(event, { user, secure })
  } catch {
    throw createError({ statusCode: 401, statusMessage: 'Token refresh failed' })
  }

  const updatedSession = await getUserSession(event)

  const backendUrl = `${config.apiBaseInternal}/projects/${projectId}/pages/images/${imageId}/blob`

  console.log('[api/media/images] Fetching:', backendUrl)

  const response = await fetch(backendUrl, {
    headers: {
      Authorization: `Bearer ${updatedSession.secure?.accessToken}`
    }
  })

  console.log('[api/media/images] Response status:', response.status)

  if (!response.ok) {
    throw createError({
      statusCode: response.status,
      statusMessage: response.statusText
    })
  }

  const contentType = response.headers.get('content-type')
  const contentLength = response.headers.get('content-length')

  if (contentType) {
    setHeader(event, 'Content-Type', contentType)
  }
  if (contentLength) {
    setHeader(event, 'Content-Length', contentLength)
  }

  try {
    await sendStream(event, response.body!)
    return
  } catch (error: unknown) {
    if (isExpectedDisconnectError(error)) {
      console.debug(`[api/media/images] Client disconnected while streaming image ${imageId}`)
      return
    }
    throw error
  }
})
