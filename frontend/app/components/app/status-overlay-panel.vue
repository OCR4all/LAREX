<script setup lang="ts">
const {
  isOverlayOpen,
  isOverlayMinimized,
  closeOverlay,
  toggleOverlayMinimized
} = useStatusCenter()
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
      v-if="isOverlayOpen"
      class="fixed bottom-4 right-4 z-50"
    >
      <AppStatusPopoverContent
        show-close
        show-minimize
        :minimized="isOverlayMinimized"
        @close="closeOverlay"
        @toggle-minimized="toggleOverlayMinimized"
      />
    </div>
  </Transition>
</template>
