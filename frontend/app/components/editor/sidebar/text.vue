<script setup lang="ts">
import { getEditorSession } from '@/session/editor/editor-session'
import EditorSidebarMetadata from '@/components/editor/sidebar/metadata.vue'
import EditorSidebarTasks from '@/components/editor/sidebar/tasks.vue'
import { useEditorSessionStore } from '@/stores/editor/editor.session.store'
import type { PcGts } from '@/models/editor/document'
import type { Page } from '@/models/editor/page'
import type { Region } from '@/models/editor/region'
import type { TextLine } from '@/models/editor/text'
import type { LinkedTask, Subtask } from '~/types/index'
import { PolygonType } from '@/models/editor'
import type { RenderablePolygon, RenderablePolyline } from '@/types/editor/rendering'
import type { MetadataApplyPayload } from '@/types/editor/metadata'

const props = defineProps<{
  collapsed?: boolean
  canvasId?: string | null
  document?: PcGts | null
  page?: Page | null
  selectedElement?: Region | TextLine | RenderablePolyline | null
  openTasks?: Subtask[]
  taskById?: Record<string, LinkedTask>
  isPageLocked?: boolean
  isTasksLoading?: boolean
  onCompleteTask?: (subtask: Subtask) => void
}>()

const emit = defineEmits<{
  'apply-metadata': [payload: MetadataApplyPayload]
}>()

const editorStore = useEditorStore()
const uiStore = useEditorUiStore()
const sessionStore = useEditorSessionStore()
const workspaceStore = useWorkspaceStore()

const effectiveCanvasId = computed(() => props.canvasId ?? editorStore.activeCanvasId)
const textViewSettings = computed(() => sessionStore.textViewSettings)
const textElementType = computed(() => PolygonType.TEXTLINE)

const paddingModel = computed({
  get: () => uiStore.textViewPadding,
  set: (next) => {
    uiStore.setTextViewPadding(Number(next))
  }
})

const fontSizeModel = computed({
  get: () => uiStore.textViewFontSize,
  set: next => uiStore.setTextViewFontSize(Number(next))
})

const textItemLayoutModel = computed({
  get: () => uiStore.textItemLayout,
  set: next => uiStore.setTextItemLayout(next)
})

const showCommentsModel = computed({
  get: () => textViewSettings.value?.showComments ?? false,
  set: (next: boolean) => {
    sessionStore.updateTextViewSettings(current => ({ ...current, showComments: Boolean(next) }))
  }
})

const highlightUnknownCodecCharsModel = computed({
  get: () => uiStore.highlightUnknownCodecChars,
  set: next => uiStore.setHighlightUnknownCodecChars(Boolean(next))
})
const includeWhitespaceInCodecHighlightModel = computed({
  get: () => uiStore.includeWhitespaceInCodecHighlight,
  set: next => uiStore.setIncludeWhitespaceInCodecHighlight(Boolean(next))
})
const hasProjectCodec = computed(() => {
  return Boolean(editorStore.projectCodecId) || (editorStore.projectCodecCharacters?.length ?? 0) > 0
})
const highlightUnknownDictionaryTokensModel = computed({
  get: () => uiStore.highlightUnknownDictionaryTokens,
  set: next => uiStore.setHighlightUnknownDictionaryTokens(Boolean(next))
})
const hasProjectDictionary = computed(() => {
  return Boolean(editorStore.projectDictionaryId)
})

const defaultGtIndexModel = computed({
  get: () => editorStore.projectTextDefaultGtIndex ?? 0,
  set: (next: number) => {
    const parsed = Number.parseInt(String(next), 10)
    editorStore.setProjectTextIndexDefaults({
      gtIndex: Number.isFinite(parsed) && parsed >= 0 ? parsed : 0,
      recognitionIndices: editorStore.projectTextDefaultRecognitionIndices ?? [1]
    })
  }
})

const defaultRecognitionIndicesModel = computed({
  get: () => editorStore.projectTextDefaultRecognitionIndices ?? [1],
  set: (next: number[]) => {
    editorStore.setProjectTextIndexDefaults({
      gtIndex: editorStore.projectTextDefaultGtIndex ?? 0,
      recognitionIndices: next
    })
  }
})

const showDiffModel = computed({
  get: () => textViewSettings.value?.showDiff ?? false,
  set: (next) => {
    sessionStore.updateTextViewSettings(current => ({ ...current, showDiff: Boolean(next) }))
  }
})

const confidenceRangeModel = computed({
  get: () => textViewSettings.value?.confidenceRange ?? [0, 1],
  set: (next) => {
    const min = Math.max(0, Math.min(1, Number(next?.[0] ?? 0)))
    const max = Math.max(0, Math.min(1, Number(next?.[1] ?? 1)))
    sessionStore.updateTextViewSettings(current => ({ ...current, confidenceRange: min <= max ? [min, max] : [max, min] }))
  }
})

const selectedIndicesModel = computed({
  get: () => textViewSettings.value?.selectedIndices ?? [],
  set: (next) => {
    const parsed = (Array.isArray(next) ? next : [])
      .map(v => Number.parseInt(String(v), 10))
      .filter(v => Number.isFinite(v) && v >= 0)
    sessionStore.updateTextViewSettings(current => ({ ...current, selectedIndices: [...new Set(parsed)].sort((a, b) => a - b) }))
  }
})

const filterUnindexedModel = computed({
  get: () => textViewSettings.value?.filterUnindexed ?? false,
  set: (next: boolean) => {
    sessionStore.updateTextViewSettings(current => ({ ...current, filterUnindexed: Boolean(next) }))
  }
})

const showNonAssignedIndicesModel = computed({
  get: () => textViewSettings.value?.showNonAssignedIndices ?? false,
  set: (next: boolean) => {
    sessionStore.updateTextViewSettings(current => ({ ...current, showNonAssignedIndices: Boolean(next) }))
  }
})

const activeProjectId = computed(() => sessionStore.projectId ?? null)
const canEditProjectTextIndexDefaults = computed(() => workspaceStore.isCurrentUserOwner)
const isSavingTextIndexDefaults = ref(false)
const textIndexDefaultsSaveError = ref<string | null>(null)
const { refreshProjectCaches } = useDataRefresh()

async function saveProjectTextIndexDefaults(payload: { defaultGtIndex: number, defaultRecognitionIndices: number[] }) {
  const workspaceId = workspaceStore.selectedWorkspaceId
  const projectId = activeProjectId.value
  if (!workspaceId || !projectId) return

  textIndexDefaultsSaveError.value = null
  isSavingTextIndexDefaults.value = true
  try {
    const project = await $fetch<any>(`/api/workspaces/${workspaceId}/projects/${projectId}`)
    const updated = await $fetch<any>(`/api/workspaces/${workspaceId}/projects/${projectId}`, {
      method: 'PUT',
      body: {
        name: project.name,
        description: project.description ?? null,
        tags: project.tags ?? [],
        codecId: project.codecId ?? null,
        labelSetId: project.labelSetId ?? null,
        tagSetId: project.tagSetId ?? null,
        defaultGtIndex: payload.defaultGtIndex,
        defaultRecognitionIndices: payload.defaultRecognitionIndices
      }
    })
    editorStore.setProjectTextIndexDefaults({
      gtIndex: updated.defaultGtIndex ?? payload.defaultGtIndex,
      recognitionIndices: updated.defaultRecognitionIndices ?? payload.defaultRecognitionIndices
    }, projectId)
    await refreshProjectCaches(workspaceId, projectId)
  } catch (error: any) {
    textIndexDefaultsSaveError.value = error?.data?.message || error?.message || 'Failed to save defaults'
  } finally {
    isSavingTextIndexDefaults.value = false
  }
}

function getRenderablePolygonsForCanvas(canvasId: string): RenderablePolygon[] {
  const session = getEditorSession(canvasId)
  const controls = session?.controls.value as { polygons?: RenderablePolygon[] } | null
  return controls?.polygons ?? editorStore.regionsByCanvasId(canvasId)
}

const availableIndices = computed(() => {
  const canvasId = effectiveCanvasId.value
  if (!canvasId) return []
  const regions = getRenderablePolygonsForCanvas(canvasId)
  const indices = new Set<number>()

  for (const r of regions) {
    if (r.type !== textElementType.value) continue
    for (const te of r.textContentVariants ?? []) {
      if (typeof te.index === 'number' && Number.isFinite(te.index) && te.index >= 0) {
        indices.add(te.index)
      }
    }
  }
  return [...indices].sort((a, b) => a - b)
})

const hasUnindexed = computed(() => {
  const canvasId = effectiveCanvasId.value
  if (!canvasId) return false
  const regions = getRenderablePolygonsForCanvas(canvasId)

  for (const r of regions) {
    if (r.type !== textElementType.value) continue
    for (const te of r.textContentVariants ?? []) {
      if (typeof te.index !== 'number' || !Number.isFinite(te.index) || te.index < 0) {
        return true
      }
    }
  }
  return false
})

const openTaskCount = computed(() => props.openTasks?.length ?? 0)

const accordionItems = [
  { label: 'Metadata', icon: 'i-lucide-badge-info', slot: 'metadata' },
  { label: 'Tasks', icon: 'i-lucide-check-square', slot: 'tasks' },
  { label: 'Settings', icon: 'i-lucide-settings', slot: 'settings' },
  { label: 'Virtual Keyboard', icon: 'i-lucide-keyboard', slot: 'virtualKeyboard' },
  { label: 'Codec', icon: 'i-lucide-badge-check', slot: 'codec' },
  { label: 'Dictionary', icon: 'i-lucide-book-copy', slot: 'dictionary' },
  { label: 'Diff', icon: 'i-lucide-git-compare', slot: 'diff' },
  { label: 'Filter', icon: 'i-lucide-filter', slot: 'filter' }
]

const accordionModel = ref<string[]>(['metadata'])
const allPanels = accordionItems.map(item => item.slot)

function expandAllPanelsForOnboarding() {
  accordionModel.value = [...allPanels]
}

function handleMetadataApply(payload: MetadataApplyPayload) {
  emit('apply-metadata', payload)
}

onMounted(() => {
  window.addEventListener('larex:onboarding:expand-text-panels', expandAllPanelsForOnboarding as EventListener)
})

onBeforeUnmount(() => {
  window.removeEventListener('larex:onboarding:expand-text-panels', expandAllPanelsForOnboarding as EventListener)
})
</script>

<template>
  <div data-tour="editor-text-sidebar" class="h-full flex flex-col bg-elevated/25">
    <div v-if="collapsed" class="flex flex-col items-center gap-1 py-1">
      <UPopover v-for="item in accordionItems" :key="item.slot" :content="{ side: 'left', align: 'start', sideOffset: 12 }">
        <UTooltip :text="item.label" :content="{ side: 'left' }">
          <UChip
            :show="item.slot === 'tasks' && openTaskCount > 0"
            :text="openTaskCount"
            position="top-right"
            :color="openTaskCount > 0 ? 'warning' : 'neutral'"
            class="z-200"
          >
            <UButton
              variant="ghost"
              color="neutral"
              size="sm"
              :icon="item.icon"
              :aria-label="item.label"
            />
          </UChip>
        </UTooltip>
        <template #content>
          <div class="w-80 max-h-[70vh] overflow-auto">
            <div class="px-3 py-2 border-b border-default">
              <span class="text-sm font-semibold">{{ item.label }}</span>
            </div>
            <template v-if="item.slot === 'metadata'">
              <EditorSidebarMetadata
                :document="document"
                :page="page"
                :selected-element="selectedElement"
                @apply="handleMetadataApply"
              />
            </template>
            <template v-else-if="item.slot === 'tasks'">
              <div class="p-3">
                <EditorSidebarTasks
                  :open-tasks="openTasks ?? []"
                  :task-by-id="taskById ?? {}"
                  :is-page-locked="isPageLocked ?? false"
                  :is-loading="isTasksLoading ?? false"
                  :on-complete-subtask="onCompleteTask ?? (() => {})"
                />
              </div>
            </template>
            <template v-else-if="item.slot === 'settings'">
              <EditorSidebarTextSettingsPanel
                v-model:padding="paddingModel"
                v-model:font-size="fontSizeModel"
                v-model:text-item-layout="textItemLayoutModel"
                v-model:show-comments="showCommentsModel"
              />
            </template>
            <template v-else-if="item.slot === 'virtualKeyboard'">
              <EditorSidebarTextVirtualKeyboardPanel />
            </template>
            <template v-else-if="item.slot === 'codec'">
              <EditorSidebarCodecSettingsPanel
                v-model:highlight-unknown-codec-chars="highlightUnknownCodecCharsModel"
                v-model:include-whitespace-in-codec-highlight="includeWhitespaceInCodecHighlightModel"
                :has-project-codec="hasProjectCodec"
              />
            </template>
            <template v-else-if="item.slot === 'dictionary'">
              <EditorSidebarDictionarySettingsPanel
                v-model:highlight-unknown-dictionary-tokens="highlightUnknownDictionaryTokensModel"
                :has-project-dictionary="hasProjectDictionary"
              />
            </template>
            <template v-else-if="item.slot === 'diff'">
              <EditorSidebarTextDiffPanel
                v-model:default-gt-index="defaultGtIndexModel"
                v-model:default-recognition-indices="defaultRecognitionIndicesModel"
                v-model:show-diff="showDiffModel"
                :can-edit-defaults="canEditProjectTextIndexDefaults"
                :is-saving-defaults="isSavingTextIndexDefaults"
                :save-error="textIndexDefaultsSaveError"
                @save-defaults="saveProjectTextIndexDefaults"
              />
            </template>
            <template v-else-if="item.slot === 'filter'">
              <EditorSidebarTextFilterPanel
                v-model:selected-indices="selectedIndicesModel"
                v-model:filter-unindexed="filterUnindexedModel"
                v-model:show-non-assigned-indices="showNonAssignedIndicesModel"
                v-model:confidence-range="confidenceRangeModel"
                :available-indices="availableIndices"
                :has-unindexed="hasUnindexed"
              />
            </template>
          </div>
        </template>
      </UPopover>
    </div>

    <UAccordion
      v-else
      v-model="accordionModel"
      type="multiple"
      :ui="{ root: 'bg-elevated/25' }"
      :items="accordionItems"
    >
      <template #leading="{ item }">
        <UChip
          :show="item.slot === 'tasks' && openTaskCount > 0"
          :text="openTaskCount"
          size="md"
          color="warning"
        >
          <Icon class="size-5" :name="item.icon" />
        </UChip>
      </template>

      <template #metadata>
        <EditorSidebarMetadata
          :document="document"
          :page="page"
          :selected-element="selectedElement"
          @apply="handleMetadataApply"
        />
      </template>

      <template #tasks>
        <div class="p-3">
          <EditorSidebarTasks
            :open-tasks="openTasks ?? []"
            :task-by-id="taskById ?? {}"
            :is-page-locked="isPageLocked ?? false"
            :is-loading="isTasksLoading ?? false"
            :on-complete-subtask="onCompleteTask ?? (() => {})"
          />
        </div>
      </template>

      <template #settings>
        <div data-tour="editor-text-settings-panel">
          <EditorSidebarTextSettingsPanel
            v-model:padding="paddingModel"
            v-model:font-size="fontSizeModel"
            v-model:text-item-layout="textItemLayoutModel"
            v-model:show-comments="showCommentsModel"
          />
        </div>
      </template>

      <template #virtualKeyboard>
        <EditorSidebarTextVirtualKeyboardPanel />
      </template>

      <template #codec>
        <EditorSidebarCodecSettingsPanel
          v-model:highlight-unknown-codec-chars="highlightUnknownCodecCharsModel"
          v-model:include-whitespace-in-codec-highlight="includeWhitespaceInCodecHighlightModel"
          :has-project-codec="hasProjectCodec"
        />
      </template>

      <template #dictionary>
        <EditorSidebarDictionarySettingsPanel
          v-model:highlight-unknown-dictionary-tokens="highlightUnknownDictionaryTokensModel"
          :has-project-dictionary="hasProjectDictionary"
        />
      </template>

      <template #diff>
        <EditorSidebarTextDiffPanel
          v-model:default-gt-index="defaultGtIndexModel"
          v-model:default-recognition-indices="defaultRecognitionIndicesModel"
          v-model:show-diff="showDiffModel"
          :can-edit-defaults="canEditProjectTextIndexDefaults"
          :is-saving-defaults="isSavingTextIndexDefaults"
          :save-error="textIndexDefaultsSaveError"
          @save-defaults="saveProjectTextIndexDefaults"
        />
      </template>

      <template #filter>
        <div data-tour="editor-text-filter-panel">
          <EditorSidebarTextFilterPanel
            v-model:selected-indices="selectedIndicesModel"
            v-model:filter-unindexed="filterUnindexedModel"
            v-model:show-non-assigned-indices="showNonAssignedIndicesModel"
            v-model:confidence-range="confidenceRangeModel"
            :available-indices="availableIndices"
            :has-unindexed="hasUnindexed"
          />
        </div>
      </template>
    </UAccordion>
  </div>
</template>
