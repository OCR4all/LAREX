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
const isTextFullMode = computed(() => uiMode.value === 'text' && uiStore.textModeSubmode === 'full')
const isTextCanvasMode = computed(() => isTextVisualMode.value || isTextFullMode.value)

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

    <EditorTextFullView
      v-if="isTextFullMode && !!src"
      class="h-full w-full"
      :src="src"
      :canvas-id="canvasId"
    />

    <Editor
      v-else-if="(uiMode === 'layout' || isTextVisualMode) && !!src"
      class="h-full w-full"
      :src="src"
      :canvas-id="canvasId"
    />

    <div
      v-else-if="uiMode === 'layout' || isTextCanvasMode"
      class="flex h-full w-full items-center justify-center bg-default"
    >
      <div class="relative h-full w-full overflow-hidden">
        <USkeleton class="absolute inset-0 h-full w-full rounded-none" />
        <div class="absolute inset-0 flex items-center justify-center bg-default/20">
          <div class="flex items-center gap-3 rounded-md border border-default bg-default/90 px-4 py-2.5 shadow-lg">
            <Icon name="i-lucide-loader-2" class="h-4 w-4 animate-spin text-muted" />
            <span class="text-sm font-medium text-highlighted">Preparing page...</span>
          </div>
        </div>
      </div>
    </div>

    <EditorTextView
      v-else-if="uiMode === 'text'"
      class="h-full w-full"
      :canvas-id="canvasId"
    />
  </div>
</template>
