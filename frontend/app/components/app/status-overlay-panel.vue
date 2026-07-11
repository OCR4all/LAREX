<script setup lang="ts">
import { useMediaQuery } from '@vueuse/core'

const {
  isOverlayOpen,
  isOverlayMinimized,
  overlayAnchorId,
  closeOverlay,
  toggleOverlayMinimized
} = useStatusCenter()

const isMobile = useMediaQuery('(max-width: 1023px)')
const shouldShowFixedOverlay = computed(() => isMobile.value && isOverlayOpen.value && overlayAnchorId.value === null)
</script>

<template>
  <Transition
    enter-active-class="transform transition duration-300 ease-out"
    enter-from-class="translate-y-3 opacity-0"
    enter-to-class="translate-y-0 opacity-100"
    leave-active-class="transform transition duration-200 ease-in"
    leave-from-class="translate-y-0 opacity-100"
    leave-to-class="translate-y-3 opacity-0"
  >
    <div
      v-if="shouldShowFixedOverlay"
      class="fixed bottom-4 left-4 z-50 rounded-lg border border-neutral-100 bg-white shadow-lg dark:border-neutral-900 dark:bg-black"
    >
      <AppStatusPopoverContent
        show-close
        show-minimize
        compact
        :minimized="isOverlayMinimized"
        @close="closeOverlay"
        @toggle-minimized="toggleOverlayMinimized"
      />
    </div>
  </Transition>
</template>
