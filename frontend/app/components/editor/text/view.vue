<script setup lang="ts">
const props = defineProps<{ canvasId?: string | null }>()
</script>

<template>
  <div class="h-full flex flex-col bg-background">
    <EditorTextLineList :canvas-id="props.canvasId" class="h-full w-full" />
  </div>
</template>
