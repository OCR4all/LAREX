export default defineEventHandler(async (event) => {
  const config = useRuntimeConfig()

  const baseUrl = config.oauth.keycloak.serverUrl // e.g., 'http://keycloak.localhost'
  const realm = config.oauth.keycloak.realm // e.g., 'larex-dev'
  const clientId = config.oauth.keycloak.clientId // e.g., 'your-client-id'

  const redirectUri = 'http://larex.localhost/auth/keycloak/password-change'

  const params = new URLSearchParams({
    client_id: clientId,
    redirect_uri: redirectUri,
    response_type: 'code',
    scope: 'openid',
    kc_action: 'UPDATE_PASSWORD'
  })

  return `${baseUrl}/realms/${realm}/protocol/openid-connect/auth?${params.toString()}`
})
