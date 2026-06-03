<script setup lang="ts">
definePageMeta({ layout: 'admin', middleware: 'admin' })

const adminSections = [
  { title: 'Actuator', description: 'Monitor application health and system info', icon: 'i-lucide-heart-pulse', to: '/admin/actuator' },
  { title: 'Quotas', description: 'Manage storage quotas for workspaces', icon: 'i-lucide-hard-drive', to: '/admin/quotas' },
  { title: 'Import', description: 'Import files from server directories', icon: 'i-lucide-folder-input', to: '/admin/import' },
  { title: 'Backup', description: 'Create dumps and reseed file-based data', icon: 'i-lucide-database-backup', to: '/admin/backup' },
  { title: 'Actions', description: 'Manage processor definitions and dispatch metadata', icon: 'i-lucide-circle-play', to: '/admin/actions' },
  { title: 'Action Runs', description: 'Inspect workspace queues and run history across all Actions', icon: 'i-lucide-list-ordered', to: '/admin/action-runs' },
  { title: 'Storage', description: 'Clean up orphaned files and manage storage', icon: 'i-lucide-trash-2', to: '/admin/storage' },
  { title: 'Errors', description: 'Inspect captured API error events by user and workspace', icon: 'i-lucide-bug', to: '/admin/errors' },
  { title: 'Workspaces', description: 'View and manage all workspaces', icon: 'i-lucide-layers', to: '/admin/workspaces' },
  { title: 'Users', description: 'View all registered users', icon: 'i-lucide-users', to: '/admin/users' },
  { title: 'Search Index', description: 'Rebuild search indexes for page filtering', icon: 'i-lucide-search', to: '/admin/search-index' }
]
</script>

<template>
  <UDashboardPanel id="admin-dashboard">
    <template #header>
      <UDashboardNavbar title="Dashboard">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <div class="p-6 space-y-8">
        <div>
          <h1 class="text-2xl font-bold mb-2">
            Welcome to the Admin Panel
          </h1>
          <p class="text-muted">
            Manage and monitor your LAREX instance
          </p>
        </div>

        <UPageColumns class="sm:grid-cols-2 lg:grid-cols-3">
          <UPageCard
            v-for="section in adminSections"
            :key="section.to"
            :to="section.to"
            :icon="section.icon"
            :title="section.title"
            :description="section.description"
          />
        </UPageColumns>
      </div>
    </template>
  </UDashboardPanel>
</template>
