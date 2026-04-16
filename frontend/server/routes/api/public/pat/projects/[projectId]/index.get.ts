export default defineEventHandler(async (event) => {
  const method = (event.node.req.method || 'GET').toUpperCase()
  if (method !== 'GET') {
    throw createError({ statusCode: 405, statusMessage: 'Method Not Allowed' })
  }

  const projectId = getRouterParam(event, 'projectId')
  if (!projectId) {
    throw createError({ statusCode: 400, statusMessage: 'Missing path parameters' })
  }

  const config = useRuntimeConfig(event)
  const targetUrl = `${config.apiBaseInternal}/public/pat/projects/${projectId}`

  return await proxyRequest(event, targetUrl, {
    headers: {
      authorization: getHeader(event, 'authorization') || undefined,
      host: undefined
    }
  })
})
