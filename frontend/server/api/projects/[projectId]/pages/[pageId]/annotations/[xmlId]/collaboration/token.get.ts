import {
  projectCollaborationTarget,
  proxyCollaborationBootstrap
} from '#server/utils/collaboration-proxy'

export default defineEventHandler(async (event) => {
  return proxyCollaborationBootstrap(event, projectCollaborationTarget(event))
})
