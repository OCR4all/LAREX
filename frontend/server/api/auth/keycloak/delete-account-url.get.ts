export default defineEventHandler(async (_event) => {
  const config = useRuntimeConfig()

  return buildKeycloakActionUrl({
    serverUrl: config.oauth.keycloak.serverUrl,
    realm: config.oauth.keycloak.realm,
    clientId: config.oauth.keycloak.clientId,
    redirectUrl: config.oauth.keycloak.redirectURL,
    callback: 'delete-account',
    action: 'delete_account'
  })
})
