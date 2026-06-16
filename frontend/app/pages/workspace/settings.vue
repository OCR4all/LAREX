<script setup lang="ts">
import type { NavigationMenuItem } from '@nuxt/ui'

await useWorkspaceBootstrap()

const workspaceStore = useWorkspaceStore()
const currentWorkspace = computed(() => workspaceStore.currentWorkspace)

const links = computed<NavigationMenuItem[][]>(() => [[{
  label: 'General',
  icon: 'i-lucide-settings',
  to: '/workspace/settings',
  exact: true
}, {
  label: 'Members',
  icon: 'i-lucide-users',
  to: '/workspace/settings/members'
}, {
  label: 'Requests',
  icon: 'i-lucide-git-pull-request',
  to: '/workspace/settings/requests'
}]])

const workspaceName = computed(() => currentWorkspace.value?.name || 'Workspace')
</script>

<template>
  <UDashboardPanel id="workspace-settings" :ui="{ body: 'lg:py-12' }">
    <template #header>
      <UDashboardNavbar :title="`${workspaceName} Settings`" />

      <UDashboardToolbar>
        <UNavigationMenu :items="links" highlight class="-mx-1 flex-1" />
      </UDashboardToolbar>
    </template>

    <template #body>
      <div class="flex flex-col gap-4 sm:gap-6 lg:gap-12 w-full lg:max-w-2xl mx-auto">
        <NuxtPage />
      </div>
    </template>
  </UDashboardPanel>
</template>
