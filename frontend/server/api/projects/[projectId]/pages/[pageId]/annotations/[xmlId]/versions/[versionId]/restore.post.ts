import { backendFetch } from '#server/utils/backendFetch'
import { collaborationState } from '#server/utils/collaboration-state'

export default defineEventHandler(async (event) => {
  const projectId = getRouterParam(event, 'projectId')
  const pageId = getRouterParam(event, 'pageId')
  const xmlId = getRouterParam(event, 'xmlId')
  const versionId = getRouterParam(event, 'versionId')

  if (!projectId || !pageId || !xmlId || !versionId) {
    throw createError({
      statusCode: 400,
      statusMessage: 'Missing restore route parameters'
    })
  }

  const response = await backendFetch(
    event,
    `/projects/${projectId}/pages/${pageId}/annotations/${xmlId}/versions/${versionId}/restore`,
    { method: 'POST' }
  )

  if (!response.ok) {
    const errorBody = await response.json().catch(() => null) as { message?: string } | null
    throw createError({
      statusCode: response.status,
      statusMessage: errorBody?.message || response.statusText
    })
  }

  const roomKey = `${projectId}:${pageId}:${xmlId}`
  collaborationState.markPersistedReloadRequired(roomKey, 'restore')
  return null
})
