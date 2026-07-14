import { useWebNotification } from '@vueuse/core'
import type { NotificationPreference, NotificationTypeInfo, NotificationType, UpdatePreferenceRequest } from '~/types'

interface NotificationPreferencesState {
  preferences: NotificationPreference[]
  types: NotificationTypeInfo[]
  isLoading: boolean
  isSaving: boolean
  hasLoaded: boolean
  desktopPermission: NotificationPermission | 'unsupported'
}

/**
 * Notification Preferences Composable
 *
 * Manages notification preferences for email, desktop, and in-app notifications.
 * Also handles desktop notification permissions and showing desktop notifications.
 */
export const useNotificationPreferences = () => {
  const state = useState<NotificationPreferencesState>('notification-preferences.state', () => ({
    preferences: [],
    types: [],
    isLoading: false,
    isSaving: false,
    hasLoaded: false,
    desktopPermission: 'default'
  }))

  const { isSupported, permissionGranted } = useWebNotification({
    title: '',
    body: '',
    icon: '/favicon.ico'
  })

  /**
   * Initialize desktop permission state
   */
  const initDesktopPermission = () => {
    if (import.meta.server) return

    if (!isSupported.value) {
      state.value.desktopPermission = 'unsupported'
      return
    }

    state.value.desktopPermission = Notification.permission
  }

  /**
   * Request desktop notification permission
   */
  const requestDesktopPermission = async (): Promise<NotificationPermission> => {
    if (import.meta.server) return 'default'

    if (!isSupported.value) {
      return 'denied'
    }

    try {
      const permission = await Notification.requestPermission()
      state.value.desktopPermission = permission
      return permission
    } catch {
      console.error('Failed to request notification permission')
      return 'denied'
    }
  }

  /**
   * Show a desktop notification
   */
  const showDesktopNotification = async (title: string, options?: NotificationOptions) => {
    if (import.meta.server) return

    if (!isSupported.value || !permissionGranted.value) {
      return
    }

    try {
      const notification = new Notification(title, {
        icon: '/favicon.ico',
        badge: '/favicon.ico',
        ...options
      })

      setTimeout(() => notification.close(), 5000)

      notification.onclick = () => {
        window.focus()
        notification.close()
      }

      return notification
    } catch (error) {
      console.error('Failed to show desktop notification:', error)
    }
  }

  /**
   * Fetch all notification preferences
   */
  const fetchPreferences = async (options?: { force?: boolean }) => {
    if (state.value.hasLoaded && !options?.force) {
      return {
        preferences: state.value.preferences,
        types: state.value.types
      }
    }

    const requestFetch = import.meta.server ? useRequestFetch() : $fetch

    state.value.isLoading = true
    try {
      const [preferences, types] = await Promise.all([
        requestFetch<NotificationPreference[]>('/api/notifications/preferences'),
        requestFetch<NotificationTypeInfo[]>('/api/notifications/preferences/types')
      ])

      state.value.preferences = preferences || []
      state.value.types = types || []
      state.value.hasLoaded = true
      return {
        preferences: state.value.preferences,
        types: state.value.types
      }
    } catch (error) {
      console.error('Failed to fetch notification preferences:', error)
      return {
        preferences: state.value.preferences,
        types: state.value.types
      }
    } finally {
      state.value.isLoading = false
    }
  }

  /**
   * Get preference for a specific notification type
   */
  const getPreference = (type: NotificationType): NotificationPreference | undefined => {
    return state.value.preferences.find(p => p.notificationType === type)
  }

  /**
   * Get type info for a specific notification type
   */
  const getTypeInfo = (type: NotificationType): NotificationTypeInfo | undefined => {
    return state.value.types.find(t => t.type === type)
  }

  /**
   * Update a single preference
   */
  const updatePreference = async (request: UpdatePreferenceRequest): Promise<boolean> => {
    state.value.isSaving = true
    try {
      const updated = await $fetch<NotificationPreference>(
        `/api/notifications/preferences/${request.notificationType}`,
        {
          method: 'PUT',
          body: request
        }
      )

      const index = state.value.preferences.findIndex(
        p => p.notificationType === request.notificationType
      )
      if (index >= 0) {
        state.value.preferences[index] = updated
      } else {
        state.value.preferences.push(updated)
      }

      return true
    } catch (error) {
      console.error('Failed to update preference:', error)
      return false
    } finally {
      state.value.isSaving = false
    }
  }

  /**
   * Toggle email notifications for a type
   */
  const toggleEmail = async (type: NotificationType, enabled: boolean): Promise<boolean> => {
    return updatePreference({
      notificationType: type,
      emailEnabled: enabled
    })
  }

  /**
   * Toggle desktop notifications for a type
   */
  const toggleDesktop = async (type: NotificationType, enabled: boolean): Promise<boolean> => {
    if (enabled && state.value.desktopPermission !== 'granted') {
      const permission = await requestDesktopPermission()
      if (permission !== 'granted') {
        return false
      }
    }

    return updatePreference({
      notificationType: type,
      desktopEnabled: enabled
    })
  }

  /**
   * Toggle in-app notifications for a type
   */
  const toggleInApp = async (type: NotificationType, enabled: boolean): Promise<boolean> => {
    return updatePreference({
      notificationType: type,
      inAppEnabled: enabled
    })
  }

  /**
   * Check if desktop notifications are enabled for a type
   */
  const isDesktopEnabled = (type: NotificationType): boolean => {
    const pref = getPreference(type)
    return pref?.desktopEnabled ?? false
  }

  /**
   * Check if email notifications are enabled for a type
   */
  const isEmailEnabled = (type: NotificationType): boolean => {
    const pref = getPreference(type)
    return pref?.emailEnabled ?? false
  }

  /**
   * Check if in-app notifications are enabled for a type
   */
  const isInAppEnabled = (type: NotificationType): boolean => {
    const pref = getPreference(type)
    return pref?.inAppEnabled ?? true // Default to true for in-app
  }

  return {
    preferences: computed(() => state.value.preferences),
    types: computed(() => state.value.types),
    isLoading: computed(() => state.value.isLoading),
    isSaving: computed(() => state.value.isSaving),
    desktopPermission: computed(() => state.value.desktopPermission),
    isDesktopSupported: isSupported,
    isDesktopPermissionGranted: permissionGranted,

    fetchPreferences,
    initDesktopPermission,
    getPreference,
    getTypeInfo,
    updatePreference,
    toggleEmail,
    toggleDesktop,
    toggleInApp,
    isDesktopEnabled,
    isEmailEnabled,
    isInAppEnabled,
    requestDesktopPermission,
    showDesktopNotification
  }
}
