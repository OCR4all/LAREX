import { buildPublicActionOutputProxyRequest } from '#server/utils/public-action-output-download'

export default defineEventHandler(async (event) => {
  const method = (event.node.req.method || 'GET').toUpperCase()
  if (method !== 'GET' && method !== 'HEAD') throw createError({ statusCode: 405, statusMessage: 'Method Not Allowed' })
  const sharePublicId = getRouterParam(event, 'sharePublicId')
  if (!sharePublicId) throw createError({ statusCode: 400, statusMessage: 'Missing sharePublicId' })
  const request = buildPublicActionOutputProxyRequest(
    useRuntimeConfig(event).apiBaseInternal,
    sharePublicId,
    getHeader(event, 'authorization'),
    method as 'GET' | 'HEAD'
  )
  const response = await fetch(request.url, request.init)
  if (!response.ok) throw createError({ statusCode: response.status, statusMessage: response.statusText || 'Download failed' })
  for (const header of ['content-type', 'cache-control', 'content-length', 'content-disposition']) {
    const value = response.headers.get(header)
    if (value) setHeader(event, header, value)
  }
  if (method === 'HEAD') return null
  await sendStream(event, response.body!)
})
