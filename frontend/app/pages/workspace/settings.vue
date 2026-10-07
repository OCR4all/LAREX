<script setup lang="ts">
await useWorkspaceBootstrap()

const route = useRoute()
const isActionsPage = computed(() => route.path.replace(/\/$/, '') === '/workspace/settings/actions')

const workspaceStore = useWorkspaceStore()
const currentWorkspace = computed(() => workspaceStore.currentWorkspace)

const workspaceName = computed(() => currentWorkspace.value?.name || 'Workspace')
</script>

<template>
  <UDashboardPanel id="workspace-settings" :ui="{ body: 'lg:py-12' }">
    <template #header>
      <UDashboardNavbar :title="`${workspaceName} Settings`" />

      <UDashboardToolbar>
        <WorkspaceSettingsNavigation />
      </UDashboardToolbar>
    </template>

    <template #body>
      <div
        class="flex flex-col gap-4 sm:gap-6 lg:gap-12 w-full min-w-0 mx-auto"
        :class="isActionsPage ? 'lg:max-w-7xl' : 'lg:max-w-2xl'"
      >
        <NuxtPage />
      </div>
    </template>
  </UDashboardPanel>
</template>
