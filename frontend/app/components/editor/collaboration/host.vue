<script setup lang="ts">
import { useEditorSession } from '@/session/editor/editor-session'
import { useEditorCollaboration } from '@/composables/editor/use-editor-collaboration'

const props = defineProps<{ canvasId: string }>()

const editorStore = useEditorStore()
const collaboration = useEditorCollaboration()
const session = useEditorSession(props.canvasId)

const canvasState = computed(() => editorStore.canvases[props.canvasId] ?? null)
const projectId = computed(() => canvasState.value?.projectId ?? null)
const pageId = computed(() => canvasState.value?.pageId ?? null)
const isLoadingAnnotations = computed(() => canvasState.value?.isLoadingAnnotations ?? false)

watch(
  [projectId, pageId, isLoadingAnnotations],
  ([nextProjectId, nextPageId, nextIsLoading], _prev, onCleanup) => {
    if (!nextProjectId || !nextPageId || nextIsLoading) return

    let disposed = false

    void collaboration.ensureCanvasRoom(props.canvasId)
      .then(() => {
        if (!disposed) {
          collaboration.attachCanvasSession(props.canvasId, session)
        }
      })
      .catch((error) => {
        console.error('[editor-collaboration] Failed to join room:', error)
      })

    onCleanup(() => {
      disposed = true
      collaboration.leaveCanvasRoom(props.canvasId)
    })
  },
  { immediate: true }
)
</script>

<template>
  <span class="hidden" aria-hidden="true" />
</template>
