/**
 * Builds the Keycloak auth URL with the current color mode preference.
 * Falls back to system preference when color mode is not explicitly set.
 */
export function buildAuthUrl(): string {
  const baseUrl = '/auth/keycloak'

  if (import.meta.server) {
    return baseUrl
  }

  let isDark: boolean

  try {
    const colorMode = useColorMode()

    if (colorMode.value === 'dark') {
      isDark = true
    } else if (colorMode.value === 'light') {
      isDark = false
    } else {
      isDark = window.matchMedia('(prefers-color-scheme: dark)').matches
    }
  } catch {
    isDark = window.matchMedia('(prefers-color-scheme: dark)').matches
  }

  return `${baseUrl}?dark=${isDark}`
}

/**
 * Navigate to Keycloak auth with the resolved theme preference.
 */
export function navigateToAuth(options?: { replace?: boolean, external?: boolean }) {
  const url = buildAuthUrl()
  return navigateTo(url, { external: true, ...options })
}
