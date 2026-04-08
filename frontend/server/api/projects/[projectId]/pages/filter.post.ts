/**
 * POST /api/projects/{projectId}/pages/filter
 *
 * Filter pages by multiple criteria with AND/OR logic.
 * Returns only the IDs of matching pages.
 */
export default defineEventHandler(async (event) => {
  const projectId = getRouterParam(event, 'projectId')
  if (!projectId) {
    throw createError({
      statusCode: 400,
      statusMessage: 'Project ID is required'
    })
  }

  const config = useRuntimeConfig(event)

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
  const accessToken = updatedSession.secure?.accessToken

  const body = await readBody(event)

  const backendUrl = `${config.apiBaseInternal}/projects/${projectId}/pages/filter`
  const headers: Record<string, string> = {
    'Authorization': `Bearer ${accessToken}`,
    'Content-Type': 'application/json'
  }

  try {
    return await $fetch(backendUrl, {
      method: 'POST',
      headers,
      body
    })
  } catch (error: any) {
    throw createError({
      statusCode: error.statusCode || 500,
      statusMessage: error.statusMessage || 'Failed to filter pages'
    })
  }
})
