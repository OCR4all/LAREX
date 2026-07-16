/**
 * GET /api/projects/{projectId}/pages/{pageId}
 *
 * Fetches one mutable page summary for targeted realtime updates.
 */
export default defineEventHandler(async (event) => {
  const projectId = getRouterParam(event, 'projectId')
  const pageId = getRouterParam(event, 'pageId')
  if (!projectId || !pageId) {
    throw createError({ statusCode: 400, statusMessage: 'Project ID and Page ID are required' })
  }

  const { user, secure } = await getUserSession(event)
  if (!user || !secure?.accessToken) {
    throw createError({ statusCode: 401, statusMessage: 'Unauthorized' })
  }

  try {
    const { refreshTokenIfExpired } = await import('#server/utils/auth')
    await refreshTokenIfExpired(event, { user, secure })
  } catch {
    throw createError({ statusCode: 401, statusMessage: 'Token refresh failed' })
  }

  const config = useRuntimeConfig(event)
  const updatedSession = await getUserSession(event)
  setHeader(event, 'Cache-Control', 'no-store')

  try {
    return await $fetch(`${config.apiBaseInternal}/projects/${projectId}/pages/${pageId}`, {
      headers: {
        'Authorization': `Bearer ${updatedSession.secure?.accessToken}`,
        'Content-Type': 'application/json'
      }
    })
  } catch (error: unknown) {
    const statusCode = Number((error as { response?: { status?: number } })?.response?.status ?? 500) || 500
    throw createError({
      statusCode,
      statusMessage: (error as { message?: string })?.message || 'Failed to fetch page'
    })
  }
})
