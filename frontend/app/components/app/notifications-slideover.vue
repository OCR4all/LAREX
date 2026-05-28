<script setup lang="ts">
import type { Notification, NotificationGroup, WorkspaceInvitation } from '~/types'
import { ROLE_LABELS } from '~/types'

const { isNotificationsSlideoverOpen } = useDashboard()
const toast = useToast()
const workspaceStore = useWorkspaceStore()
const {
  refreshWorkspaceList,
  refreshWorkspaceMembership,
  refreshWorkspaceTransfers,
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
  sourceWorkspaceId: string
  targetWorkspaceId: string
}

const hasUnread = computed(() => groupedNotifications.value.some(g => g.items.some(n => !n.read)))

function getNotificationIcon(type: string) {
  switch (type) {
    case 'WORKSPACE_INVITATION':
      return 'i-lucide-user-plus'
    case 'TASK_ASSIGNED':
      return 'i-lucide-clipboard-list'
    case 'TASK_COMPLETED':
      return 'i-lucide-check-circle'
    case 'PROJECT_CREATED':
    case 'PROJECT_DELETED':
      return 'i-lucide-folder'
    case 'PAGE_CREATED':
    case 'PAGE_DELETED':
      return 'i-lucide-file'
    case 'COLLAB_TAKEOVER_REQUESTED':
    case 'COLLAB_TAKEOVER_GRANTED':
    case 'COLLAB_TAKEOVER_DECLINED':
    case 'COLLAB_TAKEOVER_FORCED':
    case 'COLLAB_LEASE_EXPIRED':
      return 'i-lucide-lock'
    default:
      return 'i-lucide-bell'
  }
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
    TASK_ASSIGNED: 'tasks assigned',
    TASK_COMPLETED: 'tasks completed',
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
    refreshUserTransfers()
  ])
}

async function approveTransfer(transfer: TransferRequest) {
  const endpoint = transfer.projectId ? `/api/project-transfers/${transfer.id}/approve` : `/api/resource-transfers/${transfer.id}/approve`
  try {
    await $fetch(endpoint, { method: 'POST' })
    toast.add({ title: 'Transfer approved', color: 'success' })
    await refreshTransferCaches(transfer)
    await refresh()
  } catch {
    toast.add({ title: 'Failed to approve', color: 'error' })
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
  toast.add({ title: 'Read notifications archived', color: 'neutral' })
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
      <div class="space-y-6">
        <div v-if="invitations.length > 0">
          <div class="flex items-center justify-between mb-3">
            <h3 class="text-sm font-semibold text-highlighted">
              Pending Invitations
            </h3>
            <NuxtLink to="/settings/invitations" class="text-xs text-primary hover:underline" @click="isNotificationsSlideoverOpen = false">View all</NuxtLink>
          </div>
          <div class="space-y-2">
            <div v-for="invitation in invitations.slice(0, 3)" :key="invitation.id" class="p-3 rounded-sm bg-primary/5 border border-primary/20">
              <div class="flex items-start gap-3">
                <div class="w-8 h-8 bg-primary/10 rounded-sm flex items-center justify-center shrink-0">
                  <UIcon name="i-lucide-users" class="w-4 h-4 text-primary" />
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
            <NuxtLink to="/settings/invitations" class="text-xs text-muted hover:text-primary" @click="isNotificationsSlideoverOpen = false">
              +{{ invitations.length - 3 }} more invitation{{ invitations.length - 3 > 1 ? 's' : '' }}
            </NuxtLink>
          </div>
        </div>

        <div v-if="invitations.length > 0 && (incomingTransfers.length > 0 || groupedNotifications.length > 0)" class="border-t border-default" />

        <div v-if="incomingTransfers.length > 0">
          <div class="flex items-center justify-between mb-3">
            <h3 class="text-sm font-semibold text-highlighted">
              Transfer Requests
            </h3>
            <NuxtLink to="/workspace/settings/requests" class="text-xs text-primary hover:underline" @click="isNotificationsSlideoverOpen = false">View all</NuxtLink>
          </div>
          <div class="space-y-2">
            <div v-for="transfer in incomingTransfers.slice(0, 3)" :key="transfer.id" class="p-3 rounded-sm bg-amber-500/5 border border-amber-500/20">
              <div class="flex items-start gap-3">
                <div class="w-8 h-8 bg-amber-500/10 rounded-sm flex items-center justify-center shrink-0">
                  <UIcon :name="transfer.transferType === 'MOVE' ? 'i-lucide-move' : 'i-lucide-copy'" class="w-4 h-4 text-amber-500" />
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
          <div class="flex items-center justify-between mb-3">
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

          <div class="space-y-1 -mx-3">
            <div v-for="group in groupedNotifications" :key="group.id">
              <div
                v-if="group.items.length === 1"
                class="px-3 py-2.5 rounded-sm hover:bg-elevated/50 flex items-start gap-3 cursor-pointer"
                :class="{ 'opacity-60': getSingleNotification(group).read }"
                @click="handleNotificationClick(getSingleNotification(group))"
              >
                <UChip color="error" :show="!getSingleNotification(group).read" inset>
                  <div class="w-8 h-8 bg-muted/10 rounded-sm flex items-center justify-center">
                    <UIcon :name="getNotificationIcon(group.type)" class="w-4 h-4 text-muted" />
                  </div>
                </UChip>
                <div class="text-sm flex-1 min-w-0">
                  <p class="flex items-center justify-between gap-2">
                    <span class="text-highlighted font-medium truncate">{{ getSingleNotification(group).title }}</span>
                    <NuxtTime :datetime="getSingleNotification(group).created" relative class="text-muted text-xs shrink-0" />
                  </p>
                  <p class="text-dimmed truncate">
                    {{ getSingleNotification(group).message }}
                  </p>
                  <p v-if="getNotificationLink(getSingleNotification(group))" class="text-xs text-primary mt-1">
                    Click to view →
                  </p>
                </div>
              </div>

              <div v-else class="px-3 py-2.5 rounded-sm hover:bg-elevated/50" :class="{ 'opacity-60': group.items.every(n => n.read) }">
                <div class="flex items-start gap-3 cursor-pointer" @click="toggleGroupExpanded(group.id)">
                  <UChip color="error" :show="group.items.some(n => !n.read)" inset>
                    <div class="w-8 h-8 bg-muted/10 rounded-sm flex items-center justify-center">
                      <UIcon :name="getNotificationIcon(group.type)" class="w-4 h-4 text-muted" />
                    </div>
                  </UChip>
                  <div class="text-sm flex-1 min-w-0">
                    <p class="flex items-center justify-between gap-2">
                      <span class="text-highlighted font-medium truncate">{{ getGroupTitle(group) }}</span>
                      <NuxtTime :datetime="group.latestCreated" relative class="text-muted text-xs shrink-0" />
                    </p>
                    <p class="text-dimmed text-xs flex items-center gap-1">
                      <UIcon :name="group.isExpanded ? 'i-lucide-chevron-up' : 'i-lucide-chevron-down'" class="w-3 h-3" />
                      {{ group.isExpanded ? 'Hide details' : 'Show details' }}
                    </p>
                  </div>
                </div>

                <div v-if="group.isExpanded" class="mt-2 ml-11 space-y-1 border-l-2 border-muted/20 pl-3">
                  <div
                    v-for="notification in group.items"
                    :key="notification.id"
                    class="py-1.5 cursor-pointer hover:bg-elevated/30 rounded-sm px-2 -ml-2"
                    :class="{ 'opacity-60': notification.read }"
                    @click="handleNotificationClick(notification)"
                  >
                    <p class="text-xs text-highlighted truncate">
                      {{ notification.title }}
                    </p>
                    <p class="text-xs text-dimmed truncate">
                      {{ notification.message }}
                    </p>
                  </div>
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

        <div class="border-t border-default pt-4 mt-4">
          <NuxtLink
            to="/settings/notifications"
            class="flex items-center gap-2 text-sm text-muted hover:text-highlighted transition-colors"
            @click="isNotificationsSlideoverOpen = false"
          >
            <UIcon name="i-lucide-settings" class="w-4 h-4" />
            <span>Notification settings</span>
          </NuxtLink>
        </div>
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
