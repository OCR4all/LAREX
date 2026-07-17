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

  const revisionResponse = await backendFetch(
    event,
    `/projects/${projectId}/pages/${pageId}/annotations/${xmlId}/collaboration/revision`
  ).catch(() => null)
  if (!revisionResponse) {
    collaborationState.markPersistedReloadRequired(`${projectId}:${pageId}:${xmlId}`, 'restore')
    return null
  }
  const revision = await revisionResponse.json().catch(() => null) as { persistedRevision?: string } | null
  if (!revisionResponse.ok || !revision?.persistedRevision) {
    collaborationState.markPersistedReloadRequired(`${projectId}:${pageId}:${xmlId}`, 'restore')
    return null
  }

  const session = await getUserSession(event)
  collaborationState.markPersistedRevision(`${projectId}:${pageId}:${xmlId}`, revision.persistedRevision, {
    reason: 'restore',
    sourceUserId: session.user?.id ?? null,
    reloadRequired: true
  })
  return revision
})
