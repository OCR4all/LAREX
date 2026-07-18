import {
  datasetCollaborationTarget,
  proxyCollaborationBootstrap
} from '#server/utils/collaboration-proxy'

export default defineEventHandler(async (event) => {
  return proxyCollaborationBootstrap(event, datasetCollaborationTarget(event))
})
