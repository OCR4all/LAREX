import { buildPublicReleaseProxyRequest } from '#server/utils/public-release-download'

export default defineEventHandler(async (event) => {
  const method = (event.node.req.method || 'GET').toUpperCase()
  if (method !== 'GET' && method !== 'HEAD') {
    throw createError({ statusCode: 405, statusMessage: 'Method Not Allowed' })
  }

  const sharePublicId = getRouterParam(event, 'sharePublicId')
  if (!sharePublicId) {
    throw createError({ statusCode: 400, statusMessage: 'Missing sharePublicId' })
  }

  const config = useRuntimeConfig(event)
  const authorizationHeader = getHeader(event, 'authorization')
  const request = buildPublicReleaseProxyRequest(
    config.apiBaseInternal,
    'dataset-releases',
    sharePublicId,
    authorizationHeader,
    method as 'GET' | 'HEAD'
  )

  const response = await fetch(request.url, request.init)
  if (!response.ok) {
    throw createError({
      statusCode: response.status,
      statusMessage: response.statusText || 'Download failed'
    })
  }

  const contentType = response.headers.get('content-type') || 'application/octet-stream'
  const cacheControl = response.headers.get('cache-control') || 'private, no-store, max-age=0'
  const contentLength = response.headers.get('content-length')
  const contentDisposition = response.headers.get('content-disposition')
  const checksum = response.headers.get('x-checksum-sha256')

  setHeader(event, 'Content-Type', contentType)
  setHeader(event, 'Cache-Control', cacheControl)
  if (contentLength) {
    const parsedLength = Number.parseInt(contentLength, 10)
    if (!Number.isNaN(parsedLength)) {
      setHeader(event, 'Content-Length', parsedLength)
    }
  }
  if (contentDisposition) {
    setHeader(event, 'Content-Disposition', contentDisposition)
  }
  if (checksum) {
    setHeader(event, 'X-Checksum-Sha256', checksum)
  }

  if (method === 'HEAD') {
    return null
  }

  await sendStream(event, response.body!)
})
