export default defineEventHandler(async (event) => {
  const method = (event.node.req.method || 'POST').toUpperCase()
  if (method !== 'POST') {
    throw createError({ statusCode: 405, statusMessage: 'Method Not Allowed' })
  }

  const projectId = getRouterParam(event, 'projectId')
  const pageId = getRouterParam(event, 'pageId')
  if (!projectId || !pageId) {
    throw createError({ statusCode: 400, statusMessage: 'Missing path parameters' })
  }

  const config = useRuntimeConfig(event)
  const targetUrl = `${config.apiBaseInternal}/public/pat/projects/${projectId}/pages/${pageId}/xml`

  return await proxyRequest(event, targetUrl, {
    headers: {
      authorization: getHeader(event, 'authorization') || undefined,
      host: undefined,
      'content-type': undefined
    }
  })
})
