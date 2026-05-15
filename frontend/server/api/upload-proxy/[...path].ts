/**
 * Specialized upload proxy for multipart/form-data requests.
 *
 * This proxy uses `proxyRequest` to stream binary data byte-for-byte without parsing,
 * preventing corruption that occurs when `readBody()` tries to parse multipart data as text
 * (which adds UTF-8 BOM markers like EF BB BF).
 *
 * CRITICAL: content-type header is set to undefined to allow the fetch client to automatically
 * detect the multipart boundary from the incoming stream. Setting it manually will break uploads.
 *
 * Handles endpoints:
 * - /upload-sessions/{sessionId}/chunks
 * - /workspaces/{workspaceId}/projects/import-package
 * - /projects/{projectId}/pages/{pageId}/images
 */
export default defineEventHandler(async (event) => {
  const path = event.node.req.url?.replace('/api/upload-proxy/', '') || ''

  if (path.startsWith('auth/')) {
    throw createError({ statusCode: 404, statusMessage: 'Not Found' })
  }

  const { user, secure } = await getUserSession(event)

  if (!user || !secure?.accessToken) {
    throw createError({ statusCode: 401, statusMessage: 'Unauthorized' })
  }

  try {
    await refreshTokenIfExpired(event, { user, secure })
  } catch (e) {
    console.error('[upload-proxy] Token refresh failed, continuing anyway:', e)
  }

  const updatedSession = await getUserSession(event)
  const config = useRuntimeConfig(event)
  const targetUrl = `${config.apiBaseInternal}/${path}`

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
      console.debug('[upload-proxy] Client disconnected during upload', { path, targetUrl })
      return
    }

    const resolvedError = error as { message?: string, statusCode?: number }
    console.error('[upload-proxy] Proxy request failed:', {
      path,
      targetUrl,
      error: resolvedError.message,
      statusCode: resolvedError.statusCode
    })

    throw createError({
      statusCode: resolvedError.statusCode || 500,
      statusMessage: resolvedError.message || 'Upload proxy failed'
    })
  }
})
