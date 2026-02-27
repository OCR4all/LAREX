function getStatusCode(error: unknown): number {
  if (typeof error !== 'object' || error === null) return 500
  const response = 'response' in error ? (error.response as { status?: unknown } | undefined) : undefined
  return typeof response?.status === 'number' ? response.status : 500
}

function getErrorMessage(error: unknown): string {
  if (error instanceof Error) return error.message
  return 'Error serving file'
}

export default defineEventHandler(async (event) => {
  const config = useRuntimeConfig(event)

  const path = getRouterParam(event, 'path') || ''

  const { user, secure } = await getUserSession(event)

  if (!user || !secure?.accessToken) {
    throw createError({
      statusCode: 401,
      statusMessage: 'Unauthorized'
    })
  }

  try {
    const { refreshTokenIfExpired } = await import('../../utils/auth')
    await refreshTokenIfExpired(event, { user, secure })
  } catch {
    throw createError({
      statusCode: 401,
      statusMessage: 'Token refresh failed'
    })
  }

  const updatedSession = await getUserSession(event)

  const backendUrl = `${config.apiBaseInternal}/files/${path}`

  try {
    const response = await $fetch.raw(backendUrl, {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${updatedSession.secure?.accessToken}`
      }
    })

    const contentType = response.headers.get('content-type')
    const contentLength = response.headers.get('content-length')
    const cacheControl = response.headers.get('cache-control')
    const parsedContentLength = contentLength ? Number.parseInt(contentLength, 10) : Number.NaN

    if (contentType) setHeader(event, 'content-type', contentType)
    if (Number.isFinite(parsedContentLength)) setHeader(event, 'content-length', parsedContentLength)
    if (cacheControl) setHeader(event, 'cache-control', cacheControl)

    setHeader(event, 'cache-control', 'public, max-age=3600')

    return response._data
  } catch (error: unknown) {
    const statusCode = getStatusCode(error)

    if (statusCode === 401) {
      throw createError({
        statusCode: 401,
        statusMessage: 'Unauthorized'
      })
    }

    if (statusCode === 404) {
      throw createError({
        statusCode: 404,
        statusMessage: 'File not found'
      })
    }

    throw createError({
      statusCode,
      statusMessage: getErrorMessage(error)
    })
  }
})
