export default defineEventHandler(async (event) => {
  const config = useRuntimeConfig(event)
  const path = event.node.req.url?.replace('/api/v1/public/actions/', '') || ''
  const targetUrl = `${config.apiBaseInternal}/public/actions/${path}`

  try {
    return await proxyRequest(event, targetUrl, {
      headers: {
        'authorization': event.node.req.headers.authorization,
        'host': undefined,
        'content-type': undefined
      }
    })
  } catch (error: unknown) {
    if (isExpectedDisconnectError(error)) {
      console.debug('[action-public-proxy] Client disconnected during Action callback', { path, targetUrl })
      return
    }

    const resolvedError = error as { message?: string, statusCode?: number }
    console.error('[action-public-proxy] Proxy request failed:', {
      path,
      targetUrl,
      error: resolvedError.message,
      statusCode: resolvedError.statusCode
    })

    throw createError({
      statusCode: resolvedError.statusCode || 500,
      statusMessage: resolvedError.message || 'Action callback proxy failed'
    })
  }
})
