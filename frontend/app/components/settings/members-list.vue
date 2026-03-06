<script setup lang="ts">
import type { WorkspaceMember } from '~/types'
import { ROLE_LABELS } from '~/types'
import { LazyUiConfirmModal } from '#components'

const props = defineProps<{
  members: WorkspaceMember[]
  workspaceId: string
  isCurrentUserAdmin: boolean
  currentUserId: string
}>()

const toast = useToast()
const overlay = useOverlay()
const { refreshWorkspaceMembership } = useDataRefresh()

const roleOptions = [
  { label: 'Curator', value: 'CURATOR' },
  { label: 'Editor', value: 'EDITOR' }
]

const updatingMemberId = ref<string | null>(null)

async function updateMemberRole(member: WorkspaceMember, newRole: 'CURATOR' | 'EDITOR') {
  if (member.role === newRole) return

  updatingMemberId.value = member.id
  try {
    await $fetch(`/api/workspaces/${props.workspaceId}/members/${member.id}`, {
      method: 'PUT',
      body: { role: newRole }
    })
    await refreshWorkspaceMembership(props.workspaceId)

    toast.add({
      title: 'Role updated',
      description: `${member.displayName || member.username} is now ${ROLE_LABELS[newRole].toLowerCase()}`,
      color: 'success'
    })
  } catch (err: any) {
    toast.add({
      title: 'Failed to update role',
      description: err?.data?.message || 'Please try again',
      color: 'error'
    })
  } finally {
    updatingMemberId.value = null
  }
}

async function confirmRemoveMember(member: WorkspaceMember) {
  const modal = overlay.create(LazyUiConfirmModal, {
    props: {
      title: 'Remove Member',
      description: `Are you sure you want to remove ${member.displayName || member.username} from this workspace? They will lose access to all workspace resources.`,
      confirmLabel: 'Remove',
      confirmColor: 'error'
    }
  })

  const confirmed = await modal.open()
  if (!confirmed) return

  try {
    await $fetch(`/api/workspaces/${props.workspaceId}/members/${member.userId}`, {
      method: 'DELETE'
    })
    await refreshWorkspaceMembership(props.workspaceId)

    toast.add({
      title: 'Member removed',
      description: `${member.displayName || member.username} has been removed from the workspace`,
      color: 'success'
    })
  } catch (err: any) {
    toast.add({
      title: 'Failed to remove member',
      description: err?.data?.message || 'Please try again',
      color: 'error'
    })
  }
}

function getMemberMenuItems(member: WorkspaceMember) {
  const items = []

  if (props.isCurrentUserAdmin && member.userId !== props.currentUserId) {
    items.push({
      label: 'Remove from workspace',
      icon: 'i-lucide-user-x',
      color: 'error' as const,
      onSelect: () => confirmRemoveMember(member)
    })
  }

  return items
}

function getStatusBadge(status: string) {
  switch (status) {
    case 'PENDING':
      return { label: 'Pending', color: 'warning' as const }
    case 'ACCEPTED':
      return { label: 'Active', color: 'success' as const }
    case 'DECLINED':
      return { label: 'Declined', color: 'error' as const }
    default:
      return { label: status, color: 'neutral' as const }
  }
}
</script>

<template>
  <ul role="list" class="divide-y divide-default">
    <li
      v-for="member in members"
      :key="member.id"
      class="flex items-center justify-between gap-3 py-3 px-4 sm:px-6"
    >
      <div class="flex items-center gap-3 min-w-0">
        <UAvatar
          :alt="member.displayName || member.username || 'User'"
          :src="member.avatar"
          size="md"
        />

        <div class="text-sm min-w-0">
          <p class="text-highlighted font-medium truncate">
            {{ member.displayName || member.username || 'Unknown User' }}
            <UBadge
              v-if="member.userId === currentUserId"
              size="xs"
              color="neutral"
              variant="solid"
              class="ml-2"
            >
              You
            </UBadge>
          </p>
          <p class="text-muted truncate">
            {{ member.email || member.username }}
          </p>
        </div>
      </div>

      <div class="flex items-center gap-3">
        <UBadge
          v-if="member.invitationStatus === 'PENDING'"
          :color="getStatusBadge(member.invitationStatus).color"
          variant="solid"
          size="sm"
        >
          {{ getStatusBadge(member.invitationStatus).label }}
        </UBadge>

        <USelect
          v-if="isCurrentUserAdmin && member.invitationStatus === 'ACCEPTED'"
          :model-value="member.role"
          :items="roleOptions"
          value-key="value"
          color="neutral"
          size="sm"
          :disabled="updatingMemberId === member.id || member.userId === currentUserId"
          :loading="updatingMemberId === member.id"
          class="w-32"
          @update:model-value="(val) => updateMemberRole(member, val as 'CURATOR' | 'EDITOR')"
        />

        <UBadge
          v-else-if="member.invitationStatus === 'ACCEPTED'"
          :color="member.role === 'CURATOR' || member.role === 'ADMINISTRATOR' ? 'primary' : 'neutral'"
          variant="solid"
          size="sm"
        >
          {{ ROLE_LABELS[member.role] }}
        </UBadge>

        <UDropdownMenu
          v-if="getMemberMenuItems(member).length > 0"
          :items="getMemberMenuItems(member)"
          :content="{ align: 'end' }"
        >
          <UButton
            icon="i-lucide-ellipsis-vertical"
            color="neutral"
            variant="ghost"
            size="sm"
          />
        </UDropdownMenu>
      </div>
    </li>

    <UEmpty
      v-if="members.length === 0"
      icon="i-lucide-users"
      title="No members found"
      description="It looks like this projects doesn't have any members yet."
    />
  </ul>
</template>
