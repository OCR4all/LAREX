import { findInsecureProductionSecrets } from '#server/utils/production-secrets'

// Refuse to serve a production build with publicly known secrets: anyone could forge
// session cookies, collaboration room tokens or notification bridge broadcasts.
export default defineNitroPlugin(() => {
  if (process.env.NODE_ENV !== 'production') return

  const config = useRuntimeConfig()
  const problems = findInsecureProductionSecrets({
    sessionPassword: config.session?.password,
    collaborationSecret: config.collaborationSecret,
    notificationBridgeSecret: config.notificationBridgeSecret,
    keycloakClientSecret: config.oauth?.keycloak?.clientSecret
  })

  if (problems.length > 0) {
    throw new Error(`Insecure production configuration: ${problems.join('; ')}. Run scripts/bootstrap-env-secrets.sh prod to generate secrets.`)
  }
})
