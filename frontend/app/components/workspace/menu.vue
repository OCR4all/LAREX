<script setup lang="ts">
import { LazyWorkspaceSlideoverCreate } from '#components'
import type { DropdownMenuItem } from '@nuxt/ui'
import { globalKey } from '@/utils/fetch-keys'

defineProps<{
  collapsed?: boolean
}>()

const overlay = useOverlay()
const workspaceCreateModal = overlay.create(LazyWorkspaceSlideoverCreate)
const workspaceStore = useWorkspaceStore()

const { data: workspaces } = await useFetch('/api/workspaces', {
  key: globalKey('workspaces', 'list'),
  transform: workspaces => workspaces.map(workspace => ({ ...workspace, label: workspace.name }))
})

const isAdminMode = computed(() => workspaceStore.isAdminMode)
const adminWorkspace = computed(() => workspaceStore.adminWorkspace)

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

  const menuItems: DropdownMenuItem[][] = [workspaceItems, [{
    label: 'Create workspace',
    icon: 'i-bxs-layer-plus',
    onSelect() { workspaceCreateModal.open() }
  }, {
    label: 'Manage workspaces',
    icon: 'i-lucide-cog',
    async onSelect() { await navigateTo('/workspaces') }
  }]]

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
      base: collapsed ? 'pe-2 px-2' : 'justify-end max-w-fit truncate max-w-8/11',
      trailing: collapsed ? 'absolute inset-y-0 end-0 flex items-center pe-0' : '',
      content: 'w-[20rem]'
    }"
  >
    <template #default="{ modelValue }">
      <UTooltip :text="isAdminMode ? `Admin: ${modelValue?.label}` : modelValue?.label">
        <div class="flex items-center gap-2">
          <UIcon v-if="isAdminMode" name="i-lucide-shield-alert" class="shrink-0" />
          <UiLogo v-else-if="collapsed" size="32" />
          <span v-if="!collapsed" class="truncate font-medium">{{ modelValue?.label }}</span>
        </div>
      </UTooltip>
    </template>
    <template #trailing>
      <Icon v-show="!collapsed" name="i-lucide-chevrons-up-down" />
    </template>
  </USelectMenu>
</template>
