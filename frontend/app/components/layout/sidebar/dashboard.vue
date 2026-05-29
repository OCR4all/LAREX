<script setup lang="ts">
import type { NavigationMenuItem } from '@nuxt/ui'

interface Props {
  open: boolean
}

interface Emits {
  'update:open': [value: boolean]
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const route = useRoute()

const sidebarOpen = computed({
  get: () => props.open,
  set: value => emit('update:open', value)
})

const isAdmin = computed(() => route.path.startsWith('/admin'))

const isActive = (to: string, exact?: boolean) =>
  exact ? route.path === to : route.path.startsWith(to)

const isTaskRoute = computed(() =>
  route.path === '/tasks' || route.path.startsWith('/tasks/')
)

const isDatasetRoute = computed(() =>
  route.path === '/datasets' || route.path.startsWith('/datasets/')
)

const isProjectsRoute = computed(() =>
  route.path === '/' || route.path.startsWith('/project/')
)

const withActive = <T extends { to?: string, exact?: boolean }>(items: T[]) =>
  items.map(item => ({ ...item, active: item.to ? isActive(item.to, item.exact) : false }))

const defaultNavigation = computed<NavigationMenuItem[]>(() => {
  const utilitiesChildren = withActive([
    { label: 'Labels', icon: 'i-lucide-tags', to: '/labels', onSelect: () => { sidebarOpen.value = false } },
    { label: 'Tags', icon: 'i-lucide-network', to: '/tag-sets', onSelect: () => { sidebarOpen.value = false } },
    { label: 'Virtual Keyboards', icon: 'i-lucide-keyboard', to: '/virtual-keyboard', onSelect: () => { sidebarOpen.value = false } },
    { label: 'Dictionaries', icon: 'i-lucide-book-copy', to: '/dictionaries', onSelect: () => { sidebarOpen.value = false } },
    { label: 'Codecs', icon: 'i-lucide-case-lower', to: '/codecs', onSelect: () => { sidebarOpen.value = false } },
    { label: 'Normalization', icon: 'i-lucide-wand-sparkles', to: '/normalization-profiles', onSelect: () => { sidebarOpen.value = false } },
    { label: 'Validation', icon: 'i-lucide-shield-alert', to: '/validation-rulesets', onSelect: () => { sidebarOpen.value = false } }
  ])
  const workspaceChildren = withActive([
    { label: 'General', to: '/workspace/settings', icon: 'i-lucide-sliders-horizontal', exact: true, onSelect: () => { sidebarOpen.value = false } },
    { label: 'Members', to: '/workspace/settings/members', icon: 'i-lucide-users', onSelect: () => { sidebarOpen.value = false } },
    { label: 'Requests', to: '/workspace/settings/requests', icon: 'i-lucide-git-pull-request', onSelect: () => { sidebarOpen.value = false } }
  ])
  const settingsChildren = withActive([
    { label: 'Profile', to: '/settings', icon: 'i-lucide-user', exact: true, onSelect: () => { sidebarOpen.value = false } },
    { label: 'Invitations', to: '/settings/invitations', icon: 'i-lucide-mailbox', onSelect: () => { sidebarOpen.value = false } },
    { label: 'Notifications', to: '/settings/notifications', icon: 'i-lucide-bell', onSelect: () => { sidebarOpen.value = false } },
    { label: 'Security', to: '/settings/security', icon: 'i-lucide-shield', onSelect: () => { sidebarOpen.value = false } }
  ])

  const hasActive = (items: { active: boolean }[]) => items.some(i => i.active)

  return [
    { label: 'Projects', icon: 'i-lucide-library', to: '/', active: isProjectsRoute.value, onSelect: () => { sidebarOpen.value = false } },
    { label: 'Datasets', icon: 'i-lucide-database', to: '/datasets', active: isDatasetRoute.value, onSelect: () => { sidebarOpen.value = false } },
    { label: 'Tasks', icon: 'i-lucide-clipboard-list', to: '/tasks', active: isTaskRoute.value, onSelect: () => { sidebarOpen.value = false } },
    { label: 'Utilities', icon: 'i-lucide-tool-case', defaultOpen: hasActive(utilitiesChildren), type: 'trigger', children: utilitiesChildren },
    { label: 'Workspace', icon: 'i-lucide-layers', defaultOpen: hasActive(workspaceChildren), type: 'trigger', children: workspaceChildren },
    { label: 'Settings', icon: 'i-lucide-settings', defaultOpen: hasActive(settingsChildren), type: 'trigger', children: settingsChildren }
  ]
})

const adminNavigation = computed<NavigationMenuItem[]>(() => {
  const dataManagementChildren = withActive([
    { label: 'Import', icon: 'i-lucide-folder-input', to: '/admin/import' },
    { label: 'Backup', icon: 'i-lucide-database-backup', to: '/admin/backup' },
    { label: 'Storage', icon: 'i-lucide-trash-2', to: '/admin/storage' }
  ])

  const hasActive = (items: { active: boolean }[]) => items.some(i => i.active)

  return [
    { label: 'Dashboard', icon: 'i-lucide-layout-dashboard', to: '/admin', active: route.path === '/admin' },
    { label: 'Actuator', icon: 'i-lucide-heart-pulse', to: '/admin/actuator', active: isActive('/admin/actuator') },
    { label: 'Errors', icon: 'i-lucide-bug', to: '/admin/errors', active: isActive('/admin/errors') },
    { label: 'Actions', icon: 'i-lucide-circle-play', to: '/admin/actions', active: isActive('/admin/actions') },
    { label: 'Search Index', icon: 'i-lucide-search', to: '/admin/search-index', active: isActive('/admin/search-index') },
    { label: 'Quotas', icon: 'i-lucide-hard-drive', to: '/admin/quotas', active: isActive('/admin/quotas') },
    { label: 'Data Management', icon: 'i-lucide-database', defaultOpen: hasActive(dataManagementChildren), type: 'trigger', children: dataManagementChildren },
    { label: 'Workspaces', icon: 'i-lucide-layers', to: '/admin/workspaces', active: isActive('/admin/workspaces') },
    { label: 'Users', icon: 'i-lucide-users', to: '/admin/users', active: isActive('/admin/users') }
  ]
})

const navigation = computed(() => isAdmin.value ? adminNavigation.value : defaultNavigation.value)
</script>

<template>
  <UDashboardSidebar
    id="dashboard"
    v-model:open="sidebarOpen"
    collapsible
    resizable
    :min-size="17"
    :default-size="20"
    :max-size="25"
    class="bg-elevated/25"
    :ui="{ header: 'px-0 py-2 border-b border-default', footer: 'lg:border-t lg:border-default' }"
  >
    <template #content>
      <div data-slot="header" class="h-(--ui-header-height) shrink-0 flex items-center gap-1.5 px-0 py-2 border-b border-default sm:px-6">
        <UButton
          color="neutral"
          variant="ghost"
          icon="i-lucide-x"
          aria-label="Close sidebar"
          class="shrink-0"
          @click="sidebarOpen = false"
        />

        <div class="flex content-center w-full h-full px-4 justify-between">
          <template v-if="isAdmin">
            <UiLogo
              size="32"
              class="self-center cursor-pointer"
              @click="navigateTo('/')"
            />
            <span class="font-medium inline-block self-center">Administration</span>
          </template>
          <template v-else>
            <UiLogo
              size="32"
              class="self-center shrink-0 cursor-pointer"
              @click="navigateTo('/')"
            />
            <WorkspaceMenu :collapsed="false" class="ml-2 min-w-0 flex-1" />
          </template>
        </div>
      </div>

      <div data-slot="body" class="flex flex-col gap-4 flex-1 overflow-y-auto px-4 py-2 sm:px-6">
        <UDashboardSearchButton
          :collapsed="false"
          class="bg-transparent ring-default"
          :kbds="['meta', 'k']"
          data-tour="search-button"
        />

        <template v-if="isAdmin">
          <UButton
            to="/"
            color="neutral"
            variant="subtle"
            label="Back to Application"
            leading-icon="i-lucide-arrow-left"
            block
            class="justify-start"
          />
        </template>

        <UNavigationMenu
          :collapsed="false"
          :items="navigation"
          orientation="vertical"
          tooltip
          popover
        />

        <LayoutSidebarFavorites v-if="!isAdmin" :collapsed="false" class="mt-4" />
      </div>

      <div data-slot="footer" class="shrink-0 flex items-center gap-1.5 px-4 py-2 sm:px-6">
        <UserMenu :collapsed="false" />
      </div>
    </template>

    <template #header="{ collapsed }">
      <div class="flex content-center w-full h-full" :class="[collapsed ? 'px-2 justify-center' : 'px-4 justify-between']">
        <template v-if="isAdmin">
          <UiLogo
            size="32"
            class="self-center cursor-pointer"
            @click="navigateTo('/')"
          />
          <span v-show="!collapsed" class="font-medium inline-block self-center">Administration</span>
        </template>
        <template v-else>
          <UiLogo
            v-show="!collapsed"
            size="32"
            class="self-center shrink-0 cursor-pointer"
            @click="navigateTo('/')"
          />
          <WorkspaceMenu :collapsed="collapsed" :class="collapsed ? '' : 'ml-2 min-w-0 flex-1'" />
        </template>
      </div>
    </template>

    <template #default="{ collapsed }">
      <UDashboardSearchButton
        :collapsed="collapsed"
        class="bg-transparent ring-default"
        :kbds="['meta', 'k']"
        data-tour="search-button"
      />

      <template v-if="isAdmin">
        <UButton
          to="/"
          color="neutral"
          variant="subtle"
          :icon="collapsed ? 'i-lucide-arrow-left' : undefined"
          :label="collapsed ? undefined : 'Back to Application'"
          :leading-icon="collapsed ? undefined : 'i-lucide-arrow-left'"
          block
          class="justify-start"
        />
      </template>

      <UNavigationMenu
        :collapsed="collapsed"
        :items="navigation"
        orientation="vertical"
        tooltip
        popover
      />

      <LayoutSidebarFavorites v-if="!isAdmin" :collapsed="collapsed" class="mt-4" />
    </template>

    <template #footer="{ collapsed }">
      <UserMenu :collapsed="collapsed" />
    </template>
  </UDashboardSidebar>
</template>
