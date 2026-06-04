import { pageCacheUtils } from '#server/utils/page-cache'

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
  const body = await readBody(event)
  const backendUrl = `${config.apiBaseInternal}/projects/${projectId}/pages/sort-order`

  try {
    const data = await $fetch(backendUrl, {
      method: 'PUT',
      headers: {
        'authorization': `Bearer ${updatedSession.secure?.accessToken}`,
        'Content-Type': 'application/json'
      },
      body
    })

    pageCacheUtils.invalidatePageList(projectId)
    return data
  } catch (error: unknown) {
    const candidate = error as { response?: { status?: number }, message?: string } | null
    const statusCode = candidate?.response?.status || 500
    throw createError({
      statusCode,
      statusMessage: candidate?.message || 'Failed to update page order'
    })
  }
})
