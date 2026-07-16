/**
 * GET /api/projects/{projectId}/pages
 *
 * Returns the current list of pages for a project.
 *
 * Page summaries include mutable workflow and effective-lock state. Those values
 * can change through page operations, tasks, Actions, and collaboration, so this
 * endpoint must not use the server-side page-list cache.
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

  setHeader(event, 'Cache-Control', 'no-store')

  const backendUrl = `${config.apiBaseInternal}/projects/${projectId}/pages`
  const headers = {
    'Authorization': `Bearer ${updatedSession.secure?.accessToken}`,
    'Content-Type': 'application/json'
  }

  try {
    return await $fetch(backendUrl, {
      method: 'GET',
      headers,
      query: getQuery(event)
    })
  } catch (error: unknown) {
    const statusCode = Number(
      (error as { response?: { status?: number } })?.response?.status ?? 500
    ) || 500
    throw createError({
      statusCode,
      statusMessage: (error as { message?: string })?.message || 'Failed to fetch pages'
    })
  }
})
