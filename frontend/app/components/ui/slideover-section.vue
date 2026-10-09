<script setup lang="ts">
defineProps<{
  title: string
  description?: string
  icon: string
}>()
</script>

<template>
  <UCard variant="subtle" class="bg-elevated/20 dark:bg-elevated/50">
    <template #header>
      <div class="flex items-start gap-3">
        <div class="flex size-9 shrink-0 items-center justify-center rounded-md bg-accented shadow-xs ring ring-inset ring-white/10 dark:shadow-black/40 dark:ring-white/20">
          <UIcon :name="icon" class="size-4 text-muted" />
        </div>
        <div class="min-w-0">
          <h3 class="text-sm leading-4.5 font-semibold text-highlighted">
            {{ title }}
          </h3>
          <p v-if="description" class="text-sm leading-4.5 text-muted">
            {{ description }}
          </p>
        </div>
      </div>
    </template>

    <slot />
  </UCard>
</template>
