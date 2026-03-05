<script setup lang="ts">
import type { WorkspaceInvitation } from '~/types'
import { ROLE_LABELS } from '~/types'
import { formatTimeAgo } from '@vueuse/core'
import { globalKey } from '@/utils/fetch-keys'

const toast = useToast()
const { refreshUserInvitations, refreshWorkspaceList, refreshWorkspaceMembership } = useDataRefresh()

const { data: invitations, pending, error } = await useFetch<WorkspaceInvitation[]>('/api/workspaces/invitations', {
  key: globalKey('user', 'invitations', 'list'),
  default: () => []
})

async function acceptInvite(invitation: WorkspaceInvitation) {
  try {
    await $fetch(`/api/workspaces/${invitation.workspaceId}/invitations/accept`, {
      method: 'POST'
    })

    toast.add({
      title: 'Invitation accepted!',
      description: `You are now a member of ${invitation.workspaceName}`,
      color: 'success'
    })

    await Promise.all([
      refreshWorkspaceList(),
      refreshWorkspaceMembership(invitation.workspaceId),
      refreshUserInvitations()
    ])
  } catch (err: any) {
    toast.add({
      title: 'Failed to accept invitation',
      description: err?.data?.message || 'Please try again',
      color: 'error'
    })
  }
}

async function declineInvite(invitation: WorkspaceInvitation) {
  try {
    await $fetch(`/api/workspaces/${invitation.workspaceId}/invitations/decline`, {
      method: 'POST'
    })

    toast.add({
      title: 'Invitation declined',
      color: 'neutral'
    })

    await refreshUserInvitations()
  } catch (err: any) {
    toast.add({
      title: 'Failed to decline invitation',
      description: err?.data?.message || 'Please try again',
      color: 'error'
    })
  }
}
</script>

<template>
  <div>
    <UPageCard
      data-tour="settings-invitations-panel"
      title="Workspace Invitations"
      description="Manage your pending workspace invitations."
      variant="subtle"
      class="mb-4"
    />

    <div v-if="pending" class="p-8 text-center text-muted">
      <UIcon name="i-lucide-loader-2" class="animate-spin mr-2" />
      Loading invitations...
    </div>

    <div v-else-if="error" class="p-8 text-center text-error">
      Failed to load invitations. Please try again.
    </div>

    <UPageCard v-else-if="invitations.length === 0" variant="subtle" class="text-center py-12">
      <UIcon name="i-lucide-inbox" class="w-12 h-12 mx-auto text-muted mb-4" />
      <p class="text-muted">
        No pending invitations
      </p>
      <p class="text-sm text-dimmed mt-1">
        When someone invites you to a workspace, it will appear here.
      </p>
    </UPageCard>

    <div v-else class="space-y-3" data-tour="settings-invitations-list">
      <UPageCard
        v-for="invitation in invitations"
        :key="invitation.id"
        variant="subtle"
        class="flex items-center justify-between gap-4"
      >
        <div class="flex items-center gap-4 min-w-0">
          <div class="w-10 h-10 bg-primary/10 rounded-sm flex items-center justify-center">
            <UIcon name="i-lucide-users" class="w-5 h-5 text-primary" />
          </div>

          <div class="min-w-0">
            <p class="font-medium text-highlighted truncate">
              {{ invitation.workspaceName }}
            </p>
            <div class="flex items-center gap-2 text-sm text-muted">
              <span>Invited as {{ ROLE_LABELS[invitation.role] }}</span>
              <span>•</span>
              <time v-text="formatTimeAgo(new Date(invitation.invitedAt))" />
            </div>
          </div>
        </div>

        <div class="flex items-center gap-2 shrink-0">
          <UButton
            color="neutral"
            variant="outline"
            size="sm"
            @click="declineInvite(invitation)"
          >
            Decline
          </UButton>
          <UButton
            size="sm"
            @click="acceptInvite(invitation)"
          >
            Accept
          </UButton>
        </div>
      </UPageCard>
    </div>
  </div>
</template>
