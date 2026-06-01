<script setup lang="ts">
import { useMediaQuery } from '@vueuse/core'

const {
  hasActiveJobs,
  activeJobs,
  hasIssues,
  issues,
  isOverlayOpen,
  toggleOverlay
} = useStatusCenter()

const isMobile = useMediaQuery('(max-width: 1023px)')

const buttonLabel = computed(() => {
  if (hasActiveJobs.value) {
    return `Status (${activeJobs.value.length} running)`
  }
  if (hasIssues.value) {
    return `Status (${issues.value.length} issue${issues.value.length === 1 ? '' : 's'})`
  }
  return 'Status'
})
</script>

<template>
  <div v-if="isMobile" class="fixed bottom-4 right-4 z-50">
    <UTooltip text="Status" :content="{ side: 'left' }">
      <UButton
        color="neutral"
        variant="soft"
        size="lg"
        square
        :aria-label="buttonLabel"
        :class="[
          'shadow-md ring-1 ring-default',
          isOverlayOpen ? 'bg-elevated' : ''
        ]"
        @click="toggleOverlay"
      >
        <span class="relative inline-flex">
          <UIcon name="i-lucide-activity" class="size-5" />
          <template v-if="hasActiveJobs">
            <span class="absolute -right-0.5 -top-0.5 inline-flex h-2.5 w-2.5">
              <span class="absolute inline-flex h-full w-full animate-ping rounded-full bg-success/70" />
              <span class="relative inline-flex h-2.5 w-2.5 rounded-full bg-success" />
            </span>
          </template>
          <template v-else-if="hasIssues">
            <span class="absolute -right-0.5 -top-0.5 inline-flex h-2.5 w-2.5 rounded-full bg-error" />
          </template>
        </span>
      </UButton>
    </UTooltip>
  </div>
</template>
