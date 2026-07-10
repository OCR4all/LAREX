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
const { isNotificationsSlideoverOpen } = useDashboard()
const { unreadCount, ensureInitialData } = useNotifications()
const isMobileSidebar = useMediaQuery('(max-width: 1023px)')

await ensureInitialData()

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

const isProjectsRoute = computed(() =>
  route.path === '/' || route.path.startsWith('/project/')
)

const withActive = <T extends { to?: string, exact?: boolean }>(items: T[]) =>
  items.map(item => ({ ...item, active: item.to ? isActive(item.to, item.exact) : false }))

interface ActiveNavigationItem {
  active?: boolean
  children?: ActiveNavigationItem[]
}

const hasActive = (items: ActiveNavigationItem[]): boolean =>
  items.some(item => item.active || (item.children ? hasActive(item.children) : false))

const defaultNavigation = computed<NavigationMenuItem[]>(() => {
  const toolkitTextChildren = withActive([
    { label: 'Dictionaries', icon: 'i-lucide-book-copy', to: '/dictionaries', onSelect: () => { sidebarOpen.value = false } },
    { label: 'Codecs', icon: 'i-lucide-case-lower', to: '/codecs', onSelect: () => { sidebarOpen.value = false } },
    { label: 'Normalization', icon: 'i-lucide-wand-sparkles', to: '/normalization-profiles', onSelect: () => { sidebarOpen.value = false } },
    { label: 'Virtual Keyboards', icon: 'i-lucide-keyboard', to: '/virtual-keyboard', onSelect: () => { sidebarOpen.value = false } },
    { label: 'Validation', icon: 'i-lucide-shield-alert', to: '/validation-rulesets', onSelect: () => { sidebarOpen.value = false } }
  ])
  const toolkitChildren = [
    ...withActive([
      { label: 'Datasets', icon: 'i-lucide-database', to: '/datasets', onSelect: () => { sidebarOpen.value = false } },
      { label: 'Tags', icon: 'i-lucide-network', to: '/tag-sets', onSelect: () => { sidebarOpen.value = false } },
      { label: 'Labels', icon: 'i-lucide-tags', to: '/labels', onSelect: () => { sidebarOpen.value = false } }
    ]),
    {
      label: 'Text',
      icon: 'i-lucide-file-text',
      type: 'trigger',
      defaultOpen: hasActive(toolkitTextChildren),
      children: toolkitTextChildren
    }
  ]
  const workspaceChildren = withActive([
    { label: 'General', to: '/workspace/settings', icon: 'i-lucide-sliders-horizontal', exact: true, onSelect: () => { sidebarOpen.value = false } },
    { label: 'Members', to: '/workspace/settings/members', icon: 'i-lucide-users', onSelect: () => { sidebarOpen.value = false } },
    { label: 'Requests', to: '/workspace/settings/requests', icon: 'i-lucide-git-pull-request', onSelect: () => { sidebarOpen.value = false } },
    { label: 'Queue', to: '/workspace/queue', icon: 'i-lucide-list-ordered', onSelect: () => { sidebarOpen.value = false } }
  ])
  const settingsChildren = withActive([
    { label: 'Profile', to: '/settings', icon: 'i-lucide-user', exact: true, onSelect: () => { sidebarOpen.value = false } },
    { label: 'Invitations', to: '/settings/invitations', icon: 'i-lucide-mailbox', onSelect: () => { sidebarOpen.value = false } },
    { label: 'Notifications', to: '/settings/notifications', icon: 'i-lucide-bell', onSelect: () => { sidebarOpen.value = false } },
    { label: 'Security', to: '/settings/security', icon: 'i-lucide-shield', onSelect: () => { sidebarOpen.value = false } }
  ])

  return [
    { label: 'Projects', icon: 'i-lucide-library', to: '/', active: isProjectsRoute.value, onSelect: () => { sidebarOpen.value = false } },
    { label: 'Tasks', icon: 'i-lucide-clipboard-list', to: '/tasks', active: isTaskRoute.value, onSelect: () => { sidebarOpen.value = false } },
    { label: 'Toolkit', icon: 'i-lucide-tool-case', defaultOpen: hasActive(toolkitChildren), type: 'trigger', children: toolkitChildren },
    { label: 'Workspace', icon: 'i-lucide-layers', defaultOpen: hasActive(workspaceChildren), type: 'trigger', children: workspaceChildren },
    { label: 'Settings', icon: 'i-lucide-settings', defaultOpen: hasActive(settingsChildren), type: 'trigger', children: settingsChildren }
  ]
})

const adminNavigation = computed<NavigationMenuItem[]>(() => {
  const systemChildren = withActive([
    { label: 'Actuator', icon: 'i-lucide-heart-pulse', to: '/admin/actuator' },
    { label: 'IIIF Settings', icon: 'i-lucide-gauge', to: '/admin/iiif-settings' },
    { label: 'Errors', icon: 'i-lucide-bug', to: '/admin/errors' }
  ])
  const actionChildren = withActive([
    { label: 'Actions', icon: 'i-lucide-circle-play', to: '/admin/actions' },
    { label: 'Action Runs', icon: 'i-lucide-list-ordered', to: '/admin/action-runs' }
  ])
  const dataManagementChildren = withActive([
    { label: 'Quotas', icon: 'i-lucide-hard-drive', to: '/admin/quotas' },
    { label: 'Import', icon: 'i-lucide-folder-input', to: '/admin/import' },
    { label: 'Backup', icon: 'i-lucide-database-backup', to: '/admin/backup' },
    { label: 'Storage', icon: 'i-lucide-trash-2', to: '/admin/storage' },
    { label: 'Search Index', icon: 'i-lucide-search', to: '/admin/search-index' }
  ])
  const directoryChildren = withActive([
    { label: 'Users', icon: 'i-lucide-users', to: '/admin/users' },
    { label: 'Workspaces', icon: 'i-lucide-layers', to: '/admin/workspaces' }
  ])

  return [
    { label: 'Overview', icon: 'i-lucide-layout-dashboard', to: '/admin', active: route.path === '/admin' },
    { label: 'System', icon: 'i-lucide-server', defaultOpen: hasActive(systemChildren), type: 'trigger', children: systemChildren },
    { label: 'Actions', icon: 'i-lucide-circle-play', defaultOpen: hasActive(actionChildren), type: 'trigger', children: actionChildren },
    { label: 'Data Management', icon: 'i-lucide-database', defaultOpen: hasActive(dataManagementChildren), type: 'trigger', children: dataManagementChildren },
    { label: 'Directory', icon: 'i-lucide-users', defaultOpen: hasActive(directoryChildren), type: 'trigger', children: directoryChildren }
  ]
})

const navigation = computed(() => isAdmin.value ? adminNavigation.value : defaultNavigation.value)
const utilityButtonClass = 'size-7 justify-center p-0'
const collapseButtonClass = 'size-7 justify-center p-0 [&_[data-slot=leadingIcon]]:size-4'
const utilityIconClass = 'size-4'
const notificationChipUi = {
  base: 'h-4 min-w-4 translate-x-1 -translate-y-1 px-1 text-[10px] leading-none ring-2 ring-bg'
}

const collapsedNavigation = computed<NavigationMenuItem[]>(() => {
  if (isAdmin.value) return adminNavigation.value

  return defaultNavigation.value.map((item) => {
    if (item.label !== 'Toolkit' || !Array.isArray(item.children)) {
      return item
    }

    const flattenedChildren: NavigationMenuItem[] = []
    for (const child of item.children as NavigationMenuItem[]) {
      if (child.label === 'Text' && Array.isArray(child.children)) {
        flattenedChildren.push(...(child.children as NavigationMenuItem[]))
      } else {
        flattenedChildren.push(child)
      }
    }

    return { ...item, children: flattenedChildren }
  })
})

function openNotifications() {
  isNotificationsSlideoverOpen.value = true
}
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
          label="Quick Search..."
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

        <div v-if="isMobileSidebar" class="mt-auto flex w-full items-center justify-between">
          <UTooltip text="Notifications" :content="{ side: 'top' }">
            <UChip
              inset
              size="3xl"
              :show="unreadCount > 0"
              :text="unreadCount"
              color="error"
              position="top-right"
              :ui="notificationChipUi"
            >
              <UButton
                color="neutral"
                variant="ghost"
                square
                size="md"
                :class="utilityButtonClass"
                aria-label="Open notifications"
                @click="openNotifications"
              >
                <UIcon name="i-lucide-bell" :class="utilityIconClass" />
              </UButton>
            </UChip>
          </UTooltip>
          <AppStatusPopoverTrigger :collapsed="false" />
        </div>
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
        :kbds="['meta', 'k']"
        label="Quick Search..."
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
        :items="collapsed ? collapsedNavigation : navigation"
        orientation="vertical"
        tooltip
        popover
      />

      <LayoutSidebarFavorites v-if="!isAdmin" :collapsed="collapsed" class="mt-4" />

      <div
        v-if="!isMobileSidebar"
        :class="collapsed ? 'mt-auto flex flex-col items-center gap-2' : 'mt-auto flex items-center justify-between'"
      >
        <UDashboardSidebarCollapse
          square
          :size="collapsed ? 'sm' : 'md'"
          :class="collapseButtonClass"
        />
        <div :class="collapsed ? 'flex flex-col items-center gap-2' : 'flex items-center gap-1'">
          <UTooltip :text="'Notifications'" :content="{ side: collapsed ? 'right' : 'top' }">
            <UChip
              inset
              size="3xl"
              :show="unreadCount > 0"
              :text="unreadCount"
              color="error"
              position="top-right"
              :ui="notificationChipUi"
            >
              <UButton
                color="neutral"
                variant="ghost"
                square
                :size="collapsed ? 'sm' : 'md'"
                :class="utilityButtonClass"
                aria-label="Open notifications"
                @click="openNotifications"
              >
                <UIcon name="i-lucide-bell" :class="utilityIconClass" />
              </UButton>
            </UChip>
          </UTooltip>
          <AppStatusPopoverTrigger :collapsed="collapsed" />
        </div>
      </div>
    </template>

    <template #footer="{ collapsed }">
      <UserMenu :collapsed="collapsed" />
    </template>
  </UDashboardSidebar>
</template>
