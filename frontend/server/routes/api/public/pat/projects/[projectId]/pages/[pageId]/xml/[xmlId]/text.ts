export default defineEventHandler(async (event) => {
  const method = (event.node.req.method || 'GET').toUpperCase()
  if (method !== 'GET' && method !== 'PUT') {
    throw createError({ statusCode: 405, statusMessage: 'Method Not Allowed' })
  }

  const projectId = getRouterParam(event, 'projectId')
  const pageId = getRouterParam(event, 'pageId')
  const xmlId = getRouterParam(event, 'xmlId')
  if (!projectId || !pageId || !xmlId) {
    throw createError({ statusCode: 400, statusMessage: 'Missing path parameters' })
  }

  const config = useRuntimeConfig(event)
  const targetUrl = `${config.apiBaseInternal}/public/pat/projects/${projectId}/pages/${pageId}/xml/${xmlId}/text`

  return await proxyRequest(event, targetUrl, {
    headers: {
      authorization: getHeader(event, 'authorization') || undefined,
      host: undefined
    }
  })
})
