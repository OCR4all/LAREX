<script setup lang="ts">
import { useEditorStore } from '@/stores/editor/editor.store'

type TextPanelParams = {
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

const props = defineProps<{ params: DockviewPanelProps<TextPanelParams> }>()

const editorStore = useEditorStore()

const canvasId = computed(() => props.params.params?.canvasId || props.params.api.id)

const requestedProjectId = computed(() => props.params.params?.projectId)
const requestedPageId = computed(() => props.params.params?.pageId)
const requestedVariantId = computed(() => props.params.params?.variantId)

onMounted(async () => {
  const id = canvasId.value
  if (!id) return

  const projectId = requestedProjectId.value || editorStore.canvases[id]?.projectId || null
  const pageId = requestedPageId.value
  if (projectId && pageId && editorStore.canvases[id]?.pageId !== pageId) {
    await editorStore.loadPageIntoCanvas(id, projectId, pageId, requestedVariantId.value)
  }
})
</script>

<template>
  <EditorTextView v-if="canvasId" class="h-full w-full" :canvas-id="canvasId" />
</template>
