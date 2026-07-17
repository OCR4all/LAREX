import { backendFetch } from '#server/utils/backendFetch'
import { collaborationState } from '#server/utils/collaboration-state'

type RevisionResponse = {
  persistedRevision: string
}

export default defineEventHandler(async (event) => {
  const projectId = getRouterParam(event, 'projectId')
  const pageId = getRouterParam(event, 'pageId')
  const xmlId = getRouterParam(event, 'xmlId')

  if (!projectId || !pageId || !xmlId) {
    throw createError({
      statusCode: 400,
      statusMessage: 'Missing annotation route parameters'
    })
  }

  const body = await readBody(event)
  const response = await backendFetch(
    event,
    `/projects/${projectId}/pages/${pageId}/annotations/${xmlId}`,
    {
      method: 'PUT',
      body: JSON.stringify(body),
      headers: {
        'Content-Type': 'application/json'
      }
    }
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
    console.warn(`[annotation-save] Saved ${xmlId}, but the persisted revision lookup failed`)
    return null
  }
  const revision = await revisionResponse.json().catch(() => null) as RevisionResponse | null
  if (!revisionResponse.ok || !revision?.persistedRevision) {
    console.warn(`[annotation-save] Saved ${xmlId}, but failed to resolve its persisted revision`)
    return null
  }

  const session = await getUserSession(event)
  const sourceUserId = session.user?.id ?? null
  collaborationState.markPersistedRevision(`${projectId}:${pageId}:${xmlId}`, revision.persistedRevision, {
    reason: 'annotation-saved',
    sourceUserId
  })
  return revision
})
