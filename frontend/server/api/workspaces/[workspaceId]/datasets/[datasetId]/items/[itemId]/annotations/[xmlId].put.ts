import { backendFetch } from '#server/utils/backendFetch'
import { collaborationState } from '#server/utils/collaboration-state'

type RevisionResponse = {
  persistedRevision: string
}

export default defineEventHandler(async (event) => {
  const workspaceId = getRouterParam(event, 'workspaceId')
  const datasetId = getRouterParam(event, 'datasetId')
  const itemId = getRouterParam(event, 'itemId')
  const xmlId = getRouterParam(event, 'xmlId')

  if (!workspaceId || !datasetId || !itemId || !xmlId) {
    throw createError({ statusCode: 400, statusMessage: 'Missing dataset annotation route parameters' })
  }

  const body = await readBody(event)
  const annotationPath = `/workspaces/${workspaceId}/datasets/${datasetId}/items/${itemId}/annotations/${xmlId}`
  const response = await backendFetch(event, annotationPath, {
    method: 'PUT',
    body: JSON.stringify(body),
    headers: { 'Content-Type': 'application/json' }
  })

  if (!response.ok) {
    const errorBody = await response.json().catch(() => null) as { message?: string } | null
    throw createError({
      statusCode: response.status,
      statusMessage: errorBody?.message || response.statusText
    })
  }

  const revisionResponse = await backendFetch(event, `${annotationPath}/collaboration/revision`).catch(() => null)
  if (!revisionResponse) {
    console.warn(`[dataset-annotation-save] Saved ${xmlId}, but the persisted revision lookup failed`)
    return null
  }
  const revision = await revisionResponse.json().catch(() => null) as RevisionResponse | null
  if (!revisionResponse.ok || !revision?.persistedRevision) {
    console.warn(`[dataset-annotation-save] Saved ${xmlId}, but failed to resolve its persisted revision`)
    return null
  }

  const session = await getUserSession(event)
  const sourceUserId = session.user?.id ?? null
  collaborationState.markPersistedRevision(`${datasetId}:${itemId}:${xmlId}`, revision.persistedRevision, {
    reason: 'annotation-saved',
    sourceUserId
  })
  return revision
})
