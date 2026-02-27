import { pageCacheUtils } from '../../../../../utils/page-cache'

/**
 * DELETE /api/projects/{projectId}/pages/{pageId}
 * 
 * Deletes a page and invalidates the page list cache.
 */
export default defineEventHandler(async (event) => {
  const projectId = getRouterParam(event, 'projectId')
  const pageId = getRouterParam(event, 'pageId')

  if (!projectId || !pageId) {
    throw createError({
      statusCode: 400,
      statusMessage: 'Project ID and Page ID are required'
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
    const { refreshTokenIfExpired } = await import('../../../../../utils/auth')
    await refreshTokenIfExpired(event, { user, secure })
  } catch {
    throw createError({
      statusCode: 401,
      statusMessage: 'Token refresh failed'
    })
  }

  const updatedSession = await getUserSession(event)

  const backendUrl = `${config.apiBaseInternal}/projects/${projectId}/pages/${pageId}`
  const headers = {
    'Authorization': `Bearer ${updatedSession.secure?.accessToken}`,
    'Content-Type': 'application/json'
  }

  try {
    const data = await $fetch(backendUrl, {
      method: 'DELETE',
      headers
    })

    pageCacheUtils.invalidatePageList(projectId)
    pageCacheUtils.invalidatePageMetadata(projectId, pageId)

    return data
  } catch (error: any) {
    const statusCode = error.response?.status || 500
    throw createError({
      statusCode,
      statusMessage: error.message || 'Failed to delete page'
    })
  }
})
