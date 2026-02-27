import { pageCacheUtils } from '../../../../utils/page-cache'

/**
 * POST /api/projects/{projectId}/pages/invalidate-cache
 * 
 * Invalidates the server-side page list cache for a project.
 * Should be called when pages are modified outside of the regular CRUD endpoints
 * (e.g., after bulk upload completes).
 */
export default defineEventHandler(async (event) => {
  const projectId = getRouterParam(event, 'projectId')
  if (!projectId) {
    throw createError({
      statusCode: 400,
      statusMessage: 'Project ID is required'
    })
  }

  const { user } = await getUserSession(event)
  if (!user) {
    throw createError({
      statusCode: 401,
      statusMessage: 'Unauthorized'
    })
  }

  pageCacheUtils.invalidatePageList(projectId)

  return { success: true, message: `Cache invalidated for project ${projectId}` }
})
