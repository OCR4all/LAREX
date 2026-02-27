import { pageCacheUtils } from '../../../../utils/page-cache'

/**
 * GET /api/projects/{projectId}/pages
 * 
 * Returns the list of pages for a project with server-side caching.
 * Cache is invalidated via WebSocket when pages are modified.
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
    const { refreshTokenIfExpired } = await import('../../../../utils/auth')
    await refreshTokenIfExpired(event, { user, secure })
  } catch {
    throw createError({
      statusCode: 401,
      statusMessage: 'Token refresh failed'
    })
  }

  const updatedSession = await getUserSession(event)

  const cached = pageCacheUtils.getPageList(projectId)
  if (cached) {
    console.log(`[PageCache] Cache hit for project ${projectId} page list`)
    setHeader(event, 'X-Cache', 'HIT')
    return cached
  }

  console.log(`[PageCache] Cache miss for project ${projectId} page list`)
  setHeader(event, 'X-Cache', 'MISS')

  const backendUrl = `${config.apiBaseInternal}/projects/${projectId}/pages`
  const headers = {
    'Authorization': `Bearer ${updatedSession.secure?.accessToken}`,
    'Content-Type': 'application/json'
  }

  try {
    const data = await $fetch(backendUrl, {
      method: 'GET',
      headers,
      query: getQuery(event)
    })

    pageCacheUtils.setPageList(projectId, data)

    return data
  } catch (error: any) {
    const statusCode = error.response?.status || 500
    throw createError({
      statusCode,
      statusMessage: error.message || 'Failed to fetch pages'
    })
  }
})
