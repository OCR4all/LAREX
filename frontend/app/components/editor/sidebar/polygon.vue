<script setup lang="ts">
import { LazyUiConfirmModal } from '#components'
import { PolygonType, type ReadingOrder } from '@/models/editor'
import type { Region, TextRegion } from '@/models/editor/region'
import type { AvailableItem } from '@/components/editor/reading-order'
import type { TreeItemData } from '@/components/editor/sidebar/tree-item.vue'
import type { RenderablePolyline } from '@/types/editor/rendering'
import type { Commander } from '@/commands/editor/commander'
import type { CommandContext } from '@/commands/editor/types'
import type { PcGts } from '@/models/editor/document'
import type { Page } from '@/models/editor/page'
import type { MetadataApplyPayload, RegionKindChangePayload } from '@/types/editor/metadata'
import {
  ChangeRegionKindCommand,
  DeletePolygonCommand,
  DeletePolylineCommand,
  DeleteSelectedElementsCommand,
  SetHiddenElementsCommand
} from '@/commands'
import { useEditorStore } from '@/stores/editor/editor.store'
import EditorSidebarTasks from '@/components/editor/sidebar/tasks.vue'
import type { LinkedTask, Subtask } from '~/types/index'
import { useEditorUiStore } from '@/stores/editor/editor.ui.store'
import { getEditorSession } from '@/session/editor/editor-session'

const overlay = useOverlay()
const confirmModal = overlay.create(LazyUiConfirmModal)

const editorStore = useEditorStore()
const editorUiStore = useEditorUiStore()

function getCommandContext(): CommandContext | undefined {
  const canvasId = editorStore.activeCanvasId
  if (!canvasId) return undefined
  const session = getEditorSession(canvasId)
  return session ? { canvasId, session } : undefined
}

interface PolygonSidebarProps {
  collapsed?: boolean
  commander?: Commander | null
  pageId?: string | null
  polygons: TreeItemData[]
  polylines?: TreeItemData[]
  selectedPolygonIds?: string[]
  selectedPolylineIds?: string[]
  selectedPolygonId?: string | null
  selectedPolylineId?: string | null
  hiddenPolygonIds?: string[]
  hiddenPolylineIds?: string[]
  hoveredPolygonId?: string | null
  document?: PcGts | null
  page?: Page | null
  accordionPanels?: string[]
  openTasks?: Subtask[]
  taskById?: Record<string, LinkedTask>
  isPageLocked?: boolean
  isTasksLoading?: boolean
  onCompleteTask?: (subtask: Subtask) => void
}

const props = withDefaults(defineProps<PolygonSidebarProps>(), {
  collapsed: false,
  commander: null,
  pageId: null,
  polylines: () => [],
  selectedPolygonIds: () => [],
  selectedPolylineIds: () => [],
  selectedPolygonId: null,
  selectedPolylineId: null,
  hiddenPolygonIds: () => [],
  hiddenPolylineIds: () => [],
  hoveredPolygonId: null,
  document: null,
  page: null,
  accordionPanels: () => ['structure'],
  openTasks: () => [],
  taskById: () => ({}),
  isPageLocked: false,
  isTasksLoading: false,
  onCompleteTask: () => {}
})

const emit = defineEmits<{
  'select-polygon': [polygonId: string | null, options?: { zoomToFit?: boolean }]
  'select-polyline': [polylineId: string | null, options?: { zoomToFit?: boolean }]
  'hover-polygon': [polygonId: string | null]
  'hover-polyline': [polylineId: string | null]
  'unhover-polygon': []
  'clear-selection': []
  'update:accordionPanels': [panels: string[]]
  'apply-reading-order': [readingOrder: ReadingOrder]
  'apply-metadata': [payload: MetadataApplyPayload]
}>()

const accordionModel = computed({
  get: () => props.accordionPanels,
  set: val => emit('update:accordionPanels', val as string[])
})

const items = [
  {
    label: 'Structure',
    icon: 'i-lucide-table-of-contents',
    slot: 'structure'
  },
  {
    label: 'Reading Order',
    icon: 'i-lucide-list-ordered',
    slot: 'reading-order'
  },
  {
    label: 'Relations',
    icon: 'i-lucide-link-2',
    slot: 'relations'
  },
  {
    label: 'Metadata',
    icon: 'i-lucide-badge-info',
    slot: 'metadata'
  },
  {
    label: 'Tasks',
    icon: 'i-lucide-check-square',
    slot: 'tasks'
  },
  {
    label: 'Heatmap',
    icon: 'i-lucide-flame',
    slot: 'heatmap'
  },
  {
    label: 'Settings',
    icon: 'i-lucide-settings',
    slot: 'settings'
  }
]

const openTaskCount = computed(() => props.openTasks?.length ?? 0)

const expandedRegions = ref<Set<string>>(new Set())
const hiddenPolygonIdSet = computed(() => new Set(props.hiddenPolygonIds ?? []))
const hiddenPolylineIdSet = computed(() => new Set(props.hiddenPolylineIds ?? []))

const selectedCount = computed(() => (props.selectedPolygonIds?.length ?? 0) + (props.selectedPolylineIds?.length ?? 0))

const regions = computed(() =>
  props.polygons.filter(polygon => polygon.type === PolygonType.REGION && !polygon.parentId)
)

const parentMap = computed(() => {
  const map = new Map<string, string | undefined>()
  for (const p of props.polygons) {
    map.set(p.id, p.parentId)
  }
  for (const p of props.polylines ?? []) {
    map.set(p.id, p.parentId)
  }
  return map
})

function expandAncestors(elementId: string): void {
  let parentId = parentMap.value.get(elementId)
  while (parentId) {
    expandedRegions.value.add(parentId)
    parentId = parentMap.value.get(parentId)
  }
  expandedRegions.value = new Set(expandedRegions.value)
}

function findRegionById(regions: Region[], id: string): Region | null {
  for (const region of regions) {
    if (region.id === id) return region
    if (region.regions?.length) {
      const nested = findRegionById(region.regions, id)
      if (nested) return nested
    }
  }
  return null
}

function findTextLineById(regions: Region[], id: string): import('@/models/editor/text').TextLine | null {
  for (const region of regions) {
    if (region.kind === 'TextRegion' && (region as TextRegion).textLines) {
      const textLine = (region as TextRegion).textLines!.find(tl => tl.id === id)
      if (textLine) return textLine
    }
    if (region.regions?.length) {
      const nested = findTextLineById(region.regions, id)
      if (nested) return nested
    }
  }
  return null
}

const selectedElement = computed<Region | import('@/models/editor/text').TextLine | RenderablePolyline | null>(() => {
  if (!props.page?.regions) return null

  if (props.selectedPolylineId) {
    const polyline = props.polylines.find(p => p.id === props.selectedPolylineId)
    return (polyline as RenderablePolyline | undefined) ?? null
  }

  if (props.selectedPolygonId) {
    const polygon = props.polygons.find(p => p.id === props.selectedPolygonId)
    if (!polygon) return null

    if (polygon.type === PolygonType.REGION) {
      return findRegionById(props.page.regions, props.selectedPolygonId)
    } else if (polygon.type === PolygonType.TEXTLINE) {
      return findTextLineById(props.page.regions, props.selectedPolygonId)
    }
  }

  return null
})

function cloneReadingOrder(readingOrder: ReadingOrder): ReadingOrder {
  return JSON.parse(JSON.stringify(readingOrder)) as ReadingOrder
}

const localReadingOrder = ref<ReadingOrder>({
  root: {
    kind: 'OrderedGroup',
    id: 'reading_order_root',
    elements: []
  }
})

watch(
  () => props.page?.readingOrder,
  (newReadingOrder) => {
    if (newReadingOrder) {
      localReadingOrder.value = cloneReadingOrder(newReadingOrder)
    } else {
      localReadingOrder.value = {
        root: {
          kind: 'OrderedGroup',
          id: 'reading_order_root',
          elements: []
        }
      }
    }
  },
  { immediate: true, deep: true }
)

watch(
  () => editorUiStore.readingOrderVersion,
  () => {
    if (props.page?.readingOrder) {
      localReadingOrder.value = cloneReadingOrder(props.page.readingOrder)
    }
  }
)

function handleReadingOrderUpdate(newReadingOrder: ReadingOrder): void {
  const cloned = cloneReadingOrder(newReadingOrder)
  localReadingOrder.value = cloned
  emit('apply-reading-order', cloned)
}

const allRegionsForReadingOrder = computed<AvailableItem[]>(() => {
  return props.polygons
    .filter(polygon => polygon.type === PolygonType.REGION || polygon.type === PolygonType.TEXTLINE)
    .map(polygon => ({
      id: polygon.id,
      label: polygon.label || polygon.id,
      regionRef: polygon.id,
      parentId: polygon.parentId
    }))
})

const allRegionsForRelations = computed(() => {
  return props.polygons
    .filter(polygon => polygon.type === PolygonType.REGION)
    .map(polygon => ({
      value: polygon.id,
      label: polygon.label ? `${polygon.label} (${polygon.id})` : polygon.id
    }))
})

function toggleExpanded(elementId: string): void {
  if (expandedRegions.value.has(elementId)) {
    expandedRegions.value.delete(elementId)
  } else {
    expandedRegions.value.add(elementId)
  }
}

function selectPolygon(polygonId: string): void {
  emit('select-polygon', polygonId)
}

function selectPolyline(polylineId: string): void {
  emit('select-polyline', polylineId)
}

function hoverPolygon(polygonId: string): void {
  emit('hover-polygon', polygonId)
}

function hoverPolyline(polylineId: string): void {
  emit('hover-polyline', polylineId)
}

function unhoverPolygon(): void {
  emit('unhover-polygon')
}

function togglePolygonVisibility(polygonId: string): void {
  if (!props.commander) return
  if (!props.pageId) return

  const ctx = getCommandContext()
  const polyline = props.polylines.find(p => p.id === polygonId)
  if (polyline) {
    const isHidden = hiddenPolylineIdSet.value.has(polygonId)
    props.commander.execute(
      new SetHiddenElementsCommand({
        pageId: props.pageId,
        action: isHidden ? 'show' : 'hide',
        polylineIds: [polygonId]
      }),
      ctx
    )
    return
  }

  const isHidden = hiddenPolygonIdSet.value.has(polygonId)
  props.commander.execute(
    new SetHiddenElementsCommand({
      pageId: props.pageId,
      action: isHidden ? 'show' : 'hide',
      polygonIds: [polygonId]
    }),
    ctx
  )
}

function hideSelected() {
  if (!props.commander) return
  if (!props.pageId) return
  if (selectedCount.value === 0) return

  const ctx = getCommandContext()
  props.commander.execute(
    new SetHiddenElementsCommand({
      pageId: props.pageId,
      action: 'hide',
      polygonIds: props.selectedPolygonIds,
      polylineIds: props.selectedPolylineIds
    }),
    ctx
  )

  emit('clear-selection')
}

function showSelected() {
  if (!props.commander) return
  if (!props.pageId) return
  if (selectedCount.value === 0) return

  const ctx = getCommandContext()
  props.commander.execute(
    new SetHiddenElementsCommand({
      pageId: props.pageId,
      action: 'show',
      polygonIds: props.selectedPolygonIds,
      polylineIds: props.selectedPolylineIds
    }),
    ctx
  )
}

async function deleteSelected(): Promise<void> {
  if (!props.commander) return
  if (selectedCount.value === 0) return

  const instance = confirmModal.open({
    title: 'Delete Selected Items?',
    description: `Are you sure you want to delete ${selectedCount.value} selected item(s)? This action cannot be undone.`,
    confirmLabel: 'Delete',
    confirmColor: 'error'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  const ctx = getCommandContext()
  props.commander.execute(
    new DeleteSelectedElementsCommand({
      polygonIds: props.selectedPolygonIds,
      polylineIds: props.selectedPolylineIds
    }),
    ctx
  )

  emit('clear-selection')
}

async function deletePolygon(polygonId: string): Promise<void> {
  if (!props.commander) return

  const ctx = getCommandContext()

  const polyline = props.polylines.find(p => p.id === polygonId)
  if (polyline) {
    const instance = confirmModal.open({
      title: 'Delete Baseline?',
      description: `Are you sure you want to delete baseline "${polyline.label || polyline.id}"?`,
      confirmLabel: 'Delete',
      confirmColor: 'error'
    })
    const confirmed = await instance.result
    if (!confirmed) return

    const parentId = polyline.parentId

    const deleteCommand = new DeletePolylineCommand({
      polylineId: polygonId
    })

    props.commander.execute(deleteCommand, ctx)

    if (parentId) {
      emit('select-polygon', parentId, { zoomToFit: false })
    }
    return
  }

  const polygon = props.polygons.find(p => p.id === polygonId)
  if (!polygon) return

  const parentId = polygon.parentId

  const hasChildren = props.polygons.some(p => p.parentId === polygonId)
  const instance = confirmModal.open({
    title: hasChildren ? 'Delete Region and Children?' : 'Delete Region?',
    description: hasChildren
      ? `Are you sure you want to delete "${polygon.label}"? This will also delete all associated textlines and baselines.`
      : `Are you sure you want to delete "${polygon.label}"?`,
    confirmLabel: 'Delete',
    confirmColor: 'error'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  const deleteCommand = new DeletePolygonCommand({
    polygonId: polygonId
  })

  props.commander.execute(deleteCommand, ctx)

  if (parentId) {
    emit('select-polygon', parentId, { zoomToFit: false })
  } else {
    emit('select-polygon', null)
  }
}

function handleMetadataApply(payload: MetadataApplyPayload) {
  emit('apply-metadata', payload)
}

function handleRegionKindChange(payload: RegionKindChangePayload) {
  if (!props.commander) return
  const ctx = getCommandContext()
  props.commander.execute(
    new ChangeRegionKindCommand(payload),
    ctx
  )
}

watch(() => props.polygons, (newPolygons) => {
  let changed = false
  newPolygons.forEach((polygon) => {
    if (polygon.type === PolygonType.REGION || polygon.type === PolygonType.TEXTLINE) {
      if (!expandedRegions.value.has(polygon.id)) {
        expandedRegions.value.add(polygon.id)
        changed = true
      }
    }
  })
  if (changed) {
    expandedRegions.value = new Set(expandedRegions.value)
  }
}, { deep: true })

watch(() => props.selectedPolygonIds, (newIds) => {
  if (!newIds?.length) return
  for (const id of newIds) {
    expandAncestors(id)
  }
}, { deep: true })

watch(() => props.selectedPolylineIds, (newIds) => {
  if (!newIds?.length) return
  for (const id of newIds) {
    expandAncestors(id)
  }
}, { deep: true })
</script>

<template>
  <div class="h-full flex flex-col bg-background">
    <div v-if="collapsed" class="flex flex-col items-center gap-1 py-1">
      <UPopover v-for="item in items" :key="item.slot" :content="{ side: 'left', align: 'start', sideOffset: 12 }">
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
          <div :class="[item.slot === 'reading-order' || item.slot === 'relations' ? 'w-md' : 'w-80', 'max-h-[70vh] overflow-auto']">
            <div class="px-3 py-2 border-b border-default">
              <span class="text-sm font-semibold">{{ item.label }}</span>
            </div>
            <template v-if="item.slot === 'structure'">
              <div data-tour="editor-layout-structure-panel">
                <EditorSidebarStructurePanel
                  :polygons="polygons"
                  :polylines="polylines"
                  :regions="regions"
                  :selected-polygon-ids="selectedPolygonIds"
                  :selected-polyline-ids="selectedPolylineIds"
                  :hovered-polygon-id="hoveredPolygonId"
                  :hidden-polygon-ids="hiddenPolygonIds"
                  :hidden-polyline-ids="hiddenPolylineIds"
                  :expanded-regions="expandedRegions"
                  :selected-count="selectedCount"
                  @select-polygon="selectPolygon"
                  @select-polyline="selectPolyline"
                  @hover-polygon="hoverPolygon"
                  @hover-polyline="hoverPolyline"
                  @unhover-polygon="unhoverPolygon"
                  @delete-item="deletePolygon"
                  @toggle-visibility="togglePolygonVisibility"
                  @toggle-expanded="toggleExpanded"
                  @delete-selected="deleteSelected"
                  @hide-selected="hideSelected"
                  @show-selected="showSelected"
                />
              </div>
            </template>
            <template v-else-if="item.slot === 'reading-order'">
              <div data-tour="editor-layout-reading-order-panel">
                <EditorReadingOrderPanel
                  :model-value="localReadingOrder"
                  :all-items="allRegionsForReadingOrder"
                  @update:model-value="handleReadingOrderUpdate"
                />
              </div>
            </template>
            <template v-else-if="item.slot === 'relations'">
              <div data-tour="editor-layout-relations-panel">
                <EditorRelationsPanel
                  :document="document"
                  :page="page"
                  :commander="commander"
                  :regions="allRegionsForRelations"
                />
              </div>
            </template>
            <template v-else-if="item.slot === 'metadata'">
              <div data-tour="editor-layout-metadata-panel">
                <EditorSidebarMetadata
                  :document="document"
                  :page="page"
                  :selected-element="selectedElement"
                  @apply="handleMetadataApply"
                  @change-region-kind="handleRegionKindChange"
                />
              </div>
            </template>
            <template v-else-if="item.slot === 'tasks'">
              <div data-tour="editor-layout-tasks-panel" class="p-3">
                <EditorSidebarTasks
                  :open-tasks="openTasks"
                  :task-by-id="taskById"
                  :is-page-locked="isPageLocked"
                  :is-loading="isTasksLoading"
                  :on-complete-subtask="onCompleteTask"
                />
              </div>
            </template>
            <template v-else-if="item.slot === 'heatmap'">
              <div data-tour="editor-layout-heatmap-panel">
                <EditorSidebarHeatmap />
              </div>
            </template>
            <template v-else-if="item.slot === 'settings'">
              <EditorSidebarSettings />
            </template>
          </div>
        </template>
      </UPopover>
    </div>

    <UAccordion
      v-else
      v-model="accordionModel"
      type="multiple"
      :items="items"
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

      <template #structure>
        <div data-tour="editor-layout-structure-panel">
          <EditorSidebarStructurePanel
            :polygons="polygons"
            :polylines="polylines"
            :regions="regions"
            :selected-polygon-ids="selectedPolygonIds"
            :selected-polyline-ids="selectedPolylineIds"
            :hovered-polygon-id="hoveredPolygonId"
            :hidden-polygon-ids="hiddenPolygonIds"
            :hidden-polyline-ids="hiddenPolylineIds"
            :expanded-regions="expandedRegions"
            :selected-count="selectedCount"
            @select-polygon="selectPolygon"
            @select-polyline="selectPolyline"
            @hover-polygon="hoverPolygon"
            @hover-polyline="hoverPolyline"
            @unhover-polygon="unhoverPolygon"
            @delete-item="deletePolygon"
            @toggle-visibility="togglePolygonVisibility"
            @toggle-expanded="toggleExpanded"
            @delete-selected="deleteSelected"
            @hide-selected="hideSelected"
            @show-selected="showSelected"
          />
        </div>
      </template>
      <template #reading-order>
        <div data-tour="editor-layout-reading-order-panel" class="h-full">
          <EditorReadingOrderPanel
            :model-value="localReadingOrder"
            :all-items="allRegionsForReadingOrder"
            @update:model-value="handleReadingOrderUpdate"
          />
        </div>
      </template>
      <template #relations>
        <div data-tour="editor-layout-relations-panel" class="h-full">
          <EditorRelationsPanel
            :document="document"
            :page="page"
            :commander="commander"
            :regions="allRegionsForRelations"
          />
        </div>
      </template>
      <template #metadata>
        <div data-tour="editor-layout-metadata-panel">
          <EditorSidebarMetadata
            :document="document"
            :page="page"
            :selected-element="selectedElement"
            @apply="handleMetadataApply"
            @change-region-kind="handleRegionKindChange"
          />
        </div>
      </template>
      <template #tasks>
        <div data-tour="editor-layout-tasks-panel" class="p-3">
          <EditorSidebarTasks
            :open-tasks="openTasks"
            :task-by-id="taskById"
            :is-page-locked="isPageLocked"
            :is-loading="isTasksLoading"
            :on-complete-subtask="onCompleteTask"
          />
        </div>
      </template>
      <template #heatmap>
        <div data-tour="editor-layout-heatmap-panel">
          <EditorSidebarHeatmap />
        </div>
      </template>
      <template #settings>
        <EditorSidebarSettings />
      </template>
    </UAccordion>
  </div>
</template>
