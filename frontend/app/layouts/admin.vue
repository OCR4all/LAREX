<script setup lang="ts">
import { LazyWorkspaceSlideoverCreate, LazyLibrarySlideoverCreate } from '#components'

const workspace = useWorkspaceStore()
const open = ref(false)
const { loggedIn } = useUserSession()
const { isNotificationsSlideoverOpen } = useDashboard()

watch(loggedIn, async (isLoggedIn, wasLoggedIn) => {
  if (!isLoggedIn && wasLoggedIn) {
    workspace.clearState()
    const { stopMonitoring } = useBackendHealth()
    stopMonitoring()
  }
})

const { searchTerm, isSearching, resultGroups } = useGlobalSearch({
  onSelectResult: () => { open.value = false }
})

const overlay = useOverlay()
const createProjectModal = overlay.create(LazyLibrarySlideoverCreate)
const createWorkspaceModal = overlay.create(LazyWorkspaceSlideoverCreate)

const groups = computed(() => {
  const baseGroups = [{
    id: 'actions',
    label: 'Actions',
    items: [{
      id: 'new-project',
      label: 'New Project',
      icon: 'i-lucide-folder-plus',
      suffix: 'Create a new project in the current workspace',
      onSelect: () => {
        createProjectModal.open()
        open.value = false
      }
    }, {
      id: 'new-workspace',
      label: 'New Team Workspace',
      icon: 'i-lucide-users',
      suffix: 'Create a new team workspace',
      onSelect: () => {
        createWorkspaceModal.open()
        open.value = false
      }
    }, {
      id: 'new-label-set',
      label: 'New Label Set',
      icon: 'i-lucide-tags',
      suffix: 'Create a new label set',
      onSelect: () => {
        navigateTo('/labels/new')
        open.value = false
      }
    }, {
      id: 'new-tag-set',
      label: 'New Tag Set',
      icon: 'i-lucide-network',
      suffix: 'Create a new tag set',
      onSelect: () => {
        navigateTo('/tag-sets/new')
        open.value = false
      }
    }, {
      id: 'new-virtual-keyboard',
      label: 'New Virtual Keyboard',
      icon: 'i-lucide-keyboard',
      suffix: 'Create a new virtual keyboard',
      onSelect: () => {
        navigateTo('/virtual-keyboard/new')
        open.value = false
      }
    }, {
      id: 'new-codec',
      label: 'New Codec',
      icon: 'i-lucide-case-lower',
      suffix: 'Create a new codec',
      onSelect: () => {
        navigateTo('/codecs/new')
        open.value = false
      }
    }, {
      id: 'open-notifications',
      label: 'Open Notifications',
      icon: 'i-lucide-bell',
      suffix: 'View your notifications',
      kbds: ['N'],
      onSelect: () => {
        isNotificationsSlideoverOpen.value = true
        open.value = false
      }
    }]
  }, {
    id: 'navigation',
    label: 'Navigation',
    items: [{
      id: 'go-library',
      label: 'Go to Library',
      icon: 'i-lucide-library',
      suffix: 'Browse your projects',
      kbds: ['G', 'H'],
      to: '/',
      onSelect: () => { open.value = false }
    }, {
      id: 'go-tasks',
      label: 'Go to Tasks',
      icon: 'i-lucide-clipboard-list',
      suffix: 'View and manage tasks',
      kbds: ['G', 'T'],
      to: '/tasks',
      onSelect: () => { open.value = false }
    }, {
      id: 'go-labels',
      label: 'Go to Labels',
      icon: 'i-lucide-tags',
      suffix: 'Manage label sets',
      kbds: ['G', 'L'],
      to: '/labels',
      onSelect: () => { open.value = false }
    }, {
      id: 'go-tags',
      label: 'Go to Tags',
      icon: 'i-lucide-network',
      suffix: 'Manage tag sets',
      to: '/tag-sets',
      onSelect: () => { open.value = false }
    }, {
      id: 'go-virtual-keyboards',
      label: 'Go to Virtual Keyboards',
      icon: 'i-lucide-keyboard',
      suffix: 'Manage virtual keyboards',
      to: '/virtual-keyboard',
      onSelect: () => { open.value = false }
    }, {
      id: 'go-codecs',
      label: 'Go to Codecs',
      icon: 'i-lucide-case-lower',
      suffix: 'Manage codecs',
      to: '/codecs',
      onSelect: () => { open.value = false }
    }, {
      id: 'go-settings',
      label: 'Go to Settings',
      icon: 'i-lucide-settings',
      suffix: 'Profile and preferences',
      kbds: ['G', 'S'],
      to: '/settings',
      onSelect: () => { open.value = false }
    }, {
      id: 'go-workspace-settings',
      label: 'Go to Workspace Settings',
      icon: 'i-lucide-layers',
      suffix: 'Workspace configuration',
      to: '/workspace/settings',
      onSelect: () => { open.value = false }
    }, {
      id: 'go-members',
      label: 'Go to Members',
      icon: 'i-lucide-users',
      suffix: 'Manage workspace members',
      to: '/workspace/settings/members',
      onSelect: () => { open.value = false }
    }, {
      id: 'go-admin-dashboard',
      label: 'Go to Admin Dashboard',
      icon: 'i-lucide-layout-dashboard',
      suffix: 'Administration overview',
      to: '/admin',
      onSelect: () => { open.value = false }
    }, {
      id: 'go-admin-search-index',
      label: 'Go to Search Index',
      icon: 'i-lucide-search',
      suffix: 'Rebuild global search index',
      to: '/admin/search-index',
      onSelect: () => { open.value = false }
    }, {
      id: 'go-admin-quotas',
      label: 'Go to Quotas',
      icon: 'i-lucide-hard-drive',
      suffix: 'Manage workspace quotas',
      to: '/admin/quotas',
      onSelect: () => { open.value = false }
    }, {
      id: 'go-admin-backup',
      label: 'Go to Backup',
      icon: 'i-lucide-database-backup',
      suffix: 'Dump and reseed workflows',
      to: '/admin/backup',
      onSelect: () => { open.value = false }
    }, {
      id: 'go-admin-storage',
      label: 'Go to Storage',
      icon: 'i-lucide-trash-2',
      suffix: 'Storage management',
      to: '/admin/storage',
      onSelect: () => { open.value = false }
    }, {
      id: 'go-admin-workspaces',
      label: 'Go to Workspaces',
      icon: 'i-lucide-layers',
      suffix: 'Manage all workspaces',
      to: '/admin/workspaces',
      onSelect: () => { open.value = false }
    }, {
      id: 'go-admin-users',
      label: 'Go to Users',
      icon: 'i-lucide-users',
      suffix: 'Manage all users',
      to: '/admin/users',
      onSelect: () => { open.value = false }
    }]
  }]

  if (searchTerm.value.trim().length >= 2) {
    return [...resultGroups.value, ...baseGroups]
  }

  return baseGroups
})
</script>

<template>
  <UDashboardGroup storage="cookie" storage-key="dashboard" unit="rem">
    <LayoutSidebarDashboard v-model:open="open" />

    <UDashboardSearch
      v-model:search-term="searchTerm"
      :groups="groups"
      :loading="isSearching"
    />

    <slot />

    <AppNotificationsSlideover />
    <WorkspaceSlideoverCreate />
  </UDashboardGroup>
</template>
