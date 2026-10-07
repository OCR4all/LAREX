<script setup lang="ts">
await useWorkspaceBootstrap()

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
      <div class="flex flex-col gap-4 sm:gap-6 lg:gap-12 w-full lg:max-w-2xl mx-auto">
        <NuxtPage />
      </div>
    </template>
  </UDashboardPanel>
</template>
