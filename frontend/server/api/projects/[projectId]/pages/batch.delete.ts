import { pageCacheUtils } from '../../../../utils/page-cache'

/**
 * DELETE /api/projects/{projectId}/pages/batch
 *
 * Deletes multiple pages and invalidates page caches.
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

  let pageIds: string[] = []
  try {
    const body = await readBody(event)
    if (Array.isArray(body)) {
      pageIds = body.filter((value): value is string => typeof value === 'string' && value.length > 0)
    }
  } catch {
    // Keep empty list; backend validation will handle invalid payloads.
  }

  const backendUrl = `${config.apiBaseInternal}/projects/${projectId}/pages/batch`
  const headers = {
    'Authorization': `Bearer ${updatedSession.secure?.accessToken}`,
    'Content-Type': 'application/json'
  }

  try {
    const data = await $fetch<{ deletedCount: number, requestedCount: number }>(backendUrl, {
      method: 'DELETE',
      headers,
      body: pageIds
    })

    pageCacheUtils.invalidatePageList(projectId)
    for (const pageId of pageIds) {
      pageCacheUtils.invalidatePageMetadata(projectId, pageId)
    }

    return data
  } catch (error: unknown) {
    const resolvedError = error as { response?: { status?: number }, message?: string }
    const statusCode = resolvedError.response?.status || 500
    throw createError({
      statusCode,
      statusMessage: resolvedError.message || 'Failed to delete pages'
    })
  }
})
