/**
 * Builds the Keycloak auth URL with the current color mode preference.
 * Falls back to system preference when color mode is not explicitly set.
 */
export function buildAuthUrl(redirectTo?: string): string {
  const baseUrl = '/auth/keycloak'

  if (import.meta.server) {
    return redirectTo
      ? `${baseUrl}?redirectTo=${encodeURIComponent(redirectTo)}`
      : baseUrl
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

  const params = new URLSearchParams({ dark: String(isDark) })

  if (redirectTo) {
    params.set('redirectTo', redirectTo)
  }

  return `${baseUrl}?${params.toString()}`
}

/**
 * Navigate to Keycloak auth with the resolved theme preference.
 */
export function navigateToAuth(options?: { redirectTo?: string, replace?: boolean, external?: boolean }) {
  const { redirectTo, ...navigateOptions } = options ?? {}
  const url = buildAuthUrl(redirectTo)
  return navigateTo(url, { external: true, ...navigateOptions })
}
