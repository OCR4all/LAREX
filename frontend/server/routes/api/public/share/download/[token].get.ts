import { consumePublicReleaseBrowserSession } from '#server/utils/public-release-browser-session'
import { buildPublicReleaseProxyRequest } from '#server/utils/public-release-download'

const TOKEN_PATTERN = /^[A-Za-z0-9_-]{16,128}$/

export default defineEventHandler(async (event) => {
  const token = getRouterParam(event, 'token')
  if (!token || !TOKEN_PATTERN.test(token)) {
    throw createError({ statusCode: 404, statusMessage: 'Not Found' })
  }

  const requesterIp = getRequestIP(event, { xForwardedFor: true }) || null
  const requesterUserAgent = getHeader(event, 'user-agent') || null
  const session = consumePublicReleaseBrowserSession(token, requesterIp, requesterUserAgent)

  if (!session) {
    throw createError({ statusCode: 404, statusMessage: 'Download link is no longer valid.' })
  }

  const config = useRuntimeConfig(event)
  const request = buildPublicReleaseProxyRequest(
    config.apiBaseInternal,
    session.kind,
    session.sharePublicId,
    session.authorizationHeader,
    'GET'
  )
  const response = await fetch(request.url, request.init)

  if (!response.ok) {
    throw createError({ statusCode: 404, statusMessage: 'Share link or secret is invalid.' })
  }

  const contentType = response.headers.get('content-type') || 'application/octet-stream'
  const contentLength = response.headers.get('content-length')
  const contentDisposition = response.headers.get('content-disposition')
  const checksum = response.headers.get('x-checksum-sha256')
  const cacheControl = response.headers.get('cache-control') || 'private, no-store, max-age=0'

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

  if (!response.body) {
    throw createError({ statusCode: 502, statusMessage: 'Download stream unavailable.' })
  }

  await sendStream(event, response.body)
})
