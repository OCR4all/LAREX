export default defineEventHandler(async (event) => {
  const { user, secure } = await getUserSession(event)

  if (!user || !secure?.accessToken) {
    throw createError({ statusCode: 401, statusMessage: 'Unauthorized' })
  }

  try {
    await refreshTokenIfExpired(event, { user, secure })
  } catch (e) {
    console.error('[upload-proxy/profile/image] Token refresh failed:', e)
  }

  const updatedSession = await getUserSession(event)
  const config = useRuntimeConfig(event)
  const targetUrl = `${config.apiBaseInternal}/profile/image`

  try {
    return await proxyRequest(event, targetUrl, {
      headers: {
        'Authorization': `Bearer ${updatedSession.secure?.accessToken}`,
        'host': undefined,
        'content-type': undefined
      }
    })
  } catch (error: unknown) {
    if (isExpectedDisconnectError(error)) {
      console.debug('[upload-proxy/profile/image] Client disconnected during upload')
      return
    }

    const err = error as { message?: string, statusCode?: number }
    console.error('[upload-proxy/profile/image] Proxy request failed:', {
      error: err.message,
      statusCode: err.statusCode
    })

    throw createError({
      statusCode: err.statusCode || 500,
      statusMessage: err.message || 'Profile image upload failed'
    })
  }
})
