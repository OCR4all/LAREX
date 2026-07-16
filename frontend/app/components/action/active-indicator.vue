<script setup lang="ts">
withDefaults(defineProps<{
  label?: string
}>(), {
  label: 'LAREX Action running'
})
</script>

<template>
  <span
    class="relative inline-flex size-2.5 shrink-0"
    role="status"
    :aria-label="label"
    :title="label"
  >
    <span class="absolute inline-flex size-full animate-ping rounded-full bg-emerald-400 opacity-60" />
    <span class="relative inline-flex size-2.5 rounded-full bg-emerald-500" />
  </span>
</template>
