import {
  assertPublicReleaseAttemptAllowed,
  buildPublicReleaseAttemptKey,
  createPublicReleaseBrowserSession,
  registerPublicReleaseAttemptResult
} from '#server/utils/public-release-browser-session'
import {
  buildPublicReleaseProxyRequest,
  PUBLIC_RELEASE_KINDS,
  type PublicReleaseKind
} from '#server/utils/public-release-download'

const SHARE_PUBLIC_ID_PATTERN = /^[A-Za-z0-9_-]{8,128}$/
const SECRET_MAX_LENGTH = 512

type ReleaseProbeResult = {
  kind: PublicReleaseKind
  response?: Response
  error?: unknown
}

function readSecret(value: unknown): string | null {
  if (typeof value !== 'string') {
    return null
  }
  const trimmed = value.trim()
  if (!trimmed || trimmed.length > SECRET_MAX_LENGTH) {
    return null
  }
  return trimmed
}

function isInvalidShareOrSecretStatus(status: number): boolean {
  return status === 401 || status === 403 || status === 404
}

function isUnexpectedStatusResponse(response: Response | undefined): response is Response {
  return response !== undefined && !isInvalidShareOrSecretStatus(response.status)
}

export default defineEventHandler(async (event) => {
  const sharePublicId = getRouterParam(event, 'sharePublicId')
  if (!sharePublicId || !SHARE_PUBLIC_ID_PATTERN.test(sharePublicId)) {
    throw createError({ statusCode: 400, statusMessage: 'Invalid share link.' })
  }

  const body = await readBody<Record<string, unknown> | null>(event).catch(() => null)
  const secret = readSecret(body?.secret)
  if (!secret) {
    throw createError({ statusCode: 400, statusMessage: 'Share secret is required.' })
  }

  const requesterIp = getRequestIP(event, { xForwardedFor: true }) || null
  const requesterUserAgent = getHeader(event, 'user-agent') || null
  const attemptKey = buildPublicReleaseAttemptKey(sharePublicId, requesterIp, requesterUserAgent)
  assertPublicReleaseAttemptAllowed(attemptKey)

  const config = useRuntimeConfig(event)
  const authorizationHeader = `Bearer ${secret}`

  const probeResults = await Promise.all<ReleaseProbeResult>(PUBLIC_RELEASE_KINDS.map(async (kind) => {
    try {
      const request = buildPublicReleaseProxyRequest(
        config.apiBaseInternal,
        kind,
        sharePublicId,
        authorizationHeader,
        'HEAD'
      )
      const response = await fetch(request.url, request.init)
      return { kind, response }
    } catch (error: unknown) {
      return { kind, error }
    }
  }))

  const matchingResult = probeResults.find(result => result.response?.ok)
  if (matchingResult?.response) {
    registerPublicReleaseAttemptResult(attemptKey, true)
    const token = createPublicReleaseBrowserSession(
      matchingResult.kind,
      sharePublicId,
      authorizationHeader,
      requesterIp,
      requesterUserAgent
    )

    setHeader(event, 'Cache-Control', 'private, no-store, max-age=0')
    return { downloadUrl: `/api/public/share/download/${token}` }
  }

  const unexpectedError = probeResults.find(result => result.error)
  if (unexpectedError) {
    throw createError({ statusCode: 502, statusMessage: 'Unable to validate share download.' })
  }

  const unexpectedStatus = probeResults
    .map(result => result.response)
    .find(isUnexpectedStatusResponse)
  if (unexpectedStatus) {
    throw createError({
      statusCode: unexpectedStatus.status,
      statusMessage: unexpectedStatus.statusText || 'Unable to validate share download.'
    })
  }

  registerPublicReleaseAttemptResult(attemptKey, false)
  assertPublicReleaseAttemptAllowed(attemptKey)
  throw createError({ statusCode: 404, statusMessage: 'Share link or secret is invalid.' })
})
