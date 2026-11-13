<script setup lang="ts">
import type { ActionKind } from '~/types/action'

const props = defineProps<{
  kind?: ActionKind
  workflow?: boolean
  size?: 'xs' | 'sm' | 'md'
}>()

const meta = computed(() => actionKindBadge(props.kind, props.workflow))
</script>

<template>
  <UBadge
    color="neutral"
    variant="subtle"
    :size="size ?? 'md'"
    :icon="meta.icon"
    :class="meta.class"
    class="shrink-0"
  >
    {{ meta.label }}
  </UBadge>
</template>
