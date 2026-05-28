<script setup lang="ts">
defineProps<{
  title: string
  icon: string
  description?: string
  iconClass?: string
}>()
</script>

<template>
  <div class="flex min-w-0 items-start gap-2 pr-8">
    <UIcon
      :name="icon"
      :class="['mt-0.5 size-5 shrink-0 text-muted', iconClass]"
    />
    <div class="min-w-0">
      <h2 class="text-highlighted font-semibold truncate">
        {{ title }}
      </h2>
      <p v-if="description" class="mt-1 text-muted text-sm">
        {{ description }}
      </p>
    </div>
  </div>
</template>
