<script setup lang="ts">
const props = defineProps({
  title: { type: String, required: true }
})
</script>

<template>
  <div class="flex items-center gap-2">
    <h3 class="text-sm font-semibold tracking-wider text-neutral-700 dark:text-neutral-300">
      {{ props.title }}
    </h3>
    <div class="flex-1 h-px bg-border" />
  </div>
</template>
