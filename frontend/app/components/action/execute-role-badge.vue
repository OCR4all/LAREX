<script setup lang="ts">
import type { ActionExecuteRole } from '~/types/action'

// An unknown role (null/undefined) renders nothing instead of guessing.
defineProps<{
  role?: ActionExecuteRole | null
  size?: 'xs' | 'sm' | 'md'
}>()
</script>

<template>
  <UBadge
    v-if="role"
    :color="role === 'CURATOR' ? 'warning' : 'neutral'"
    :icon="role === 'CURATOR' ? 'i-lucide-shield-user' : 'i-lucide-user'"
    :size="size ?? 'sm'"
    :title="role === 'CURATOR' ? 'Only curators can run this' : 'Editors and curators can run this'"
    variant="subtle"
    class="shrink-0"
  >
    {{ role === 'CURATOR' ? 'Curator' : 'Editor' }}
  </UBadge>
</template>
