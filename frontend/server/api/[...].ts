export default defineEventHandler(async (event) => {
  const config = useRuntimeConfig(event)

  const path = getRequestURL(event).pathname.replace(/^\/api\/?/, '')
  const method = toHttpMethod(event.node.req.method)

  if (path.startsWith('auth/')) {
    throw createError({
      statusCode: 404,
      statusMessage: 'Not Found'
    })
  }

  const contentType = event.node.req.headers['content-type'] || ''
  if (contentType.includes('multipart/form-data')) {
    throw createError({
      statusCode: 400,
      statusMessage: 'File uploads must use /api/upload-proxy/ endpoint'
    })
  }

  const { user, secure } = await getUserSession(event)

  if (!user || !secure?.accessToken) {
    throw createError({
      statusCode: 401,
      statusMessage: 'Unauthorized'
    })
  }

  try {
    const { refreshTokenIfExpired } = await import('#server/utils/auth')
    await refreshTokenIfExpired(event, { user, secure })
  } catch {
    throw createError({
      statusCode: 401,
      statusMessage: 'Token refresh failed'
    })
  }

  const updatedSession = await getUserSession(event)

  const headers = {
    'Authorization': `Bearer ${updatedSession.secure?.accessToken}`,
    'Content-Type': event.node.req.headers['content-type'] || 'application/json'
  }

  let body: unknown
  if (method !== 'GET' && method !== 'HEAD') {
    try {
      body = await readBody(event)
    } catch {
      // Ignore parsing failures for empty or non-JSON request bodies.
    }
  }

  const backendUrl = `${config.apiBaseInternal}/${path}`

  if (method === 'GET' && /^projects\/[^/]+\/pages(?:\/[^/]+)?$/.test(path)) {
    setHeader(event, 'Cache-Control', 'no-store')
  }

  try {
    const isBlobRequest = path.includes('/blob') || path.includes('/export') || path.includes('/download')
    const acceptHeader = event.node.req.headers.accept || ''
    const isSseRequest = typeof acceptHeader === 'string' && acceptHeader.includes('text/event-stream')

    if (isBlobRequest || isSseRequest) {
      const query = getQuery(event) as Record<string, string | string[] | undefined>
      const queryString = toQueryString(query)
      const fullUrl = `${backendUrl}${queryString}`

      const response = await fetch(fullUrl, {
        method,
        headers,
        body: toRequestBody(body)
      })

      if (!response.ok) {
        throw createError({
          statusCode: response.status,
          statusMessage: response.statusText
        })
      }

      const contentType = response.headers.get('content-type')
        || (path.startsWith('xml/') && path.includes('/blob') ? 'application/xml' : 'application/octet-stream')
      const cacheControl = response.headers.get('cache-control')
      const contentLength = response.headers.get('content-length')
      const contentDisposition = response.headers.get('content-disposition')
      const connection = response.headers.get('connection')

      setHeader(event, 'Content-Type', contentType)
      if (cacheControl) {
        setHeader(event, 'Cache-Control', cacheControl)
      }
      if (isSseRequest) {
        setHeader(event, 'X-Accel-Buffering', 'no')
      }
      if (contentLength) {
        const parsedLength = Number.parseInt(contentLength, 10)
        if (!Number.isNaN(parsedLength)) {
          setHeader(event, 'Content-Length', parsedLength)
        }
      }
      if (connection) {
        setHeader(event, 'Connection', connection)
      }
      if (contentDisposition) {
        setHeader(event, 'Content-Disposition', contentDisposition)
      }

      await sendStream(event, response.body!)
      return
    } else {
      return await $fetch(backendUrl, {
        method,
        headers,
        body: body as Record<string, unknown> | BodyInit | null | undefined,
        query: getQuery(event)
      })
    }
  } catch (error: unknown) {
    if (isExpectedDisconnectError(error)) {
      console.debug(`[api proxy] Client disconnected while streaming /api/${path}`)
      return
    }

    const resolvedError = error as {
      response?: { status?: number, statusText?: string }
      statusCode?: number
      statusMessage?: string
      data?: unknown
      message?: string
    }
    const statusCode = resolvedError.response?.status || resolvedError.statusCode || 500
    const errorData = isErrorResponseData(resolvedError.data) ? resolvedError.data : undefined

    if (statusCode === 401) {
      throw createError({
        statusCode: 401,
        statusMessage: 'Unauthorized',
        data: errorData
      })
    }

    throw createError({
      statusCode,
      statusMessage: errorData?.message
        || resolvedError.statusMessage
        || resolvedError.response?.statusText
        || resolvedError.message
        || 'Internal Server Error',
      data: errorData
    })
  }
})

type ErrorResponseData = {
  status?: number
  error?: string
  message?: string
  path?: string
  details?: string[]
  code?: string
}

function toRequestBody(body: unknown): BodyInit | undefined {
  if (body == null) {
    return undefined
  }
  if (body instanceof ArrayBuffer) {
    return body
  }
  if (ArrayBuffer.isView(body)) {
    return body as unknown as BodyInit
  }
  if (typeof body === 'string') {
    return body
  }
  return JSON.stringify(body)
}

function toQueryString(query: Record<string, string | string[] | undefined>): string {
  const params = new URLSearchParams()
  for (const [key, rawValue] of Object.entries(query)) {
    if (rawValue == null) {
      continue
    }
    if (Array.isArray(rawValue)) {
      for (const value of rawValue) {
        params.append(key, String(value))
      }
      continue
    }
    params.append(key, String(rawValue))
  }
  const serialized = params.toString()
  return serialized ? `?${serialized}` : ''
}

function isErrorResponseData(value: unknown): value is ErrorResponseData {
  if (!value || typeof value !== 'object') {
    return false
  }

  const candidate = value as Record<string, unknown>
  return typeof candidate.message === 'string'
}

type ProxyMethod = 'GET' | 'HEAD' | 'POST' | 'PUT' | 'PATCH' | 'DELETE' | 'CONNECT' | 'OPTIONS' | 'TRACE'

function toHttpMethod(method: string | undefined): ProxyMethod {
  const normalized = method?.toUpperCase()
  switch (normalized) {
    case 'GET':
    case 'HEAD':
    case 'POST':
    case 'PUT':
    case 'PATCH':
    case 'DELETE':
    case 'CONNECT':
    case 'OPTIONS':
    case 'TRACE':
      return normalized
    default:
      return 'GET'
  }
}
