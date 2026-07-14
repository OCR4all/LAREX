/**
 * Auth Redirector Composable
 *
 * Handles authentication errors by:
 * - Clearing workspace state
 * - Logging out from Keycloak
 * - Redirecting to login
 */
export const useAuthRedirector = () => {
  const { loggedIn, clear } = useUserSession()
  const nuxtApp = useNuxtApp()

  const handleAuthError = async () => {
    if (!loggedIn.value) {
      return
    }

    console.warn('Authentication error detected, logging out user...')

    try {
      const workspaceStore = useWorkspaceStore()
      workspaceStore.clearState()
    } catch {
      // The workspace store may not be initialized when the authentication failure occurs.
    }

    const isInitialized = useState<boolean>('app.isInitialized')
    isInitialized.value = false

    try {
      await $fetch('/api/auth/logout', { method: 'POST' })
    } catch (logoutError) {
      console.error('Logout error:', logoutError)
      await clear()
    }

    await nuxtApp.runWithContext(() => navigateToAuth({ replace: true }))
  }

  return {
    handleAuthError
  }
}
