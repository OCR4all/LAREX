import { describe, expect, it } from 'vitest'
import { findInsecureProductionSecrets } from '../production-secrets'

const STRONG = 'Qm4cQ9v1r8yN3kZpX2sW7tB6uH0jL5eA9fD3gK8mP1'

describe('findInsecureProductionSecrets', () => {
  it('accepts generated secrets', () => {
    expect(findInsecureProductionSecrets({
      sessionPassword: STRONG,
      collaborationSecret: STRONG,
      notificationBridgeSecret: STRONG,
      keycloakClientSecret: STRONG
    })).toEqual([])
  })

  it('reports missing, placeholder and short secrets', () => {
    const problems = findInsecureProductionSecrets({
      sessionPassword: 'replace-with-random-32-plus-character-secret',
      collaborationSecret: 'larex-collaboration-dev-secret',
      notificationBridgeSecret: 'too-short',
      keycloakClientSecret: undefined
    })

    expect(problems).toEqual([
      'NUXT_SESSION_PASSWORD still uses a placeholder or development value',
      'NUXT_COLLABORATION_SECRET still uses a placeholder or development value',
      'NUXT_NOTIFICATION_BRIDGE_SECRET must be at least 32 characters',
      'NUXT_OAUTH_KEYCLOAK_CLIENT_SECRET is not set'
    ])
  })
})
