<script setup lang="ts">
import { useMediaQuery } from '@vueuse/core'

const props = withDefaults(defineProps<{
  collapsed?: boolean
  forcePopover?: boolean
}>(), {
  collapsed: false,
  forcePopover: false
})

const {
  activeJobs,
  hasActiveJobs,
  hasIssues,
  issues,
  isOverlayOpen,
  overlayAnchorId,
  closeOverlay,
  openOverlay,
  setOverlayAnchor,
  toggleOverlay
} = useStatusCenter()

const triggerId = useId()
const isLargeScreen = useMediaQuery('(min-width: 1024px)')
const shouldUsePopover = computed(() => props.forcePopover || isLargeScreen.value)
const isCurrentPopoverOpen = computed(() => isOverlayOpen.value && overlayAnchorId.value === triggerId)
const popoverContent = computed(() => ({
  side: 'right' as const,
  align: 'center' as const,
  sideOffset: 12
}))

const buttonLabel = computed(() => {
  if (hasActiveJobs.value) {
    return `Jobs (${activeJobs.value.length} running)`
  }
  if (hasIssues.value) {
    return `Jobs (${issues.value.length} issue${issues.value.length === 1 ? '' : 's'})`
  }
  return 'Jobs'
})

watch([shouldUsePopover, isOverlayOpen], ([usesPopover, open]) => {
  if (!open) return
  if (usesPopover && overlayAnchorId.value === null) {
    setOverlayAnchor(triggerId)
    return
  }
  if (!usesPopover && overlayAnchorId.value === triggerId) {
    setOverlayAnchor(null)
  }
}, { immediate: true })

onBeforeUnmount(() => {
  if (overlayAnchorId.value === triggerId) {
    setOverlayAnchor(null)
  }
})

function handlePopoverOpenUpdate(open: boolean) {
  if (open) {
    openOverlay(triggerId)
    return
  }
  if (overlayAnchorId.value === triggerId) {
    closeOverlay()
  }
}

function handleFixedOverlayToggle() {
  toggleOverlay(null)
}
</script>

<template>
  <UPopover
    v-if="shouldUsePopover"
    :open="isCurrentPopoverOpen"
    :dismissible="false"
    :content="popoverContent"
    @update:open="handlePopoverOpenUpdate"
  >
    <UTooltip :text="buttonLabel" :content="{ side: collapsed ? 'right' : 'top' }">
      <UButton
        color="neutral"
        variant="ghost"
        :size="collapsed ? 'sm' : 'md'"
        square
        :aria-label="buttonLabel"
        :class="isCurrentPopoverOpen ? 'bg-elevated' : undefined"
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

    <template #content>
      <AppStatusPopoverContent
        show-close
        @close="closeOverlay"
      />
    </template>
  </UPopover>

  <UTooltip v-else :text="buttonLabel" :content="{ side: collapsed ? 'right' : 'top' }">
    <UButton
      color="neutral"
      variant="ghost"
      :size="collapsed ? 'sm' : 'md'"
      square
      :aria-label="buttonLabel"
      :class="isOverlayOpen && overlayAnchorId === null ? 'bg-elevated' : undefined"
      @click="handleFixedOverlayToggle"
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
