<script setup lang="ts">
import type { NavigationMenuItem } from '@nuxt/ui'

const links: NavigationMenuItem[][] = [[{
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
}, {
  label: 'Actions',
  icon: 'i-lucide-zap',
  to: '/workspace/settings/actions'
}, {
  label: 'Queue',
  icon: 'i-lucide-list-ordered',
  to: '/workspace/queue'
}]]
</script>

<template>
  <UNavigationMenu :items="links" highlight class="-mx-1 flex-1" />
</template>
