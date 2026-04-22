<script setup lang="ts">
type EditorPanelParams = {
  projectId?: string
  pageId?: string
  canvasId?: string
  variantId?: string
}

type DockviewPanelProps<TParams> = {
  params: TParams
  api: { id: string }
  containerApi: unknown
  tabLocation?: unknown
}

const props = defineProps<{ params: DockviewPanelProps<EditorPanelParams> }>()

const editorStore = useEditorStore()
const uiStore = useEditorUiStore()

const canvasId = computed(() => props.params.params?.canvasId || props.params.api.id)

const uiMode = computed(() => editorStore.effectiveUiMode(canvasId.value))
const isTextVisualMode = computed(() => uiMode.value === 'text' && uiStore.textModeSubmode === 'visual')

const requestedPageId = computed(() => props.params.params?.pageId)
const requestedProjectId = computed(() => props.params.params?.projectId)
const requestedVariantId = computed(() => props.params.params?.variantId)

const src = computed(() => {
  const id = canvasId.value
  if (!id) return ''
  return editorStore.canvases[id]?.imageSrc ?? ''
})

onMounted(async () => {
  const id = canvasId.value
  if (!id) return

  const pageId = requestedPageId.value
  const projectId = requestedProjectId.value || editorStore.canvases[id]?.projectId || null
  if (pageId && projectId && editorStore.canvases[id]?.pageId !== pageId) {
    await editorStore.loadPageIntoCanvas(id, projectId, pageId, requestedVariantId.value)
  }
})
</script>

<template>
  <div v-if="canvasId" class="h-full w-full">
    <EditorCollaborationHost :canvas-id="canvasId" />

    <Editor
      v-if="(uiMode === 'layout' || isTextVisualMode) && !!src"
      class="h-full w-full"
      :src="src"
      :canvas-id="canvasId"
    />

    <EditorTextView
      v-else-if="uiMode === 'text'"
      class="h-full w-full"
      :canvas-id="canvasId"
    />
  </div>
</template>
