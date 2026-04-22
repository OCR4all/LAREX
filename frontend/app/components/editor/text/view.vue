<script setup lang="ts">
import { useEditorCollaboration } from '@/composables/editor/use-editor-collaboration'

const props = defineProps<{ canvasId?: string | null }>()

const editorStore = useEditorStore()
const collaboration = useEditorCollaboration()
const effectiveCanvasId = computed(() => props.canvasId ?? editorStore.activeCanvasId)
const canvasState = computed(() => {
  const canvasId = effectiveCanvasId.value
  return canvasId ? editorStore.canvases[canvasId] ?? null : null
})

function emitPresence() {
  const canvasId = effectiveCanvasId.value
  const canvas = canvasState.value
  if (!canvasId || !canvas?.projectId || !canvas.pageId || !canvas.xmlFileId) return

  collaboration.updatePresence(canvasId, {
    projectId: canvas.projectId,
    pageId: canvas.pageId,
    xmlId: canvas.xmlFileId,
    canvasId,
    variantId: canvas.imageVariantId ?? null,
    uiMode: 'text',
    active: editorStore.activeCanvasId === canvasId
  })
}

watch(
  () => collaboration.isCollaborativeCanvas(effectiveCanvasId.value ?? ''),
  (ready) => {
    if (ready) {
      emitPresence()
    }
  },
  { immediate: true }
)

watch(
  () => [
    effectiveCanvasId.value,
    canvasState.value?.projectId ?? null,
    canvasState.value?.pageId ?? null,
    canvasState.value?.xmlFileId ?? null,
    canvasState.value?.imageVariantId ?? null,
    editorStore.activeCanvasId
  ],
  () => {
    emitPresence()
  }
)
</script>

<template>
  <div class="h-full flex flex-col bg-background">
    <EditorTextLineList :canvas-id="props.canvasId" class="h-full w-full" />
  </div>
</template>
