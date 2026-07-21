export interface KeycloakActionUrlOptions {
  serverUrl: string
  realm: string
  clientId: string
  redirectUrl: string
  callback: string
  action: string
}

export function buildKeycloakActionUrl(options: KeycloakActionUrlOptions): string {
  const baseUrl = options.serverUrl.replace(/\/+$/, '')
  const authorizationUrl = new URL(
    `${baseUrl}/realms/${encodeURIComponent(options.realm)}/protocol/openid-connect/auth`
  )
  const redirectUrl = new URL(options.redirectUrl)

  redirectUrl.pathname = `${redirectUrl.pathname.replace(/\/+$/, '')}/${options.callback}`
  redirectUrl.search = ''
  redirectUrl.hash = ''

  authorizationUrl.search = new URLSearchParams({
    client_id: options.clientId,
    redirect_uri: redirectUrl.toString(),
    response_type: 'code',
    scope: 'openid',
    kc_action: options.action
  }).toString()

  return authorizationUrl.toString()
}
