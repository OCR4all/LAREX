export default defineEventHandler(async (event) => {
  const method = (event.node.req.method || 'GET').toUpperCase()
  if (method !== 'GET') {
    throw createError({ statusCode: 405, statusMessage: 'Method Not Allowed' })
  }

  const projectId = getRouterParam(event, 'projectId')
  const xmlId = getRouterParam(event, 'xmlId')
  if (!projectId || !xmlId) {
    throw createError({ statusCode: 400, statusMessage: 'Missing path parameters' })
  }

  const config = useRuntimeConfig(event)
  const query = getQuery(event) as Record<string, string | string[] | undefined>
  const queryString = new URLSearchParams(
    Object.entries(query)
      .flatMap(([key, value]) => {
        if (value == null) return []
        if (Array.isArray(value)) return value.map(v => [key, String(v)] as const)
        return [[key, String(value)] as const]
      })
  ).toString()

  const targetUrl = `${config.apiBaseInternal}/public/pat/projects/${projectId}/pages/xml/${xmlId}/export${queryString ? `?${queryString}` : ''}`

  return await proxyRequest(event, targetUrl, {
    headers: {
      authorization: getHeader(event, 'authorization') || undefined,
      host: undefined
    }
  })
})
