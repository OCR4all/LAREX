<script setup lang="ts">
const props = withDefaults(defineProps<{
  title: string
  icon: string
  description?: string
  iconClass?: string
  tone?: 'default' | 'error' | 'warning'
}>(), {
  tone: 'default'
})

const resolvedDescription = computed(() => props.description || 'Review details and available actions.')
</script>

<template>
  <div class="flex min-h-10 min-w-0 flex-1 items-start gap-3 pr-8">
    <div
      class="flex size-10 shrink-0 items-center justify-center rounded-md"
      :class="tone === 'error' ? 'bg-error/10' : tone === 'warning' ? 'bg-warning/10' : 'bg-navy-600 shadow-xs ring ring-inset ring-white/10 dark:shadow-black/40 dark:ring-white/20'"
    >
      <UIcon
        :name="icon"
        :class="['size-4', tone === 'error' ? 'text-error' : tone === 'warning' ? 'text-warning' : 'text-white', iconClass]"
        aria-hidden="true"
      />
    </div>
    <div class="min-w-0">
      <h2 class="break-words text-base leading-6 font-semibold text-highlighted">
        {{ title }}
      </h2>
      <div class="mt-0.5 text-sm text-muted">
        <slot name="description">
          <p>{{ resolvedDescription }}</p>
        </slot>
      </div>
    </div>
  </div>
</template>
