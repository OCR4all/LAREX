<script setup lang="ts">
withDefaults(defineProps<{
  collapsed?: boolean
}>(), {
  collapsed: false
})

const {
  activeJobs,
  hasActiveJobs,
  hasIssues,
  issues,
  isOverlayOpen,
  toggleOverlay
} = useStatusCenter()

const buttonLabel = computed(() => {
  if (hasActiveJobs.value) {
    return `Jobs (${activeJobs.value.length} running)`
  }
  if (hasIssues.value) {
    return `Jobs (${issues.value.length} issue${issues.value.length === 1 ? '' : 's'})`
  }
  return 'Jobs'
})
</script>

<template>
  <UTooltip :text="buttonLabel" :content="{ side: collapsed ? 'right' : 'top' }">
    <UButton
      color="neutral"
      variant="ghost"
      :size="collapsed ? 'sm' : 'md'"
      square
      :aria-label="buttonLabel"
      :class="isOverlayOpen ? 'bg-elevated' : undefined"
      @click="toggleOverlay"
    >
      <span class="relative inline-flex">
        <UIcon name="i-lucide-activity" class="size-4" />
        <template v-if="hasActiveJobs">
          <span class="absolute -right-0.5 -top-0.5 inline-flex h-2 w-2">
            <span class="absolute inline-flex h-full w-full animate-ping rounded-full bg-success/70" />
            <span class="relative inline-flex h-2 w-2 rounded-full bg-success" />
          </span>
        </template>
        <template v-else-if="hasIssues">
          <span class="absolute -right-0.5 -top-0.5 inline-flex h-2 w-2 rounded-full bg-error" />
        </template>
      </span>
    </UButton>
  </UTooltip>
</template>
