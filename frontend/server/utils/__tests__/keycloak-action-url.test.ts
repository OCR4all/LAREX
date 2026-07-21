import { describe, expect, it } from 'vitest'
import { buildKeycloakActionUrl } from '../keycloak-action-url'

describe('Keycloak action URL', () => {
  it('uses the configured production callback instead of a local URL', () => {
    const result = new URL(buildKeycloakActionUrl({
      serverUrl: 'https://auth.example.org/',
      realm: 'larex prod',
      clientId: 'larex-frontend',
      redirectUrl: 'https://app.example.org/auth/keycloak',
      callback: 'delete-account',
      action: 'delete_account'
    }))

    expect(result.origin + result.pathname).toBe(
      'https://auth.example.org/realms/larex%20prod/protocol/openid-connect/auth'
    )
    expect(result.searchParams.get('client_id')).toBe('larex-frontend')
    expect(result.searchParams.get('redirect_uri')).toBe(
      'https://app.example.org/auth/keycloak/delete-account'
    )
    expect(result.searchParams.get('kc_action')).toBe('delete_account')
  })

  it('preserves a Keycloak context path and normalizes trailing slashes', () => {
    const result = new URL(buildKeycloakActionUrl({
      serverUrl: 'https://example.org/keycloak///',
      realm: 'larex',
      clientId: 'frontend',
      redirectUrl: 'https://example.org/larex/auth/keycloak/',
      callback: 'password-change',
      action: 'UPDATE_PASSWORD'
    }))

    expect(result.pathname).toBe('/keycloak/realms/larex/protocol/openid-connect/auth')
    expect(result.searchParams.get('redirect_uri')).toBe(
      'https://example.org/larex/auth/keycloak/password-change'
    )
  })
})
