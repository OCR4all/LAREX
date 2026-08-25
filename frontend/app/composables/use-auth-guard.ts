/**
 * Auth Guard Composable
 *
 * Periodically checks auth status and handles token expiry.
 * Uses the dedicated /api/auth/check endpoint for efficient validation.
 */
import { createScopedLogger } from '@/services/editor/logger-service'

const log = createScopedLogger('AuthGuard')

interface AuthCheckResponse {
  valid: boolean
  expiresIn?: number
  expiresAt?: number
}

let authCheckInterval: ReturnType<typeof setInterval> | null = null
let proactiveRefreshTimeout: ReturnType<typeof setTimeout> | null = null
let visibilityChangeHandler: (() => void) | null = null

export const useAuthGuard = () => {
  const { loggedIn } = useUserSession()
  const { handleAuthError } = useAuthRedirector()

  const isChecking = useState<boolean>('auth.guard.isChecking', () => false)
  const tokenExpiresAt = useState<number | null>('auth.tokenExpiresAt', () => null)

  /**
   * Check auth status using dedicated endpoint
   */
  const checkAuthStatus = async (): Promise<boolean> => {
    if (import.meta.server) return true
    if (!loggedIn.value || isChecking.value) return false

    try {
      isChecking.value = true
      const response = await $fetch<AuthCheckResponse>('/api/auth/check')

      if (!response.valid) {
        console.warn('Token validation failed, logging out user...')
        await handleAuthError()
        return false
      }

      if (response.expiresAt) {
        tokenExpiresAt.value = response.expiresAt
        scheduleProactiveRefresh(response.expiresIn || 0)
      }

      return true
    } catch (error: unknown) {
      const status = (error as { statusCode?: number, status?: number } | null)?.statusCode
        ?? (error as { statusCode?: number, status?: number } | null)?.status

      if (status === 401) {
        console.warn('Auth check returned 401, logging out user...')
        await handleAuthError()
        return false
      }
      console.warn('Auth check failed (network issue?):', error)
      return true
    } finally {
      isChecking.value = false
    }
  }

  /**
   * Schedule a proactive token refresh before expiry
   */
  const scheduleProactiveRefresh = (expiresIn: number) => {
    if (proactiveRefreshTimeout) {
      clearTimeout(proactiveRefreshTimeout)
    }

    const refreshIn = Math.max(0, (expiresIn - 120) * 1000)

    if (refreshIn > 0) {
      proactiveRefreshTimeout = setTimeout(async () => {
        if (loggedIn.value) {
          await checkAuthStatus()
        }
      }, refreshIn)
    } else {
      proactiveRefreshTimeout = null
    }
  }

  /**
   * Start periodic auth checking
   */
  const startAuthGuard = (intervalMs: number = 60000) => {
    if (import.meta.server || authCheckInterval) return

    void checkAuthStatus()

    authCheckInterval = setInterval(async () => {
      if (loggedIn.value) {
        await checkAuthStatus()
      }
    }, intervalMs)

    if (!visibilityChangeHandler) {
      visibilityChangeHandler = () => {
        if (document.visibilityState === 'visible' && loggedIn.value) {
          log.debug('Tab visible, checking auth status')
          void checkAuthStatus()
        }
      }
      document.addEventListener('visibilitychange', visibilityChangeHandler)
    }
  }

  /**
   * Stop auth guard
   */
  const stopAuthGuard = () => {
    if (authCheckInterval) {
      clearInterval(authCheckInterval)
      authCheckInterval = null
    }
    if (proactiveRefreshTimeout) {
      clearTimeout(proactiveRefreshTimeout)
      proactiveRefreshTimeout = null
    }
    if (visibilityChangeHandler) {
      document.removeEventListener('visibilitychange', visibilityChangeHandler)
      visibilityChangeHandler = null
    }
  }

  return {
    checkAuthStatus,
    startAuthGuard,
    stopAuthGuard,
    isChecking: readonly(isChecking),
    tokenExpiresAt: readonly(tokenExpiresAt)
  }
}
