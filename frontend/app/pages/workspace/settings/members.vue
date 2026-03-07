<script setup lang="ts">
import type { WorkspaceMember } from '~/types/index'
import { globalKey, wsKey } from '@/utils/fetch-keys'
import { LazyWorkspaceSlideoverInviteMember } from '#components'

const workspace = useWorkspaceStore()
const selectedWorkspace = computed(() => workspace.selectedWorkspaceId)
const { capabilities: workspaceCapabilities } = useWorkspaceCapabilities(selectedWorkspace)
const { allow } = useActionVisibility()
const { user } = useUserSession()
const currentUserId = computed(() => user.value?.id || '')

const membersKey = computed(() => {
  if (!selectedWorkspace.value) return globalKey('pending', 'members', 'list')
  return wsKey(selectedWorkspace.value, 'members', 'list')
})

const { data: members } = await useFetch<WorkspaceMember[]>(
  `/api/workspaces/${selectedWorkspace.value}/members`,
  {
    key: membersKey,
    watch: [selectedWorkspace],
    default: () => []
  })

const { data: currentWorkspace } = await useFetch<{ isPersonal: boolean }>(
  `/api/workspaces/${selectedWorkspace.value}`,
  {
    key: computed(() => selectedWorkspace.value
      ? wsKey(selectedWorkspace.value, 'details')
      : globalKey('pending', 'workspace', 'details')),
    watch: [selectedWorkspace]
  }
)

const canManageMembers = computed(() => allow(workspaceCapabilities.value.canManageMembers))

const q = ref('')

const filteredMembers = computed(() => {
  if (!q.value) return members.value
  const searchLower = q.value.toLowerCase()
  return members.value.filter((member) => {
    return (member.username?.toLowerCase().includes(searchLower))
      || (member.displayName?.toLowerCase().includes(searchLower))
      || (member.email?.toLowerCase().includes(searchLower))
  })
})

const overlay = useOverlay()
const inviteSlideover = overlay.create(LazyWorkspaceSlideoverInviteMember)

async function openInviteModal() {
  if (!selectedWorkspace.value) return
  const instance = inviteSlideover.open({ workspaceId: selectedWorkspace.value })
  await instance.result
}
</script>

<template>
  <div v-if="currentWorkspace">
    <UPageCard
      data-tour="workspace-members-panel"
      title="Members"
      :description="currentWorkspace.isPersonal ? 'Personal workspace members.' : 'Manage workspace members and their roles.'"
      variant="subtle"
      orientation="horizontal"
      class="mb-4"
    >
      <UButton
        data-tour="workspace-members-invite"
        v-if="canManageMembers && !currentWorkspace.isPersonal"
        label="Invite people"
        color="primary"
        variant="solid"
        icon="i-lucide-user-plus"
        class="w-fit lg:ms-auto"
        @click="openInviteModal"
      />
    </UPageCard>

    <UPageCard
      v-if="currentWorkspace.isPersonal"
      variant="subtle"
      class="text-center py-8 mb-4"
    >
      <UIcon name="i-lucide-user" class="w-12 h-12 mx-auto text-muted mb-4" />
      <p class="text-muted">
        This is your personal workspace.
      </p>
      <p class="text-sm text-dimmed mt-1">
        Personal workspaces don't support multiple members.
      </p>
    </UPageCard>

    <UPageCard
      v-else
      variant="subtle"
      :ui="{ container: 'p-0 sm:p-0 gap-y-0', wrapper: 'items-stretch', header: 'p-4 mb-0 border-b border-default' }"
    >
      <template #header>
        <UInput
          data-tour="workspace-members-search"
          v-model="q"
          icon="i-lucide-search"
          placeholder="Search members..."
          class="w-full"
        />
      </template>

      <SettingsMembersList
        :members="filteredMembers"
        :workspace-id="selectedWorkspace || ''"
        :is-current-user-admin="canManageMembers"
        :current-user-id="currentUserId"
      />
    </UPageCard>
  </div>
</template>
