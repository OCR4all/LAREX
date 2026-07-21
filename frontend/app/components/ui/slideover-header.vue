<script setup lang="ts">
const props = defineProps<{
  title: string
  icon: string
  description?: string
  iconClass?: string
}>()

const resolvedDescription = computed(() => props.description || 'Review details and available actions.')
</script>

<template>
  <div class="flex min-w-0 items-start gap-3 pr-8">
    <div class="flex size-10 shrink-0 items-center justify-center rounded-md bg-navy-600">
      <UIcon
        :name="icon"
        :class="['size-4 text-white', iconClass]"
      />
    </div>
    <div class="min-w-0">
      <h2 class="text-highlighted font-semibold truncate">
        {{ title }}
      </h2>
      <p class="mt-0.5 text-muted text-sm">
        {{ resolvedDescription }}
      </p>
    </div>
  </div>
</template>
