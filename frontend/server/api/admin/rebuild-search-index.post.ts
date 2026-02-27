/**
 * POST /api/admin/rebuild-search-index
 * 
 * Rebuild the global search index for all pages across all projects.
 * Admin only operation.
 */
export default defineEventHandler(async (event) => {
  const config = useRuntimeConfig(event)

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
  const accessToken = updatedSession.secure?.accessToken

  const backendUrl = `${config.apiBaseInternal}/admin/rebuild-search-index`
  const headers: Record<string, string> = {
    'Authorization': `Bearer ${accessToken}`
  }

  try {
    const data = await $fetch(backendUrl, {
      method: 'POST',
      headers
    })
    return data
  } catch (error: any) {
    throw createError({
      statusCode: error.statusCode || 500,
      statusMessage: error.statusMessage || 'Failed to rebuild global search index'
    })
  }
})
