<script setup lang="ts">
const uiStore = useEditorUiStore()
</script>

<template>
  <div class="flex items-center justify-between gap-3">
    <span class="text-sm">Show Comments on Canvas</span>
    <USwitch
      aria-label="Show Comments on Canvas"
      :model-value="uiStore.commentsOverlay.visible"
      @update:model-value="uiStore.toggleCommentsOverlay()"
    />
  </div>
</template>
