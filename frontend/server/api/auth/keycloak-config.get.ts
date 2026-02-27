export default defineEventHandler(async (_event) => {
  const config = useRuntimeConfig()

  return {
    serverUrl: config.oauth.keycloak.serverUrl,
    realm: config.oauth.keycloak.realm,
    clientId: config.oauth.keycloak.clientId
  }
})
