<script setup lang="ts">
definePageMeta({ layout: 'admin', middleware: 'admin' })

const adminSections = [
  { title: 'Actuator', description: 'Monitor application health and system info', icon: 'i-lucide-heart-pulse', to: '/admin/actuator', group: 'System' },
  { title: 'IIIF Settings', description: 'Configure adaptive IIIF download pacing', icon: 'i-lucide-gauge', to: '/admin/iiif-settings', group: 'System' },
  { title: 'Quotas', description: 'Manage storage quotas for workspaces', icon: 'i-lucide-hard-drive', to: '/admin/quotas', group: 'Data Management' },
  { title: 'Import', description: 'Import files from server directories', icon: 'i-lucide-folder-input', to: '/admin/import', group: 'Data Management' },
  { title: 'Backup', description: 'Create dumps and reseed file-based data', icon: 'i-lucide-database-backup', to: '/admin/backup', group: 'Data Management' },
  { title: 'Actions', description: 'Manage processor definitions and dispatch metadata', icon: 'i-lucide-circle-play', to: '/admin/actions', group: 'Actions' },
  { title: 'Action Runs', description: 'Inspect workspace queues and run history across all Actions', icon: 'i-lucide-list-ordered', to: '/admin/action-runs', group: 'Actions' },
  { title: 'Storage', description: 'Clean up orphaned files and manage storage', icon: 'i-lucide-trash-2', to: '/admin/storage', group: 'Data Management' },
  { title: 'Errors', description: 'Inspect captured API error events by user and workspace', icon: 'i-lucide-bug', to: '/admin/errors', group: 'System' },
  { title: 'Workspaces', description: 'View and manage all workspaces', icon: 'i-lucide-layers', to: '/admin/workspaces', group: 'Directory' },
  { title: 'Users', description: 'View all registered users', icon: 'i-lucide-users', to: '/admin/users', group: 'Directory' },
  { title: 'Search Index', description: 'Rebuild search indexes for page filtering', icon: 'i-lucide-search', to: '/admin/search-index', group: 'Data Management' }
]
</script>

<template>
  <UDashboardPanel id="admin-overview">
    <template #header>
      <UDashboardNavbar title="Overview" />
    </template>

    <template #body>
      <div class="mx-auto flex w-full max-w-7xl flex-col gap-8 px-4 py-8 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-3xl text-center">
          <h1 class="mt-3 text-3xl font-semibold text-highlighted sm:text-4xl">
            Welcome to the Admin Panel
          </h1>
          <p class="mt-4 text-base text-muted sm:text-lg">
            Manage and monitor your <span class="font-italic">LAREX</span> instance
          </p>
        </div>

        <UPageColumns class="gap-4 space-y-4 md:columns-2 xl:columns-3">
          <UPageCard
            v-for="section in adminSections"
            :key="section.to"
            :to="section.to"
            variant="subtle"
            class="min-h-56"
            :ui="{
              container: 'min-h-56 p-6',
              wrapper: 'h-full',
              header: 'mb-0 w-full',
              body: 'flex flex-1 items-end',
              leadingIcon: 'size-5 text-muted'
            }"
          >
            <template #header>
              <div class="flex items-start justify-between gap-4">
                <div class="flex size-12 items-center justify-center rounded-lg ring ring-default bg-default">
                  <UIcon :name="section.icon" class="size-5 text-muted" />
                </div>
                <UBadge color="neutral" variant="outline">
                  {{ section.group }}
                </UBadge>
              </div>
            </template>

            <template #body>
              <div>
                <h2 class="text-lg font-semibold text-highlighted">
                  {{ section.title }}
                </h2>
                <p class="mt-2 text-sm leading-6 text-muted">
                  {{ section.description }}
                </p>
              </div>
            </template>
          </UPageCard>
        </UPageColumns>
      </div>
    </template>
  </UDashboardPanel>
</template>
