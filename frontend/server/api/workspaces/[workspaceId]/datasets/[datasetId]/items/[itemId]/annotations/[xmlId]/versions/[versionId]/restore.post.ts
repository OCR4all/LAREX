import { backendFetch } from '#server/utils/backendFetch'
import { collaborationState } from '#server/utils/collaboration-state'

export default defineEventHandler(async (event) => {
  const workspaceId = getRouterParam(event, 'workspaceId')
  const datasetId = getRouterParam(event, 'datasetId')
  const itemId = getRouterParam(event, 'itemId')
  const xmlId = getRouterParam(event, 'xmlId')
  const versionId = getRouterParam(event, 'versionId')

  if (!workspaceId || !datasetId || !itemId || !xmlId || !versionId) {
    throw createError({
      statusCode: 400,
      statusMessage: 'Missing restore route parameters'
    })
  }

  const response = await backendFetch(
    event,
    `/workspaces/${workspaceId}/datasets/${datasetId}/items/${itemId}/annotations/${xmlId}/versions/${versionId}/restore`,
    { method: 'POST' }
  )

  if (!response.ok) {
    const errorBody = await response.json().catch(() => null) as { message?: string } | null
    throw createError({
      statusCode: response.status,
      statusMessage: errorBody?.message || response.statusText
    })
  }

  const roomKey = `${datasetId}:${itemId}:${xmlId}`
  collaborationState.markPersistedReloadRequired(roomKey, 'restore')
  return null
})
