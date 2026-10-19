<script setup lang="ts">
import type { Notification, NotificationGroup, NotificationType, WorkspaceInvitation } from '~/types'
import { ROLE_LABELS } from '~/types'
import { LazyLabelModalTransferConflict, LazyProjectModalTransferConflict } from '#components'
import { extractApiErrorMessage, isLabelSetNameConflictError, isProjectNameConflictError } from '@/utils/api-error'

const { openSettings } = useUserSettings()

function openSettingsSection(section: 'invitations' | 'notifications') {
  isNotificationsSlideoverOpen.value = false
  openSettings(section)
}

const { isNotificationsSlideoverOpen } = useDashboard()
const toast = useToast()
const overlay = useOverlay()
const workspaceStore = useWorkspaceStore()
const {
  refreshWorkspaceList,
  refreshWorkspaceMembership,
  refreshWorkspaceTransfers,
  refreshLabelSets,
  refreshUserInvitations,
  refreshUserTransfers
} = useDataRefresh()

const {
  groupedNotifications,
  invitations,
  incomingTransfers,
  ensureInitialData,
  refresh,
  markAsRead: markNotificationAsRead,
  markAllAsRead,
  archiveAllRead,
  hasReadNotifications,
  toggleGroupExpanded
} = useNotifications()

await ensureInitialData()

type TransferRequest = {
  id: string
  projectId?: string
  projectName?: string
  resourceId?: string
  resourceName?: string
  resourceType?: string
  targetName?: string
  transferType?: 'MOVE' | 'COPY'
  targetWorkspaceName?: string
  sourceWorkspaceId: string
  targetWorkspaceId: string
}

const transferConflictModal = overlay.create(LazyProjectModalTransferConflict)
const labelConflictModal = overlay.create(LazyLabelModalTransferConflict)

const hasUnread = computed(() => groupedNotifications.value.some(g => g.items.some(n => !n.read)))

const iconTones = {
  info: 'bg-info/15 text-info ring-info/20',
  success: 'bg-success/15 text-success ring-success/20',
  warning: 'bg-warning/15 text-toned ring-warning/30',
  error: 'bg-error/15 text-error ring-error/20',
  secondary: 'bg-secondary/15 text-secondary ring-secondary/20'
}

const notificationAppearance = {
  WORKSPACE_INVITATION: { icon: 'i-lucide-user-plus', tone: iconTones.info },
  TASK_ASSIGNED: { icon: 'i-lucide-clipboard-list', tone: iconTones.info },
  TASK_COMPLETED: { icon: 'i-lucide-clipboard-check', tone: iconTones.success },
  TASK_REMINDER: { icon: 'i-lucide-bell-ring', tone: iconTones.warning },
  TASK_MENTIONED: { icon: 'i-lucide-at-sign', tone: iconTones.secondary },
  TASK_UPDATED: { icon: 'i-lucide-clipboard-pen', tone: iconTones.info },
  TASK_DUE_SOON: { icon: 'i-lucide-calendar-clock', tone: iconTones.warning },
  TASK_OVERDUE: { icon: 'i-lucide-alarm-clock', tone: iconTones.error },
  TASK_COMMENT_ADDED: { icon: 'i-lucide-message-square', tone: iconTones.secondary },
  PROJECT_CREATED: { icon: 'i-lucide-folder-plus', tone: iconTones.success },
  PROJECT_DELETED: { icon: 'i-lucide-folder-minus', tone: iconTones.error },
  PAGE_CREATED: { icon: 'i-lucide-file-plus', tone: iconTones.success },
  PAGE_DELETED: { icon: 'i-lucide-file-minus', tone: iconTones.error },
  WORKSPACE_WATCH: { icon: 'i-lucide-panels-top-left', tone: iconTones.info },
  PROJECT_WATCH: { icon: 'i-lucide-folder-search', tone: iconTones.info },
  UPLOAD_COMPLETED: { icon: 'i-lucide-cloud-upload', tone: iconTones.success },
  UPLOAD_FAILED: { icon: 'i-lucide-cloud-alert', tone: iconTones.error },
  IMPORT_COMPLETED: { icon: 'i-lucide-file-input', tone: iconTones.success },
  IMPORT_FAILED: { icon: 'i-lucide-file-warning', tone: iconTones.error },
  COLLAB_TAKEOVER_REQUESTED: { icon: 'i-lucide-key-round', tone: iconTones.warning },
  COLLAB_TAKEOVER_GRANTED: { icon: 'i-lucide-lock-open', tone: iconTones.success },
  COLLAB_TAKEOVER_DECLINED: { icon: 'i-lucide-shield-x', tone: iconTones.error },
  COLLAB_TAKEOVER_FORCED: { icon: 'i-lucide-shield-alert', tone: iconTones.warning },
  COLLAB_LEASE_EXPIRED: { icon: 'i-lucide-hourglass', tone: iconTones.warning }
} satisfies Record<NotificationType, { icon: string, tone: string }>

function getNotificationAppearance(type: NotificationType) {
  return notificationAppearance[type] ?? { icon: 'i-lucide-bell', tone: iconTones.info }
}

function getGroupTitle(group: NotificationGroup): string {
  const count = group.items.length
  const firstItem = group.items[0]
  if (count === 1 && firstItem) return firstItem.title
  const typeLabels: Record<string, string> = {
    PAGE_CREATED: 'pages created',
    PAGE_DELETED: 'pages deleted',
    PROJECT_CREATED: 'projects created',
    PROJECT_DELETED: 'projects deleted',
    TASK_ASSIGNED: 'assignments assigned',
    TASK_COMPLETED: 'assignments completed',
    COLLAB_TAKEOVER_REQUESTED: 'edit requests',
    COLLAB_TAKEOVER_GRANTED: 'edit access grants',
    COLLAB_TAKEOVER_DECLINED: 'edit access declines',
    COLLAB_TAKEOVER_FORCED: 'forced takeovers',
    COLLAB_LEASE_EXPIRED: 'expired edit locks'
  }
  return `${count} ${typeLabels[group.type] || 'notifications'}`
}

function getSingleNotification(group: NotificationGroup): Notification {
  const notification = group.items[0]
  if (!notification) {
    throw new Error(`Expected a single notification for group ${group.id}`)
  }
  return notification
}

async function acceptInvite(invitation: WorkspaceInvitation) {
  try {
    await $fetch(`/api/workspaces/${invitation.workspaceId}/invitations/accept`, { method: 'POST' })
    toast.add({ title: 'Invitation accepted!', description: `You are now a member of ${invitation.workspaceName}`, color: 'success' })
    await refreshWorkspaceList()
    workspaceStore.selectWorkspace(invitation.workspaceId)
    await Promise.all([
      refreshWorkspaceMembership(invitation.workspaceId),
      refreshWorkspaceTransfers(invitation.workspaceId),
      refreshUserInvitations(),
      refreshUserTransfers()
    ])
    await refresh()
  } catch (err: unknown) {
    const message = typeof err === 'object' && err !== null && 'data' in err
      ? (err as { data?: { message?: string } }).data?.message
      : null
    toast.add({ title: 'Failed to accept invitation', description: message || 'Please try again', color: 'error' })
  }
}

async function declineInvite(invitation: WorkspaceInvitation) {
  try {
    await $fetch(`/api/workspaces/${invitation.workspaceId}/invitations/decline`, { method: 'POST' })
    toast.add({ title: 'Invitation declined', color: 'neutral' })
    await refreshUserInvitations()
    await refresh()
  } catch (err: unknown) {
    const message = typeof err === 'object' && err !== null && 'data' in err
      ? (err as { data?: { message?: string } }).data?.message
      : null
    toast.add({ title: 'Failed to decline invitation', description: message || 'Please try again', color: 'error' })
  }
}

async function refreshTransferCaches(transfer: TransferRequest) {
  await Promise.all([
    refreshWorkspaceTransfers(transfer.targetWorkspaceId),
    refreshWorkspaceTransfers(transfer.sourceWorkspaceId),
    refreshUserTransfers(),
    ...(transfer.resourceType === 'LABEL_SET'
      ? [refreshLabelSets(transfer.sourceWorkspaceId, transfer.resourceId), refreshLabelSets(transfer.targetWorkspaceId)]
      : [])
  ])
}

async function approveTransfer(transfer: TransferRequest) {
  const endpoint = transfer.projectId ? `/api/project-transfers/${transfer.id}/approve` : `/api/resource-transfers/${transfer.id}/approve`
  let projectName: string | undefined
  let targetName: string | undefined

  while (true) {
    try {
      await $fetch(endpoint, {
        method: 'POST',
        ...(projectName || targetName ? { body: { ...(projectName ? { projectName } : {}), ...(targetName ? { targetName } : {}) } } : {})
      })
      toast.add({ title: 'Transfer approved', color: 'success' })
      await refreshTransferCaches(transfer)
      await refresh()
      return
    } catch (error: unknown) {
      if (transfer.resourceType === 'LABEL_SET' && isLabelSetNameConflictError(error)) {
        const name = targetName || transfer.targetName || (transfer.transferType === 'COPY'
          ? `${transfer.resourceName || 'Label set'} (Copy)`
          : transfer.resourceName || 'Label set')
        const renamed = await labelConflictModal.open({
          name,
          workspaceName: transfer.targetWorkspaceName || 'the target workspace',
          resourceId: transfer.resourceId!,
          targetWorkspaceId: transfer.targetWorkspaceId
        }).result
        if (!renamed) return
        targetName = renamed
        continue
      }
      if (!transfer.projectId || !isProjectNameConflictError(error)) {
        toast.add({ title: 'Failed to approve', description: extractApiErrorMessage(error, 'Could not approve the transfer.'), color: 'error' })
        return
      }

      const transferType = transfer.transferType || 'MOVE'
      const renamed = await transferConflictModal.open({
        projectId: transfer.projectId,
        projectName: projectName || (transferType === 'COPY' ? `${transfer.projectName || 'Project'} (Copy)` : transfer.projectName || 'Project'),
        targetWorkspaceId: transfer.targetWorkspaceId,
        targetWorkspaceName: transfer.targetWorkspaceName || 'the target workspace',
        transferType
      }).result

      if (!renamed) {
        await rejectTransfer(transfer)
        return
      }
      projectName = renamed
    }
  }
}

async function rejectTransfer(transfer: TransferRequest) {
  const endpoint = transfer.projectId ? `/api/project-transfers/${transfer.id}/reject` : `/api/resource-transfers/${transfer.id}/reject`
  try {
    await $fetch(endpoint, { method: 'POST', body: { rejectionReason: '' } })
    toast.add({ title: 'Transfer rejected', color: 'neutral' })
    await refreshTransferCaches(transfer)
    await refresh()
  } catch {
    toast.add({ title: 'Failed to reject', color: 'error' })
  }
}

async function markAsRead(notification: Notification) {
  if (notification.read) return
  await markNotificationAsRead(notification.id)
}

async function handleNotificationClick(notification: Notification) {
  await markAsRead(notification)
  const link = getNotificationLink(notification)
  if (link) {
    isNotificationsSlideoverOpen.value = false
    navigateTo(link)
  }
}

async function handleArchiveAllRead() {
  await archiveAllRead()
}
</script>

<template>
  <UiResponsiveSlideover
    v-model:open="isNotificationsSlideoverOpen"
    side="right"
    :ui="{ overlay: 'z-[60]', content: 'z-[60]' }"
  >
    <template #header>
      <UiSlideoverHeader title="Notifications" icon="i-lucide-bell" />
    </template>

    <template #body>
      <div class="space-y-4">
        <div v-if="invitations.length > 0">
          <div class="flex items-center justify-between mb-2">
            <h3 class="text-sm font-semibold text-highlighted">
              Pending Invitations
            </h3>
            <button type="button" class="text-xs text-primary hover:underline" @click="openSettingsSection('invitations')">
              View all
            </button>
          </div>
          <div class="space-y-2">
            <div v-for="invitation in invitations.slice(0, 3)" :key="invitation.id" class="p-3 rounded-sm bg-elevated/50 border border-default">
              <div class="flex items-start gap-2.5">
                <div class="size-9 bg-info/15 text-info ring-1 ring-inset ring-info/20 rounded-md flex items-center justify-center shrink-0">
                  <UIcon name="i-lucide-user-plus" class="size-4" />
                </div>
                <div class="flex-1 min-w-0">
                  <p class="text-sm font-medium text-highlighted truncate">
                    {{ invitation.workspaceName }}
                  </p>
                  <p class="text-xs text-muted">
                    Invited as {{ ROLE_LABELS[invitation.role] }}
                  </p>
                </div>
              </div>
              <div class="flex gap-2 mt-3">
                <UButton
                  size="xs"
                  color="neutral"
                  variant="outline"
                  class="flex-1"
                  @click="declineInvite(invitation)"
                >
                  Decline
                </UButton>
                <UButton size="xs" class="flex-1" @click="acceptInvite(invitation)">
                  Accept
                </UButton>
              </div>
            </div>
          </div>
          <div v-if="invitations.length > 3" class="mt-2 text-center">
            <button type="button" class="text-xs text-muted hover:text-primary" @click="openSettingsSection('invitations')">
              +{{ invitations.length - 3 }} more invitation{{ invitations.length - 3 > 1 ? 's' : '' }}
            </button>
          </div>
        </div>

        <div v-if="invitations.length > 0 && (incomingTransfers.length > 0 || groupedNotifications.length > 0)" class="border-t border-default" />

        <div v-if="incomingTransfers.length > 0">
          <div class="flex items-center justify-between mb-2">
            <h3 class="text-sm font-semibold text-highlighted">
              Transfer Requests
            </h3>
            <NuxtLink to="/workspace/settings/requests" class="text-xs text-primary hover:underline" @click="isNotificationsSlideoverOpen = false">View all</NuxtLink>
          </div>
          <div class="space-y-2">
            <div v-for="transfer in incomingTransfers.slice(0, 3)" :key="transfer.id" class="p-3 rounded-sm bg-elevated/50 border border-default">
              <div class="flex items-start gap-2.5">
                <div class="size-9 bg-secondary/15 text-secondary ring-1 ring-inset ring-secondary/20 rounded-md flex items-center justify-center shrink-0">
                  <UIcon :name="transfer.transferType === 'MOVE' ? 'i-lucide-folder-input' : 'i-lucide-copy'" class="size-4" />
                </div>
                <div class="flex-1 min-w-0">
                  <p class="text-sm font-medium text-highlighted truncate">
                    {{ transfer.projectName || transfer.resourceName }}
                  </p>
                  <p class="text-xs text-muted">
                    {{ transfer.transferType }} from {{ transfer.sourceWorkspaceName }}
                  </p>
                </div>
              </div>
              <div class="flex gap-2 mt-3">
                <UButton
                  size="xs"
                  color="neutral"
                  variant="outline"
                  class="flex-1"
                  @click="rejectTransfer(transfer)"
                >
                  Reject
                </UButton>
                <UButton size="xs" class="flex-1" @click="approveTransfer(transfer)">
                  Approve
                </UButton>
              </div>
            </div>
          </div>
          <div v-if="incomingTransfers.length > 3" class="mt-2 text-center">
            <NuxtLink to="/workspace/settings/requests" class="text-xs text-muted hover:text-primary" @click="isNotificationsSlideoverOpen = false">
              +{{ incomingTransfers.length - 3 }} more request{{ incomingTransfers.length - 3 > 1 ? 's' : '' }}
            </NuxtLink>
          </div>
        </div>

        <div v-if="(invitations.length > 0 || incomingTransfers.length > 0) && groupedNotifications.length > 0" class="border-t border-default" />

        <div v-if="groupedNotifications.length > 0">
          <div class="flex items-center justify-between mb-2">
            <h3 v-if="invitations.length > 0" class="text-sm font-semibold text-highlighted">
              Recent Activity
            </h3>
            <div class="flex gap-2">
              <UButton
                v-if="hasReadNotifications"
                size="xs"
                variant="ghost"
                color="neutral"
                @click="handleArchiveAllRead"
              >
                Archive All
              </UButton>
              <UButton
                v-if="hasUnread"
                size="xs"
                variant="ghost"
                color="neutral"
                @click="markAllAsRead"
              >
                Mark all read
              </UButton>
            </div>
          </div>

          <div class="space-y-2">
            <div v-for="group in groupedNotifications" :key="group.id">
              <button
                v-if="group.items.length === 1"
                type="button"
                class="w-full p-3 rounded-sm border flex items-start gap-2.5 text-left cursor-pointer hover:bg-elevated focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                :class="getSingleNotification(group).read ? 'border-default bg-elevated/40' : 'border-accented bg-elevated/80'"
                @click="handleNotificationClick(getSingleNotification(group))"
              >
                <UChip
                  as="span"
                  color="error"
                  :show="!getSingleNotification(group).read"
                  inset
                  class="shrink-0"
                >
                  <span class="size-9 rounded-md ring-1 ring-inset flex items-center justify-center" :class="getNotificationAppearance(group.type).tone">
                    <UIcon :name="getNotificationAppearance(group.type).icon" class="size-4" />
                  </span>
                </UChip>
                <span class="text-sm flex-1 min-w-0 space-y-0.5">
                  <span class="sr-only">{{ getSingleNotification(group).read ? 'Read' : 'Unread' }} notification:</span>
                  <span class="flex flex-wrap items-baseline justify-between gap-x-2 gap-y-1">
                    <span class="text-highlighted font-medium flex-1 min-w-32 line-clamp-2">{{ getSingleNotification(group).title }}</span>
                    <span class="flex items-center gap-1 text-muted shrink-0">
                      <NuxtTime :datetime="getSingleNotification(group).created" relative class="text-xs" />
                      <UIcon v-if="getNotificationLink(getSingleNotification(group))" name="i-lucide-arrow-up-right" class="size-3" />
                    </span>
                  </span>
                  <span class="block text-xs leading-4.5 text-muted line-clamp-2 break-words">
                    {{ getSingleNotification(group).message }}
                  </span>
                </span>
              </button>

              <div v-else class="rounded-sm border overflow-hidden" :class="group.items.every(n => n.read) ? 'border-default bg-elevated/40' : 'border-accented bg-elevated/80'">
                <button
                  type="button"
                  class="w-full p-3 flex items-start gap-2.5 text-left cursor-pointer hover:bg-elevated focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary"
                  :aria-expanded="group.isExpanded"
                  @click="toggleGroupExpanded(group.id)"
                >
                  <UChip
                    as="span"
                    color="error"
                    :show="group.items.some(n => !n.read)"
                    inset
                    class="shrink-0"
                  >
                    <span class="size-9 rounded-md ring-1 ring-inset flex items-center justify-center" :class="getNotificationAppearance(group.type).tone">
                      <UIcon :name="getNotificationAppearance(group.type).icon" class="size-4" />
                    </span>
                  </UChip>
                  <span class="text-sm flex-1 min-w-0 space-y-0.5">
                    <span class="sr-only">{{ group.items.every(n => n.read) ? 'Read' : 'Unread' }} notifications:</span>
                    <span class="flex flex-wrap items-baseline justify-between gap-x-2 gap-y-1">
                      <span class="text-highlighted font-medium flex-1 min-w-32 line-clamp-2">{{ getGroupTitle(group) }}</span>
                      <NuxtTime :datetime="group.latestCreated" relative class="text-muted text-xs shrink-0" />
                    </span>
                    <span class="text-muted text-xs flex items-center gap-1">
                      <UIcon :name="group.isExpanded ? 'i-lucide-chevron-up' : 'i-lucide-chevron-down'" class="size-3.5" />
                      {{ group.isExpanded ? 'Hide details' : 'Show details' }}
                    </span>
                  </span>
                </button>

                <div v-if="group.isExpanded" class="mx-3 mb-3 border-t border-default pt-2 space-y-2">
                  <button
                    v-for="notification in group.items"
                    :key="notification.id"
                    type="button"
                    class="w-full p-2 flex items-start gap-2 rounded-sm bg-default/70 border border-default text-left cursor-pointer hover:bg-elevated focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                    @click="handleNotificationClick(notification)"
                  >
                    <span class="mt-1.5 size-1.5 shrink-0 rounded-full" :class="notification.read ? 'bg-transparent' : 'bg-error'" />
                    <span class="min-w-0 space-y-0.5">
                      <span class="sr-only">{{ notification.read ? 'Read' : 'Unread' }} notification:</span>
                      <span class="block text-sm text-highlighted font-medium break-words">
                        {{ notification.title }}
                      </span>
                      <span class="block text-xs text-muted line-clamp-2 break-words">
                        {{ notification.message }}
                      </span>
                    </span>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="groupedNotifications.length === 0 && invitations.length === 0 && incomingTransfers.length === 0" class="text-center py-8">
          <UIcon name="i-lucide-inbox" class="w-12 h-12 mx-auto text-muted mb-4" />
          <p class="text-muted">
            No notifications
          </p>
          <p class="text-sm text-dimmed mt-1">
            You're all caught up!
          </p>
        </div>

        <div class="border-t border-default pt-3 mt-3">
          <button
            type="button"
            class="flex items-center gap-2 text-sm text-muted hover:text-highlighted transition-colors"
            @click="openSettingsSection('notifications')"
          >
            <UIcon name="i-lucide-settings" class="w-4 h-4" />
            <span>Notification settings</span>
          </button>
        </div>
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
