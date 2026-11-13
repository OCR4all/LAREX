<script setup lang="ts">
const props = defineProps<{
  global: boolean
  size?: 'xs' | 'sm' | 'md'
}>()

const meta = computed(() => actionScopeBadge(props.global))
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
