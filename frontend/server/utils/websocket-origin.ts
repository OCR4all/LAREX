/**
 * Rejects cross-site WebSocket handshakes. Browsers always send `Origin` and cannot forge it or
 * `Host`, so a page on another site cannot open `/_ws` with the user's session cookie. Clients
 * without an `Origin` header are not browsers and still need a valid session cookie.
 */
export function isSameOriginWebSocket(headers: Headers | undefined): boolean {
  const origin = headers?.get('origin')
  if (!origin) return true

  let originHost: string
  try {
    originHost = new URL(origin).host.toLowerCase()
  } catch {
    return false
  }

  const requestHosts = [headers?.get('x-forwarded-host'), headers?.get('host')]
    .flatMap(value => value?.split(',') ?? [])
    .map(value => value.trim().toLowerCase())
    .filter(Boolean)
  return requestHosts.includes(originHost)
}
