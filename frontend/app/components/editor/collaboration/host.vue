<script setup lang="ts">
import { useEditorCanvasCollaboration } from '@/composables/editor/use-editor-canvas-collaboration'

const props = defineProps<{ canvasId: string }>()

useEditorCanvasCollaboration(props.canvasId)
</script>

<template>
  <span class="hidden" aria-hidden="true" />
</template>
