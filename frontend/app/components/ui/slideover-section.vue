<script setup lang="ts">
defineProps<{
  title: string
  description?: string
  icon: string
}>()
</script>

<template>
  <UCard variant="subtle">
    <template #header>
      <div class="flex items-start gap-3">
        <div class="flex size-9 shrink-0 items-center justify-center rounded-md bg-accented">
          <UIcon :name="icon" class="size-4 text-muted" />
        </div>
        <div class="min-w-0">
          <h3 class="text-sm font-semibold text-highlighted">
            {{ title }}
          </h3>
          <p v-if="description" class="mt-1 text-sm text-muted">
            {{ description }}
          </p>
        </div>
      </div>
    </template>

    <slot />
  </UCard>
</template>
