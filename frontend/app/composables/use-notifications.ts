import type { Notification, NotificationGroup, WorkspaceInvitation, NotificationType } from '~/types'
import type { WorkspaceToolkitResourceType } from '@/types/capabilities'
import { getNotificationLink } from '~/utils/notifications'
import { extractApiErrorMessage } from '@/utils/api-error'

const GROUPING_WINDOW_MS = 2 * 60 * 1000 // 2 minutes
const NOTIFICATION_FALLBACK_POLL_MS = 30 * 1000
const NOTIFICATION_AUDIT_POLL_MS = 5 * 60 * 1000
let notificationRealtimeUnsubscribe: (() => void) | null = null
let notificationConnectionStop: (() => void) | null = null
let notificationPollTimer: ReturnType<typeof setTimeout> | null = null

interface TransferRequest {
  id: string
  projectId?: string
  projectName?: string
  resourceId?: string
  resourceName?: string
  resourceType?: WorkspaceToolkitResourceType
  sourceWorkspaceId: string
  sourceWorkspaceName: string
  targetWorkspaceId: string
  targetWorkspaceName: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'COMPLETED' | 'CANCELLED'
  transferType: 'MOVE' | 'COPY'
  message?: string
  created: string
}

interface NotificationState {
  notifications: Notification[]
  invitations: WorkspaceInvitation[]
  incomingTransfers: TransferRequest[]
  unreadCount: number
  isConnected: boolean
}

/**
 * Notifications Composable
 *
 * Manages notifications and invitations with WebSocket support for real-time updates.
 * Provides a centralized state that can be accessed from anywhere.
 */
export const useNotifications = () => {
  const { reportIssue, resolveIssue } = useStatusIssues()
  const state = useState<NotificationState>('notifications.state', () => ({
    notifications: [],
    invitations: [],
    incomingTransfers: [],
    unreadCount: 0,
    isConnected: false
  }))

  const isLoading = useState<boolean>('notifications.loading', () => false)
  const hasFetchedInitialNotifications = useState<boolean>('notifications.hasFetchedInitialNotifications', () => false)
  const hasFetchedInitialInvitations = useState<boolean>('notifications.hasFetchedInitialInvitations', () => false)
  const hasLoadedInitialData = useState<boolean>('notifications.hasLoadedInitialData', () => false)
  const shownToastKeys = useState<Set<string>>('notifications.shownToastKeys', () => new Set())
  const requestFetch = import.meta.server ? useRequestFetch() : $fetch
  const realtime = useRealtimeSocket()
  const notificationIssueId = 'notifications-sync'

  const reportNotificationIssue = (fallback: string, error: unknown) => {
    reportIssue({
      id: notificationIssueId,
      source: 'notifications',
      severity: 'warning',
      title: 'Notifications may be outdated',
      message: extractApiErrorMessage(error, fallback),
      retryLabel: 'Retry sync',
      retry: async () => {
        await refresh()
      }
    })
  }

  const rememberShownToast = (key: string) => {
    const next = new Set(shownToastKeys.value)
    next.add(key)
    shownToastKeys.value = next
  }

  const maybeShowToast = (
    key: string,
    title: string,
    description: string,
    type: NotificationType,
    onOpen?: () => void
  ) => {
    if (import.meta.server || shownToastKeys.value.has(key)) return

    const { isInAppEnabled } = useNotificationPreferences()
    if (!isInAppEnabled(type)) return

    const toast = useToast()
    toast.add({
      title,
      description,
      color: 'neutral',
      icon: 'i-lucide-bell',
      onClick: onOpen
        ? () => {
            onOpen()
          }
        : undefined,
      actions: onOpen
        ? [
            {
              label: 'Open',
              color: 'primary',
              variant: 'soft',
              onClick: (event?: MouseEvent) => {
                event?.stopPropagation()
                onOpen()
              }
            }
          ]
        : undefined
    })

    rememberShownToast(key)
  }

  const maybeShowNotificationToast = (notification: Notification) => {
    if (notification.read) return
    const link = getNotificationLink(notification)
    maybeShowToast(
      `notification-${notification.id}`,
      notification.title,
      notification.message,
      notification.type as NotificationType,
      link
        ? () => {
            void navigateTo(link)
          }
        : undefined
    )
  }

  const maybeShowInvitationToast = (invitation: WorkspaceInvitation) => {
    maybeShowToast(
      `invitation-${invitation.id}`,
      'New Workspace Invitation',
      `You've been invited to join "${invitation.workspaceName}"`,
      'WORKSPACE_INVITATION'
    )
  }

  /**
   * Fetch notifications from API
   */
  const fetchNotifications = async (): Promise<boolean> => {
    try {
      const existingNotificationIds = new Set(state.value.notifications.map(n => n.id))
      const data = await requestFetch<Notification[]>('/api/notifications')
      state.value.notifications = data || []
      updateUnreadCount()

      if (!hasFetchedInitialNotifications.value) {
        hasFetchedInitialNotifications.value = true
        return true
      }

      for (const notification of state.value.notifications) {
        if (!existingNotificationIds.has(notification.id)) {
          maybeShowNotificationToast(notification)
        }
      }
      return true
    } catch (error) {
      console.error('Failed to fetch notifications:', error)
      reportNotificationIssue('Could not refresh notifications. Existing items are still shown.', error)
      return false
    }
  }

  /**
   * Fetch pending invitations from API
   */
  const fetchInvitations = async (): Promise<boolean> => {
    try {
      const existingInvitationIds = new Set(state.value.invitations.map(inv => inv.id))
      const data = await requestFetch<WorkspaceInvitation[]>('/api/workspaces/invitations')
      state.value.invitations = data || []
      updateUnreadCount()

      if (!hasFetchedInitialInvitations.value) {
        hasFetchedInitialInvitations.value = true
        return true
      }

      for (const invitation of state.value.invitations) {
        if (!existingInvitationIds.has(invitation.id)) {
          maybeShowInvitationToast(invitation)
        }
      }
      return true
    } catch (error) {
      console.error('Failed to fetch invitations:', error)
      reportNotificationIssue('Could not refresh workspace invitations. Existing items are still shown.', error)
      return false
    }
  }

  /**
   * Fetch incoming transfer requests for workspaces where user is admin
   */
  const fetchIncomingTransfers = async (): Promise<boolean> => {
    try {
      const workspaceStore = useWorkspaceStore()
      const workspaceId = workspaceStore.selectedWorkspaceId
      if (!workspaceId) {
        state.value.incomingTransfers = []
        updateUnreadCount()
        resolveIssue(notificationIssueId)
        return true
      }

      const [projectTransfers, resourceTransfers] = await Promise.all([
        requestFetch<TransferRequest[]>(`/api/project-transfers/workspace/${workspaceId}/incoming`),
        requestFetch<TransferRequest[]>(`/api/resource-transfers/workspace/${workspaceId}/incoming`)
      ])
      state.value.incomingTransfers = [...(projectTransfers || []), ...(resourceTransfers || [])]
      updateUnreadCount()
      return true
    } catch (error) {
      console.error('Failed to fetch incoming transfers:', error)
      reportNotificationIssue('Could not refresh transfer requests. Existing items are still shown.', error)
      return false
    }
  }

  /**
   * Refresh all notification data
   */
  const refresh = async (): Promise<boolean> => {
    isLoading.value = true
    try {
      const results = await Promise.all([fetchNotifications(), fetchInvitations(), fetchIncomingTransfers()])
      const allSucceeded = results.every(Boolean)
      if (allSucceeded) {
        resolveIssue(notificationIssueId)
      }
      return allSucceeded
    } finally {
      isLoading.value = false
    }
  }

  const ensureInitialData = async (): Promise<boolean> => {
    if (hasLoadedInitialData.value) {
      return true
    }

    const loaded = await refresh()
    hasLoadedInitialData.value = loaded
    return loaded
  }

  /**
   * Update unread count based on current state
   */
  const updateUnreadCount = () => {
    const unreadNotifications = state.value.notifications.filter(n => !n.read).length
    const pendingInvitations = state.value.invitations.length
    const pendingTransfers = state.value.incomingTransfers.length
    state.value.unreadCount = unreadNotifications + pendingInvitations + pendingTransfers
  }

  /**
   * Connect to WebSocket for real-time updates
   */
  const connectWebSocket = () => {
    if (import.meta.server) return
    realtime.connect()
  }

  /**
   * Legacy no-op for API compatibility. The shared realtime socket is app-scoped.
   */
  const disconnectWebSocket = () => {
    return
  }

  /**
   * Handle incoming WebSocket messages
   */
  const handleWebSocketMessage = (message: { type: string, payload: unknown }) => {
    const { isDesktopEnabled, showDesktopNotification } = useNotificationPreferences()

    switch (message.type) {
      case 'NOTIFICATION': {
        const notification = message.payload as Notification
        state.value.notifications.unshift(notification)
        updateUnreadCount()
        maybeShowNotificationToast(notification)

        if (isDesktopEnabled(notification.type as NotificationType)) {
          showDesktopNotification(notification.title, {
            body: notification.message,
            tag: notification.id // Prevent duplicate notifications
          })
        }
        break
      }

      case 'INVITATION': {
        const invitation = message.payload as WorkspaceInvitation
        state.value.invitations.push(invitation)
        updateUnreadCount()
        maybeShowInvitationToast(invitation)

        if (isDesktopEnabled('WORKSPACE_INVITATION')) {
          showDesktopNotification('New Workspace Invitation', {
            body: `You've been invited to join "${invitation.workspaceName}"`,
            tag: `invitation-${invitation.id}`
          })
        }
        break
      }

      case 'INVITATION_UPDATE':
        fetchInvitations()
        break

      case 'NOTIFICATION_READ': {
        const payload = message.payload as { id: string }
        const readNotification = state.value.notifications.find(n => n.id === payload.id)
        if (readNotification) {
          readNotification.read = true
          updateUnreadCount()
        }
        break
      }

      case 'REFRESH_NOTIFICATIONS':
        refresh()
        break
    }
  }

  /**
   * Mark a notification as read
   */
  const markAsRead = async (notificationId: string) => {
    try {
      await $fetch(`/api/notifications/${notificationId}/read`, {
        method: 'PUT'
      })

      const notification = state.value.notifications.find(n => n.id === notificationId)
      if (notification) {
        notification.read = true
        updateUnreadCount()
      }
    } catch (error) {
      console.error('Failed to mark notification as read:', error)
    }
  }

  /**
   * Mark all notifications as read
   */
  const markAllAsRead = async () => {
    try {
      await $fetch('/api/notifications/read-all', {
        method: 'PUT'
      })

      state.value.notifications.forEach((n) => {
        n.read = true
      })
      updateUnreadCount()
    } catch (error) {
      console.error('Failed to mark all as read:', error)
    }
  }

  /**
   * Initialize notifications - call this on app startup
   */
  const initialize = async () => {
    const { fetchPreferences } = useNotificationPreferences()
    try {
      await fetchPreferences()
    } catch (error) {
      reportNotificationIssue('Could not load notification preferences.', error)
    }

    const loaded = await ensureInitialData()
    connectWebSocket()

    if (!notificationConnectionStop) {
      notificationConnectionStop = watch([
        () => realtime.connectionStatus.value,
        () => realtime.isPageVisible.value
      ], ([status, pageVisible], previous) => {
        const [previousStatus, previouslyVisible] = previous ?? []
        state.value.isConnected = status === 'connected'
        startPolling()
        if (!pageVisible) return
        if (previouslyVisible === false || (status === 'connected' && (previousStatus === 'disconnected' || previousStatus === 'error'))) {
          void refresh()
        }
      }, { immediate: true })
    }

    startPolling()
    if (loaded) {
      resolveIssue(notificationIssueId)
    }
  }

  if (import.meta.client && !notificationRealtimeUnsubscribe) {
    notificationRealtimeUnsubscribe = realtime.subscribe((message) => {
      if (!message.type) return
      handleWebSocketMessage(message as { type: string, payload: unknown })
    })
  }

  /**
   * Start polling for notifications (fallback for WebSocket)
   */
  function startPolling() {
    if (import.meta.server) return
    if (notificationPollTimer) {
      clearTimeout(notificationPollTimer)
    }
    notificationPollTimer = null
    if (!realtime.isPageVisible.value) return

    const delay = realtime.connectionStatus.value === 'connected'
      ? NOTIFICATION_AUDIT_POLL_MS
      : NOTIFICATION_FALLBACK_POLL_MS
    notificationPollTimer = setTimeout(async () => {
      notificationPollTimer = null
      const { loggedIn } = useUserSession()
      if (loggedIn.value) {
        await refresh()
      }
      startPolling()
    }, delay)
  }

  /**
   * Stop polling
   */
  const stopPolling = () => {
    if (notificationPollTimer) {
      clearTimeout(notificationPollTimer)
      notificationPollTimer = null
    }
  }

  /**
   * Archive (delete) all read notifications
   */
  const archiveAllRead = async () => {
    try {
      await $fetch('/api/notifications/read', { method: 'DELETE' })
      state.value.notifications = state.value.notifications.filter(n => !n.read)
      updateUnreadCount()
    } catch (error) {
      console.error('Failed to archive read notifications:', error)
    }
  }

  const expandedGroups = useState<Set<string>>('notifications.expandedGroups', () => new Set())

  const toggleGroupExpanded = (groupId: string) => {
    const newSet = new Set(expandedGroups.value)
    if (newSet.has(groupId)) {
      newSet.delete(groupId)
    } else {
      newSet.add(groupId)
    }
    expandedGroups.value = newSet
  }

  /**
   * Group notifications by type and context within 2-minute windows
   */
  const groupedNotifications = computed((): NotificationGroup[] => {
    const notifications = state.value.notifications.filter(n => n.type !== 'WORKSPACE_INVITATION')
    if (notifications.length === 0) return []

    const groups: NotificationGroup[] = []
    const sorted = [...notifications].sort((a, b) => new Date(b.created).getTime() - new Date(a.created).getTime())

    for (const notification of sorted) {
      const notifTime = new Date(notification.created).getTime()
      const groupKey = `${notification.type}-${notification.relatedEntityType || ''}`

      const existingGroup = groups.find((g) => {
        if (g.type !== notification.type || g.relatedEntityType !== notification.relatedEntityType) return false
        const groupTime = new Date(g.latestCreated).getTime()
        return Math.abs(groupTime - notifTime) <= GROUPING_WINDOW_MS
      })

      if (existingGroup) {
        existingGroup.items.push(notification)
        if (notifTime > new Date(existingGroup.latestCreated).getTime()) {
          existingGroup.latestCreated = notification.created
        }
      } else {
        groups.push({
          id: `${groupKey}-${notification.created}`,
          type: notification.type,
          relatedEntityType: notification.relatedEntityType,
          items: [notification],
          isExpanded: expandedGroups.value.has(`${groupKey}-${notification.created}`),
          latestCreated: notification.created
        })
      }
    }

    groups.forEach((g) => {
      g.isExpanded = expandedGroups.value.has(g.id)
    })

    return groups.sort((a, b) => new Date(b.latestCreated).getTime() - new Date(a.latestCreated).getTime())
  })

  const hasReadNotifications = computed(() => state.value.notifications.some(n => n.read))

  return {
    notifications: computed(() => state.value.notifications),
    groupedNotifications,
    invitations: computed(() => state.value.invitations),
    incomingTransfers: computed(() => state.value.incomingTransfers),
    unreadCount: computed(() => state.value.unreadCount),
    isConnected: computed(() => state.value.isConnected),
    isLoading: readonly(isLoading),
    hasReadNotifications,

    fetchNotifications,
    fetchInvitations,
    fetchIncomingTransfers,
    refresh,
    ensureInitialData,
    markAsRead,
    markAllAsRead,
    archiveAllRead,
    toggleGroupExpanded,
    connectWebSocket,
    disconnectWebSocket,
    startPolling,
    stopPolling,
    initialize
  }
}
