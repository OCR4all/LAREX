<script setup lang="ts">
import type { PageDto } from '@/types/page-dto'
import type { PageXmlVersion } from '@/types/version'
import { convertPageDtoToPcGts } from '@/services/editor/page-conversion.service'
import { getEditorSession } from '@/session/editor/editor-session'
import {
  comparePageVersions,
  groupPageVersionChanges,
  textDiffSegments,
  type PageVersionCompareChange,
  type PageVersionCompareChangeType,
  type PageVersionCompareElementKind,
  type PageVersionCompareSummary
} from '@/utils/editor/page-version-compare'
import type { PageComparisonDiffHighlight } from '@/stores/editor/types'
import { useOverlayDialogs } from '@/composables/editor/use-overlay-dialogs'

const props = defineProps<{
  pageLabel: string
  annotationBasePath: string
  xmlId: string
  currentCanvasId: string
  versionCanvasId: string
  currentPage: PageDto
  initialVersionPage: PageDto
  initialVersion: PageXmlVersion
  canRestore: boolean
  hasUnsavedChanges: boolean
  gtIndex?: number
}>()

const emit = defineEmits<{
  close: [result: 'closed' | 'restored']
}>()

type FilterKey = 'all' | 'geometry' | 'text' | 'metadata'
type CompareViewMode = 'default' | 'textline' | 'baseline'

const editorStore = useEditorStore()
const toast = useToast()
const { confirm } = useOverlayDialogs()
const isOpen = ref(true)
const comparedPage = shallowRef<PageDto>(props.initialVersionPage)
const selectedVersionId = ref(props.initialVersion.id)
const selectedVersion = ref<PageXmlVersion>(props.initialVersion)
const loadingVersion = ref(false)
const restoring = ref(false)
const linkedViews = ref(true)
const filter = ref<FilterKey>('all')
const compareViewMode = ref<CompareViewMode>('default')
let closeEmitted = false
let syncingSelection = false

const { data: versions } = useFetch<PageXmlVersion[]>(
  () => `${props.annotationBasePath}/${props.xmlId}/versions`,
  {
    lazy: true,
    default: () => [props.initialVersion]
  }
)

const versionOptions = computed(() => {
  const byId = new Map<string, PageXmlVersion>()
  byId.set(props.initialVersion.id, props.initialVersion)
  for (const version of versions.value ?? []) {
    byId.set(version.id, version)
  }
  return [...byId.values()].sort((left, right) => right.versionNumber - left.versionNumber)
})

const summary = computed(() =>
  comparePageVersions(props.currentPage, comparedPage.value, { gtIndex: props.gtIndex })
)

const filteredChanges = computed(() => {
  const changes = summary.value.changes
  if (filter.value === 'all') return changes
  if (filter.value === 'geometry') return changes.filter(change => change.changeType === 'geometry')
  if (filter.value === 'text') return changes.filter(change => change.changeType === 'text')
  return changes.filter(change => change.changeType === 'metadata')
})

const changeGroups = computed(() => groupPageVersionChanges(summary.value.changes))
const filteredChangeGroups = computed(() =>
  groupPageVersionChanges(filteredChanges.value).map(group => ({
    ...group,
    textSegments: group.changeTypes.includes('text')
      ? textDiffSegments(group.current?.text || '', group.compared?.text || '')
      : []
  }))
)
const countTotal = computed(() => changeGroups.value.length)
const textChangeCount = computed(() => summary.value.counts.textline.text)

const filterItems: Array<{ key: FilterKey, label: string }> = [
  { key: 'all', label: 'All' },
  { key: 'geometry', label: 'Geometry' },
  { key: 'text', label: 'Text' },
  { key: 'metadata', label: 'Metadata' }
]
const viewModeItems: Array<{ key: CompareViewMode, label: string, icon: string }> = [
  { key: 'default', label: 'Regions', icon: 'i-lucide-panel-top' },
  { key: 'textline', label: 'Lines', icon: 'i-lucide-text-cursor-input' },
  { key: 'baseline', label: 'Baselines', icon: 'i-lucide-baseline' }
]

function controlsForCanvas(canvasId: string) {
  return getEditorSession(canvasId)?.controls.value ?? null
}

const currentControls = computed(() => controlsForCanvas(props.currentCanvasId))
const versionControls = computed(() => controlsForCanvas(props.versionCanvasId))

function applyViewMode(mode: CompareViewMode) {
  controlsForCanvas(props.currentCanvasId)?.setViewMode?.(mode, { persistAsLayoutPreference: false })
  controlsForCanvas(props.versionCanvasId)?.setViewMode?.(mode, { persistAsLayoutPreference: false })
}

function resetView(canvasId: string) {
  controlsForCanvas(canvasId)?.resetView?.()
}

function copyCanvasView(sourceCanvasId: string, targetCanvasId: string) {
  if (!linkedViews.value) return
  const sourceView = controlsForCanvas(sourceCanvasId)?.view
  const targetControls = controlsForCanvas(targetCanvasId)
  const targetView = targetControls?.view
  if (!sourceView || !targetControls?.setView || !targetView) return
  if (
    sourceView.zoom === targetView.zoom
    && sourceView.offsetX === targetView.offsetX
    && sourceView.offsetY === targetView.offsetY
  ) {
    return
  }
  targetControls.setView({
    zoom: sourceView.zoom,
    offsetX: sourceView.offsetX,
    offsetY: sourceView.offsetY
  })
}

function syncCanvasSelection(sourceCanvasId: string, targetCanvasId: string) {
  if (!linkedViews.value || syncingSelection) return
  const sourceCanvas = editorStore.canvases[sourceCanvasId]
  const targetCanvas = editorStore.canvases[targetCanvasId]
  const targetControls = controlsForCanvas(targetCanvasId)
  if (!sourceCanvas || !targetCanvas || !targetControls) return

  const regionId = sourceCanvas.selectedRegionId
  const baselineId = sourceCanvas.selectedBaselineId
  if (
    targetCanvas.selectedRegionId === regionId
    && targetCanvas.selectedBaselineId === baselineId
  ) {
    return
  }

  syncingSelection = true
  if (baselineId && targetControls.polylines?.some(polyline => polyline.id === baselineId)) {
    targetControls.selectPolylineById?.(baselineId, { focusMode: 'none' })
  } else if (regionId && targetControls.polygons?.some(polygon => polygon.id === regionId)) {
    targetControls.selectPolygonById?.(regionId, { focusMode: 'none' })
  } else {
    targetControls.selectPolygonById?.(null, { focusMode: 'none' })
  }
  void nextTick(() => {
    syncingSelection = false
  })
}

watch(compareViewMode, (mode) => {
  applyViewMode(mode)
})

watch(
  () => {
    const view = currentControls.value?.view
    return [view?.zoom, view?.offsetX, view?.offsetY] as const
  },
  () => copyCanvasView(props.currentCanvasId, props.versionCanvasId)
)

watch(
  () => {
    const view = versionControls.value?.view
    return [view?.zoom, view?.offsetX, view?.offsetY] as const
  },
  () => copyCanvasView(props.versionCanvasId, props.currentCanvasId)
)

watch(
  () => [
    editorStore.canvases[props.currentCanvasId]?.selectedRegionId,
    editorStore.canvases[props.currentCanvasId]?.selectedBaselineId,
    versionControls.value
  ] as const,
  () => syncCanvasSelection(props.currentCanvasId, props.versionCanvasId)
)

watch(
  () => [
    editorStore.canvases[props.versionCanvasId]?.selectedRegionId,
    editorStore.canvases[props.versionCanvasId]?.selectedBaselineId,
    currentControls.value
  ] as const,
  () => syncCanvasSelection(props.versionCanvasId, props.currentCanvasId)
)

watch(linkedViews, (linked) => {
  if (!linked) return
  void nextTick(() => {
    copyCanvasView(props.currentCanvasId, props.versionCanvasId)
    syncCanvasSelection(props.currentCanvasId, props.versionCanvasId)
  })
})

onMounted(() => {
  void nextTick(() => {
    applyViewMode(compareViewMode.value)
    copyCanvasView(props.currentCanvasId, props.versionCanvasId)
  })
})

function highlightToneForChange(change: PageVersionCompareChange, side: 'current' | 'version'): PageComparisonDiffHighlight | null {
  if (side === 'current' && change.changeType === 'added' && change.current) {
    return { tone: 'added', kind: change.kind }
  }
  if (side === 'version' && change.changeType === 'removed' && change.compared) {
    return { tone: 'removed', kind: change.kind }
  }
  if (change.changeType === 'geometry' || change.changeType === 'text' || change.changeType === 'metadata') {
    return { tone: 'changed', kind: change.kind }
  }
  return null
}

function buildHighlights(side: 'current' | 'version', compareSummary: PageVersionCompareSummary): Record<string, PageComparisonDiffHighlight> {
  const highlights: Record<string, PageComparisonDiffHighlight> = {}
  for (const change of compareSummary.changes) {
    const element = side === 'current' ? change.current : change.compared
    if (!element) continue
    const highlight = highlightToneForChange(change, side)
    if (highlight) {
      highlights[element.id] = highlight
    }
  }
  return highlights
}

function applyDiffHighlights() {
  const currentCanvas = editorStore.canvases[props.currentCanvasId]
  const versionCanvas = editorStore.canvases[props.versionCanvasId]
  if (currentCanvas) {
    currentCanvas.diffHighlights = buildHighlights('current', summary.value)
  }
  if (versionCanvas) {
    versionCanvas.diffHighlights = buildHighlights('version', summary.value)
  }
}

watch(summary, applyDiffHighlights, { immediate: true })

async function loadSelectedVersion(versionId: string) {
  const nextVersion = versionOptions.value.find(version => version.id === versionId)
  if (!nextVersion) return

  loadingVersion.value = true
  try {
    const pageDto = await $fetch<PageDto>(`${props.annotationBasePath}/${props.xmlId}/versions/${nextVersion.id}/annotation`)
    comparedPage.value = pageDto
    selectedVersion.value = nextVersion
    editorStore.setCanvasDocument(props.versionCanvasId, convertPageDtoToPcGts(pageDto))

    const versionCanvas = editorStore.canvases[props.versionCanvasId]
    if (versionCanvas?.comparison) {
      versionCanvas.comparison.version = nextVersion
    }
  } catch (error: unknown) {
    toast.add({
      title: 'Version load failed',
      description: error instanceof Error ? error.message : 'Failed to load the selected version.',
      color: 'error'
    })
    selectedVersionId.value = selectedVersion.value.id
  } finally {
    loadingVersion.value = false
  }
}

watch(selectedVersionId, (versionId) => {
  if (versionId !== selectedVersion.value.id) {
    void loadSelectedVersion(versionId)
  }
})

function focusChangeGroup(group: ReturnType<typeof groupPageVersionChanges>[number]) {
  if (group.current) {
    focusCanvasElement(props.currentCanvasId, group.kind, group.current.id, 'context')
    focusCanvasElement(props.versionCanvasId, group.kind, group.compared?.id, 'none')
    copyCanvasView(props.currentCanvasId, props.versionCanvasId)
  } else if (group.compared) {
    focusCanvasElement(props.versionCanvasId, group.kind, group.compared.id, 'context')
    copyCanvasView(props.versionCanvasId, props.currentCanvasId)
  }
}

function focusCanvasElement(
  canvasId: string,
  kind: PageVersionCompareElementKind,
  elementId: string | undefined,
  focusMode: 'context' | 'none'
) {
  if (!elementId) return
  const controls = getEditorSession(canvasId)?.controls.value
  if (!controls) return
  if (kind === 'baseline') {
    controls.selectPolylineById?.(elementId, { focusMode })
  } else {
    controls.selectPolygonById?.(elementId, { focusMode })
  }
}

function changeToneClass(changeTypes: PageVersionCompareChangeType[]): string {
  if (changeTypes.includes('added')) return 'border-lime-400 bg-lime-400/10'
  if (changeTypes.includes('removed')) return 'border-red-400 bg-red-400/10'
  return 'border-amber-400 bg-amber-400/10'
}

function changeLabel(changeType: PageVersionCompareChangeType): string {
  if (changeType === 'added') return 'Added'
  if (changeType === 'removed') return 'Removed'
  if (changeType === 'geometry') return 'Geometry'
  if (changeType === 'text') return 'Text'
  return 'Metadata'
}

function countByType(type: PageVersionCompareChangeType): number {
  return Object.values(summary.value.counts).reduce((total, counts) => total + counts[type], 0)
}

async function restoreSelectedVersion() {
  if (!props.canRestore || restoring.value || loadingVersion.value) return

  const version = selectedVersion.value
  const message = props.hasUnsavedChanges
    ? 'Unsaved changes in the current editor will be discarded. The currently saved XML will be preserved as a new version first.'
    : 'The currently saved XML will be preserved as a new version first.'
  const confirmed = await confirm({
    title: `Restore version ${version.versionNumber}?`,
    message,
    confirmLabel: `Restore v${version.versionNumber}`,
    confirmColor: 'warning',
    confirmIcon: 'i-lucide-history'
  })
  if (!confirmed) return

  restoring.value = true
  try {
    await $fetch(
      `${props.annotationBasePath}/${props.xmlId}/versions/${version.id}/restore`,
      { method: 'POST' }
    )
    toast.add({
      title: 'Version restored',
      description: `Restored to version ${version.versionNumber}`,
      color: 'success',
      icon: 'i-lucide-check'
    })
    finishClose('restored')
  } catch (error: unknown) {
    toast.add({
      title: 'Restore failed',
      description: error instanceof Error ? error.message : 'Failed to restore version.',
      color: 'error',
      icon: 'i-lucide-alert-circle'
    })
  } finally {
    restoring.value = false
  }
}

function finishClose(result: 'closed' | 'restored') {
  if (closeEmitted) return
  closeEmitted = true
  emit('close', result)
  if (isOpen.value) {
    isOpen.value = false
  }
}

function close() {
  if (restoring.value) return
  finishClose('closed')
}
</script>

<template>
  <UModal
    v-model:open="isOpen"
    title="Compare PAGE Versions"
    :dismissible="!restoring"
    :ui="{
      content: 'sm:max-w-[min(98vw,120rem)]',
      body: 'p-0 sm:p-0'
    }"
    @close="close"
  >
    <template #body>
      <div class="flex h-[min(88vh,72rem)] flex-col bg-default">
        <div class="flex shrink-0 flex-wrap items-center gap-3 border-b border-default px-4 py-3">
          <div class="min-w-0">
            <div class="text-xs font-medium uppercase text-muted">
              Page
            </div>
            <div class="truncate text-sm font-semibold text-highlighted">
              {{ pageLabel }}
            </div>
          </div>

          <div class="flex items-center gap-2">
            <label class="text-xs font-medium uppercase text-muted" for="compare-version-select">Version</label>
            <select
              id="compare-version-select"
              v-model="selectedVersionId"
              class="h-8 rounded-sm border border-default bg-default px-2 text-sm text-highlighted outline-none focus:border-primary"
              :disabled="loadingVersion || restoring"
            >
              <option
                v-for="version in versionOptions"
                :key="version.id"
                :value="version.id"
              >
                v{{ version.versionNumber }} - {{ version.comment || version.created }}
              </option>
            </select>
            <UButton
              v-if="canRestore"
              icon="i-lucide-history"
              color="warning"
              variant="soft"
              size="sm"
              :label="`Restore v${selectedVersion.versionNumber}`"
              :loading="restoring"
              :disabled="loadingVersion || restoring"
              @click="restoreSelectedVersion"
            />
          </div>

          <div class="ml-auto flex items-center gap-2">
            <UTooltip
              :text="linkedViews ? 'Unlink canvas navigation' : 'Link canvas navigation'"
              :delay-duration="0"
            >
              <UButton
                :icon="linkedViews ? 'i-lucide-link' : 'i-lucide-unlink'"
                color="neutral"
                :variant="linkedViews ? 'soft' : 'ghost'"
                size="sm"
                :aria-label="linkedViews ? 'Unlink canvas navigation' : 'Link canvas navigation'"
                @click="() => { linkedViews = !linkedViews }"
              />
            </UTooltip>
            <div class="flex rounded-sm border border-default bg-default p-0.5">
              <UTooltip
                v-for="item in viewModeItems"
                :key="item.key"
                :text="item.label"
                :delay-duration="0"
              >
                <button
                  type="button"
                  :class="[
                    'flex h-7 w-8 items-center justify-center rounded-[2px]',
                    compareViewMode === item.key ? 'bg-accented text-highlighted' : 'text-muted hover:text-highlighted'
                  ]"
                  @click="compareViewMode = item.key"
                >
                  <Icon :name="item.icon" class="h-4 w-4" />
                </button>
              </UTooltip>
            </div>
            <UBadge color="neutral" variant="subtle">
              {{ countTotal }} changed {{ countTotal === 1 ? 'element' : 'elements' }}
            </UBadge>
            <UBadge color="warning" variant="subtle">
              {{ textChangeCount }} text
            </UBadge>
            <UButton
              icon="i-lucide-x"
              color="neutral"
              variant="ghost"
              size="sm"
              :disabled="restoring"
              @click="close"
            />
          </div>
        </div>

        <div class="grid min-h-0 flex-1 grid-cols-[minmax(0,1fr)_22rem]">
          <div class="grid min-h-0 grid-cols-2">
            <section class="flex min-h-0 flex-col border-r border-default">
              <div class="flex h-9 shrink-0 items-center justify-between border-b border-default px-3">
                <span class="text-sm font-medium text-highlighted">Current snapshot</span>
                <UButton
                  icon="i-lucide-scan"
                  color="neutral"
                  variant="ghost"
                  size="xs"
                  @click="resetView(currentCanvasId)"
                />
              </div>
              <Editor
                class="min-h-0 flex-1"
                :src="editorStore.canvases[currentCanvasId]?.imageSrc || ''"
                :canvas-id="currentCanvasId"
              />
            </section>

            <section class="flex min-h-0 flex-col">
              <div class="flex h-9 shrink-0 items-center justify-between border-b border-default px-3">
                <span class="text-sm font-medium text-highlighted">Version v{{ selectedVersion.versionNumber }}</span>
                <UButton
                  icon="i-lucide-scan"
                  color="neutral"
                  variant="ghost"
                  size="xs"
                  @click="resetView(versionCanvasId)"
                />
              </div>
              <Editor
                class="min-h-0 flex-1"
                :src="editorStore.canvases[versionCanvasId]?.imageSrc || ''"
                :canvas-id="versionCanvasId"
              />
            </section>
          </div>

          <aside class="flex min-h-0 flex-col border-l border-default bg-elevated/30">
            <div class="shrink-0 border-b border-default p-3">
              <div class="grid grid-cols-2 gap-2 text-xs">
                <div class="rounded-sm border border-default bg-default p-2">
                  <div class="text-muted">
                    Added
                  </div>
                  <div class="text-lg font-semibold text-lime-500">
                    {{ countByType('added') }}
                  </div>
                </div>
                <div class="rounded-sm border border-default bg-default p-2">
                  <div class="text-muted">
                    Removed
                  </div>
                  <div class="text-lg font-semibold text-red-500">
                    {{ countByType('removed') }}
                  </div>
                </div>
                <div class="rounded-sm border border-default bg-default p-2">
                  <div class="text-muted">
                    Geometry
                  </div>
                  <div class="text-lg font-semibold text-amber-500">
                    {{ countByType('geometry') }}
                  </div>
                </div>
                <div class="rounded-sm border border-default bg-default p-2">
                  <div class="text-muted">
                    Metadata
                  </div>
                  <div class="text-lg font-semibold text-amber-500">
                    {{ countByType('metadata') }}
                  </div>
                </div>
              </div>

              <div class="mt-3 flex rounded-sm border border-default bg-default p-0.5">
                <button
                  v-for="item in filterItems"
                  :key="item.key"
                  type="button"
                  :class="[
                    'h-7 flex-1 rounded-[2px] px-2 text-xs font-medium',
                    filter === item.key ? 'bg-accented text-highlighted' : 'text-muted hover:text-highlighted'
                  ]"
                  @click="filter = item.key"
                >
                  {{ item.label }}
                </button>
              </div>
            </div>

            <div class="min-h-0 flex-1 overflow-auto p-3">
              <UEmpty
                v-if="filteredChangeGroups.length === 0"
                icon="i-lucide-git-compare"
                title="No changes"
                description="No changed elements match the selected filter."
              />

              <div v-else class="space-y-2">
                <button
                  v-for="group in filteredChangeGroups"
                  :key="group.key"
                  type="button"
                  :class="[
                    'w-full rounded-sm border border-l-4 p-2 text-left transition hover:bg-elevated',
                    changeToneClass(group.changeTypes)
                  ]"
                  @click="focusChangeGroup(group)"
                >
                  <div class="flex items-center justify-between gap-2">
                    <span class="truncate text-sm font-medium text-highlighted">
                      {{ group.current?.label || group.compared?.label || group.id }}
                    </span>
                    <div class="flex shrink-0 items-center gap-1">
                      <UBadge
                        v-for="changeType in group.changeTypes"
                        :key="changeType"
                        size="xs"
                        color="neutral"
                        variant="subtle"
                      >
                        {{ changeLabel(changeType) }}
                      </UBadge>
                    </div>
                  </div>
                  <div class="mt-1 text-xs text-muted">
                    {{ group.kind }} · {{ group.id }}
                  </div>

                  <div v-if="group.textSegments.length" class="mt-2 rounded-sm bg-default p-2 text-xs leading-5">
                    <span
                      v-for="(segment, index) in group.textSegments"
                      :key="index"
                      :class="[
                        segment.status === 'current' ? 'bg-lime-400/30 text-lime-700 dark:text-lime-200' : '',
                        segment.status === 'compared' ? 'bg-red-400/30 text-red-700 line-through dark:text-red-200' : ''
                      ]"
                    >{{ segment.value }}</span>
                  </div>
                </button>
              </div>
            </div>
          </aside>
        </div>
      </div>
    </template>
  </UModal>
</template>
