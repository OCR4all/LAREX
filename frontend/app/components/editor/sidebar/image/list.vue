<script setup lang="ts">
import ImageItem from './item.vue'
import { useEditorStore } from '@/stores/editor/editor.store'
import { useEditorSessionStore } from '@/stores/editor/editor.session.store'
import type { PageData } from '@/stores/editor/types'

const emit = defineEmits<{
  'select-page': [pageId: string, variantId?: string, projectId?: string]
  'unload-page': [pageId: string, projectId?: string]
}>()

const props = withDefaults(defineProps<{
  pages?: PageData[]
  projectId?: string | null
  filter?: string
  onlyWithOpenSubtasks?: boolean
  openSubtaskCountByPage?: Record<string, number>
  filteredPageIds?: string[] | null
}>(), {
  pages: () => [],
  projectId: null,
  filter: '',
  onlyWithOpenSubtasks: false,
  openSubtaskCountByPage: () => ({}),
  filteredPageIds: null
})

const editorStore = useEditorStore()
const sessionStore = useEditorSessionStore()

const currentPageId = computed(() => editorStore.currentPageId)

const filteredPages = computed<PageData[]>(() => {
  const q = props.filter.trim().toLowerCase()
  let result = props.pages

  if (q) {
    result = result.filter(p => (p.label ?? '').toLowerCase().includes(q))
  }

  if (props.onlyWithOpenSubtasks) {
    result = result.filter(p => (props.openSubtaskCountByPage?.[p.id] ?? 0) > 0)
  }

  if (props.filteredPageIds && props.filteredPageIds.length > 0) {
    const pageIdSet = new Set(props.filteredPageIds)
    result = result.filter(p => pageIdSet.has(p.id))
  }

  return result
})

function getDisplayedVariant(page: PageData) {
  return editorStore.getDisplayedVariantForPage(page)
}

function getPreviewUrl(page: PageData) {
  return editorStore.getPreviewUrlForPage(page)
}

const variantItemsByPageId = computed<Record<string, Array<{ label: string, value: string }>>>(() => {
  const map: Record<string, Array<{ label: string, value: string }>> = {}
  for (const page of props.pages) {
    map[page.id] = (page.imageVariants ?? []).map(variant => ({
      label: variant.label,
      value: variant.id
    }))
  }
  return map
})

function handleVariantChange(page: PageData, variantId: string) {
  editorStore.setSelectedVariantOverride(page.id, variantId, props.projectId ?? undefined)
  if (props.projectId) {
    sessionStore.setSelectedVariant(props.projectId, page.id, variantId)
  }

  if (page.id === currentPageId.value) {
    const canvasId = editorStore.activeCanvasId
    if (!canvasId) return
    editorStore.switchImageVariantForCanvas(canvasId, variantId)
  }
}

function handlePageClick(page: PageData) {
  const variant = getDisplayedVariant(page)
  emit('select-page', page.id, variant?.id ?? undefined, props.projectId ?? undefined)
}

function handlePageUnload(page: PageData) {
  emit('unload-page', page.id, props.projectId ?? page.projectId)
}
</script>

<template>
  <div class="px-3 py-2 space-y-3">
    <div v-if="filteredPages.length === 0" class="text-sm text-muted-foreground px-1 py-2">
      No pages match this filter.
    </div>

    <ImageItem
      v-for="page in filteredPages"
      :key="page.id"
      :page="page"
      :current-page-id="currentPageId"
      :preview-url="getPreviewUrl(page)"
      :variant-items="variantItemsByPageId[page.id] ?? []"
      :selected-variant="getDisplayedVariant(page)?.id ?? null"
      :open-subtask-count="openSubtaskCountByPage?.[page.id] ?? 0"
      @select-page="handlePageClick"
      @variant-change="(id) => handleVariantChange(page, id)"
      @unload-page="handlePageUnload"
    />
  </div>
</template>
