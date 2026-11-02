const KNOWN_PLACEHOLDERS = new Set([
  'change-me',
  'please_change_in_production',
  'replace-with-random-32-plus-character-secret',
  'larex-collaboration-dev-secret',
  'larex-notification-bridge-dev-secret'
])

const MIN_SECRET_LENGTH = 32

export interface ProductionSecrets {
  sessionPassword?: string
  collaborationSecret?: string
  notificationBridgeSecret?: string
  keycloakClientSecret?: string
}

function isPlaceholder(value: string): boolean {
  const normalized = value.trim().toLowerCase()
  return KNOWN_PLACEHOLDERS.has(normalized) || normalized.includes('change-me')
}

/** Lists secrets that are missing, placeholders or too short to be safe in production. */
export function findInsecureProductionSecrets(secrets: ProductionSecrets): string[] {
  const checks: Array<[string, string | undefined, number]> = [
    ['NUXT_SESSION_PASSWORD', secrets.sessionPassword, MIN_SECRET_LENGTH],
    ['NUXT_COLLABORATION_SECRET', secrets.collaborationSecret, MIN_SECRET_LENGTH],
    ['NUXT_NOTIFICATION_BRIDGE_SECRET', secrets.notificationBridgeSecret, MIN_SECRET_LENGTH],
    ['NUXT_OAUTH_KEYCLOAK_CLIENT_SECRET', secrets.keycloakClientSecret, 0]
  ]

  const problems: string[] = []
  for (const [name, value, minLength] of checks) {
    if (!value?.trim()) problems.push(`${name} is not set`)
    else if (isPlaceholder(value)) problems.push(`${name} still uses a placeholder or development value`)
    else if (value.length < minLength) problems.push(`${name} must be at least ${minLength} characters`)
  }
  return problems
}
