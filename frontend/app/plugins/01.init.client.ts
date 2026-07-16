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
import { extractApiErrorMessage } from '@/utils/api-error'

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
  const isInitializing = useState<boolean>('app.isInitializing', () => false)
  const { reportIssue, resolveIssue } = useStatusIssues()
  const issueId = 'startup-incomplete'

  if (isInitialized.value || isInitializing.value) {
    return
  }

  const { loggedIn } = useUserSession()

  if (!loggedIn.value) {
    resolveIssue(issueId)
    return
  }

  isInitializing.value = true
  let failedStep = 'finish startup'
  try {
    failedStep = 'refresh your session'
    await $fetch('/api/auth/refresh-profile', {
      method: 'POST'
    })

    const { checkHealth, startMonitoring } = useBackendHealth()

    let attempts = 0
    const maxAttempts = 5
    const baseDelay = 1000

    while (attempts < maxAttempts) {
      failedStep = 'reach the backend'
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

    failedStep = 'load your workspace selection'
    const workspaceStore = useWorkspaceStore()
    await workspaceStore.validateAndSelectWorkspace()

    startMonitoring(15000)

    failedStep = 'load notifications'
    const { initialize: initNotifications } = useNotifications()
    await initNotifications()
    useActionRunsStore().initializeRealtime()

    isInitialized.value = true
    resolveIssue(issueId)
  } catch (error) {
    isInitialized.value = false
    reportIssue({
      id: issueId,
      source: 'startup',
      severity: 'error',
      title: 'App startup incomplete',
      message: extractApiErrorMessage(
        error,
        `Could not ${failedStep}. Some data may be stale or unavailable until this is retried.`
      ),
      retryLabel: 'Retry startup',
      retry: async () => {
        await initializeApp()
      }
    })
  } finally {
    isInitializing.value = false
  }
}
