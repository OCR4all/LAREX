<script setup lang="ts">
import type { NavigationMenuItem } from '@nuxt/ui'

definePageMeta({ layout: 'admin', middleware: 'admin' })

const links = [[{
  label: 'Health',
  icon: 'i-lucide-heart-pulse',
  to: '/admin/actuator',
  exact: true
}]] satisfies NavigationMenuItem[][]
</script>

<template>
  <UDashboardPanel id="admin-actuator" :ui="{ body: 'lg:py-12' }">
    <template #header>
      <UDashboardNavbar title="System Health & Monitoring">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar>
        <UNavigationMenu :items="links" highlight class="-mx-1 flex-1" />
      </UDashboardToolbar>
    </template>

    <template #body>
      <div class="flex flex-col gap-4 sm:gap-6 lg:gap-12 w-full lg:max-w-4xl mx-auto">
        <NuxtPage />
      </div>
    </template>
  </UDashboardPanel>
</template>
