<script setup lang="ts">
import { LazyWorkspaceSlideoverCreate } from '#components'
import type { DropdownMenuItem } from '@nuxt/ui'
import { globalKey } from '@/utils/fetch-keys'

defineProps<{
  collapsed?: boolean
}>()

interface WorkspaceOption {
  id: string
  name: string
  label: string
}

const overlay = useOverlay()
const workspaceCreateModal = overlay.create(LazyWorkspaceSlideoverCreate)
const workspaceStore = useWorkspaceStore()
const { user } = useUserSession()

const { data: workspaces } = await useFetch<WorkspaceOption[]>('/api/workspaces', {
  key: globalKey('workspaces', 'list'),
  transform: (entries: WorkspaceOption[]) => entries.map(workspace => ({ ...workspace, label: workspace.name }))
})

const isAdminMode = computed(() => workspaceStore.isAdminMode)
const adminWorkspace = computed(() => workspaceStore.adminWorkspace)
const canCreateTeamWorkspace = computed(() => {
  const roles = user.value?.roles || []
  return roles.includes('GLOBAL_ADMIN') || roles.includes('GLOBAL_CURATOR')
})

const selectedWorkspace = computed(() => {
  if (isAdminMode.value && adminWorkspace.value) {
    return { ...adminWorkspace.value, label: adminWorkspace.value.name }
  }
  const selected = workspaces.value?.find(workspace => workspace.id === workspaceStore.selectedWorkspaceId)
  return selected || workspaces.value?.[0]
})

const items = computed<DropdownMenuItem[][]>(() => {
  if (!workspaces.value) return []

  const workspaceItems = workspaces.value.map(workspace => ({
    ...workspace,
    async onSelect() {
      workspaceStore.selectWorkspace(workspace.id)
      await navigateTo('/')
    }
  }))

  const secondaryItems: DropdownMenuItem[] = []
  if (canCreateTeamWorkspace.value) {
    secondaryItems.push({
      label: 'Create workspace',
      icon: 'i-bxs-layer-plus',
      onSelect() { workspaceCreateModal.open() }
    })
  }
  secondaryItems.push({
    label: 'Manage workspaces',
    icon: 'i-lucide-cog',
    async onSelect() { await navigateTo('/workspaces') }
  })

  const menuItems: DropdownMenuItem[][] = [workspaceItems, secondaryItems]

  if (isAdminMode.value) {
    menuItems.push([{
      label: 'Exit Admin Mode',
      icon: 'i-lucide-log-out',
      color: 'warning',
      onSelect() { workspaceStore.exitAdminMode() }
    }])
  }

  return menuItems
})
</script>

<template>
  <USelectMenu
    v-model="selectedWorkspace"
    :items="items"
    variant="ghost"
    :class="isAdminMode ? 'bg-warning/20' : ''"
    :ui="{
      base: collapsed ? 'pe-2 px-2' : 'w-full max-w-full min-w-0 justify-between',
      trailing: collapsed ? 'absolute inset-y-0 end-0 flex items-center pe-0' : '',
      content: 'w-[20rem]'
    }"
  >
    <template #default="{ modelValue }">
      <UTooltip :text="isAdminMode ? `Admin: ${modelValue?.label}` : modelValue?.label">
        <div class="flex min-w-0 items-center gap-2 text-left">
          <UIcon v-if="isAdminMode" name="i-lucide-shield-alert" class="shrink-0" />
          <UiLogo v-else-if="collapsed" size="32" />
          <span v-if="!collapsed" class="truncate font-medium text-left">{{ modelValue?.label }}</span>
        </div>
      </UTooltip>
    </template>
    <template #trailing>
      <Icon v-show="!collapsed" name="i-lucide-chevrons-up-down" />
    </template>
  </USelectMenu>
</template>
