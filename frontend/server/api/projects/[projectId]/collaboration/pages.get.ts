import { collaborationState } from '#server/utils/collaboration-state'

export default defineEventHandler(async (event) => {
  const projectId = getRouterParam(event, 'projectId')

  if (!projectId) {
    throw createError({ statusCode: 400, statusMessage: 'Missing projectId route parameter' })
  }

  return collaborationState.getProjectPageSummaries(projectId)
})
