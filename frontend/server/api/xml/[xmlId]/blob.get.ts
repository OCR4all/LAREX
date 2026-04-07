export default defineEventHandler(async (event) => {
  const { xmlId } = getRouterParams(event)
  const query = getQuery(event)

  if (!xmlId) {
    throw createError({
      statusCode: 400,
      statusMessage: 'XML ID is required'
    })
  }

  const config = useRuntimeConfig()

  const { user, secure } = await getUserSession(event)
  if (!user || !secure?.accessToken) {
    throw createError({
      statusCode: 401,
      statusMessage: 'Unauthorized'
    })
  }

  try {
    const { refreshTokenIfExpired } = await import('#server/utils/auth')
    await refreshTokenIfExpired(event, { user, secure })
  } catch {
    throw createError({
      statusCode: 401,
      statusMessage: 'Token refresh failed'
    })
  }

  const updatedSession = await getUserSession(event)

  const headers = {
    Authorization: `Bearer ${updatedSession.secure?.accessToken}`
  }

  const queryString = Object.keys(query).length > 0
    ? '?' + new URLSearchParams(query as Record<string, string>).toString()
    : ''

  const backendUrl = `${config.apiBaseInternal}/xml/${xmlId}/blob${queryString}`

  try {
    const response = await fetch(backendUrl, {
      method: 'GET',
      headers
    })

    if (!response.ok) {
      throw createError({
        statusCode: response.status,
        statusMessage: response.statusText
      })
    }

    const contentType = response.headers.get('content-type') || 'application/xml'
    const contentDisposition = response.headers.get('content-disposition')
    const contentLength = response.headers.get('content-length')

    setHeader(event, 'Content-Type', contentType)
    if (contentDisposition) {
      setHeader(event, 'Content-Disposition', contentDisposition)
    }
    if (contentLength) {
      setHeader(event, 'Content-Length', contentLength)
    }

    await sendStream(event, response.body!)
    return
  } catch (error: unknown) {
    if (isExpectedDisconnectError(error)) {
      console.debug(`[api/xml/blob] Client disconnected while streaming XML blob ${xmlId}`)
      return
    }

    const resolvedError = error as { statusCode?: number, message?: string }
    throw createError({
      statusCode: resolvedError.statusCode || 500,
      statusMessage: resolvedError.message || 'Internal Server Error'
    })
  }
})
