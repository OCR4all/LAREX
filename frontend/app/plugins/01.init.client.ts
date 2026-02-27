/**
 * App Initialization Plugin (Client-only)
 *
 * Handles app initialization on first load:
 * - Checks authentication status
 * - Validates backend health
 * - Initializes workspace selection
 * - Starts background health monitoring
 * - Initializes notifications polling
 */
export default defineNuxtPlugin({
  name: 'app-init',
  enforce: 'post', // Run after other plugins (including Pinia)
  async setup(nuxtApp) {
    if (import.meta.server) return

    nuxtApp.hook('app:mounted', async () => {
      await initializeApp()
    })
  }
})

async function initializeApp() {
  const isInitialized = useState<boolean>('app.isInitialized', () => false)

  if (isInitialized.value) {
    return
  }

  const { loggedIn } = useUserSession()

  if (!loggedIn.value) {
    return
  }

  try {
    const { checkHealth, startMonitoring } = useBackendHealth()

    let attempts = 0
    const maxAttempts = 5
    const baseDelay = 1000

    while (attempts < maxAttempts) {
      const isHealthy = await checkHealth(true)

      if (isHealthy) {
        break
      }

      attempts++
      if (attempts >= maxAttempts) {
        break
      }

      const delay = baseDelay * Math.pow(1.5, attempts - 1)
      await new Promise(resolve => setTimeout(resolve, delay))
    }

    const workspaceStore = useWorkspaceStore()
    await workspaceStore.validateAndSelectWorkspace()

    startMonitoring(15000)

    const { initialize: initNotifications } = useNotifications()
    await initNotifications()

    isInitialized.value = true
  } catch {
    // Initialization errors are handled by individual feature fallbacks.
  }
}
