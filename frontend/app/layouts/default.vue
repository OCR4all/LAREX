<script setup lang="ts">
import { LazyWorkspaceSlideoverCreate, LazyLibrarySlideoverCreate } from '#components'

const workspace = useWorkspaceStore()
const route = useRoute()
const open = ref(false)
const { storageKey } = useInstance()
const dashboardStorageKey = storageKey('dashboard')
const { isNotificationsSlideoverOpen } = useDashboard()
const { startDashboardTour, startCurrentPageTour } = useOnboarding()

const isInitialized = useState<boolean>('app.isInitialized', () => false)
const { loggedIn, user } = useUserSession()
const canCreateTeamWorkspace = computed(() => {
  const roles = user.value?.roles || []
  return roles.includes('GLOBAL_ADMIN') || roles.includes('GLOBAL_CURATOR')
})

watch(loggedIn, async (isLoggedIn, wasLoggedIn) => {
  if (!isLoggedIn && wasLoggedIn) {
    isInitialized.value = false
    workspace.clearState()

    const { stopMonitoring } = useBackendHealth()
    stopMonitoring()
  }
})

const { searchTerm, isSearching, resultGroups } = useGlobalSearch({
  onSelectResult: () => { open.value = false }
})

const overlay = useOverlay()

const createProjectSlideover = overlay.create(LazyLibrarySlideoverCreate)
const createWorkspaceSlideover = overlay.create(LazyWorkspaceSlideoverCreate)
const isEmbeddedToolkitEditor = computed(() => route.query.embedded === 'toolkit-editor')

const handleOpenSidebarForOnboarding = () => {
  open.value = true
}

onMounted(() => {
  window.addEventListener('larex:onboarding:open-dashboard-sidebar', handleOpenSidebarForOnboarding as EventListener)
})

onBeforeUnmount(() => {
  window.removeEventListener('larex:onboarding:open-dashboard-sidebar', handleOpenSidebarForOnboarding as EventListener)
})

const groups = computed(() => {
  const actionItems = []
  if (workspace.canManageProjects) {
    actionItems.push({
      id: 'new-project',
      label: 'New Project',
      icon: 'i-lucide-folder-plus',
      suffix: 'Create a new project in the current workspace',
      onSelect: () => {
        createProjectSlideover.open()
        open.value = false
      }
    })
  }
  if (canCreateTeamWorkspace.value) {
    actionItems.push({
      id: 'new-workspace',
      label: 'New Team Workspace',
      icon: 'i-lucide-users',
      suffix: 'Create a new team workspace',
      onSelect: () => {
        createWorkspaceSlideover.open()
        open.value = false
      }
    })
  }
  if (workspace.canManageToolkit) {
    actionItems.push({
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
    })
  }
  actionItems.push({
    id: 'open-notifications',
    label: 'Open Notifications',
    icon: 'i-lucide-bell',
    suffix: 'View your notifications',
    kbds: ['N'],
    onSelect: () => {
      isNotificationsSlideoverOpen.value = true
      open.value = false
    }
  }, {
    id: 'start-dashboard-tour',
    label: 'Start Dashboard Tour',
    icon: 'i-lucide-route',
    suffix: 'Interactive walkthrough of the dashboard',
    onSelect: () => {
      open.value = false
      nextTick(() => startDashboardTour())
    }
  }, {
    id: 'start-current-page-tour',
    label: 'Start Current Page Tour',
    icon: 'i-lucide-compass',
    suffix: 'Interactive walkthrough for this page',
    onSelect: () => {
      open.value = false
      nextTick(() => {
        void startCurrentPageTour()
      })
    }
  })

  const baseGroups = [{
    id: 'actions',
    label: 'Actions',
    items: actionItems
  }, {
    id: 'navigation',
    label: 'Navigation',
    items: [{
      id: 'go-projects',
      label: 'Go to Projects',
      icon: 'i-lucide-library',
      suffix: 'Browse your projects',
      kbds: ['G', 'H'],
      to: '/',
      onSelect: () => { open.value = false }
    }, {
      id: 'go-datasets',
      label: 'Go to Datasets',
      icon: 'i-lucide-database',
      suffix: 'Manage datasets and releases',
      to: '/datasets',
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
      id: 'go-workspace-queue',
      label: 'Go to Workspace Queue',
      icon: 'i-lucide-list-ordered',
      suffix: 'View workspace Action runs',
      to: '/workspace/queue',
      onSelect: () => { open.value = false }
    }, {
      id: 'go-members',
      label: 'Go to Members',
      icon: 'i-lucide-users',
      suffix: 'Manage workspace members',
      to: '/workspace/settings/members',
      onSelect: () => { open.value = false }
    }]
  }
  ]
  if (searchTerm.value.trim().length >= 2) {
    return [...resultGroups.value, ...baseGroups]
  }
  return baseGroups
})
</script>

<template>
  <div v-if="isEmbeddedToolkitEditor" class="h-screen min-h-0 overflow-hidden">
    <slot />
  </div>
  <UDashboardGroup
    v-else
    storage="cookie"
    :storage-key="dashboardStorageKey"
    unit="rem"
  >
    <LayoutSidebarDashboard v-model:open="open" />

    <UDashboardSearch
      v-model:search-term="searchTerm"
      :groups="groups"
      :loading="isSearching"
    />

    <slot />

    <AppStatusOverlayPanel />
    <AppNotificationsSlideover />
  </UDashboardGroup>
</template>
