/**
 * Auth Redirector Composable
 *
 * Handles authentication errors by:
 * - Clearing workspace state
 * - Clearing the local session while preserving Keycloak's remembered login
 * - Returning to the requested page after authentication
 */
export const useAuthRedirector = () => {
  const { loggedIn, clear } = useUserSession()
  const nuxtApp = useNuxtApp()
  const route = useRoute()

  const handleAuthError = async () => {
    if (!loggedIn.value) {
      return
    }

    console.warn('Authentication error detected, renewing the local session...')

    try {
      const workspaceStore = useWorkspaceStore()
      workspaceStore.clearState()
    } catch {
      // The workspace store may not be initialized when the authentication failure occurs.
    }

    const isInitialized = useState<boolean>('app.isInitialized')
    isInitialized.value = false

    // Only explicit logout should revoke the remembered Keycloak session.
    await clear()

    await nuxtApp.runWithContext(() => navigateToAuth({ redirectTo: route.fullPath, replace: true }))
  }

  return {
    handleAuthError
  }
}
