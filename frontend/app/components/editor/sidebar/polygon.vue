<script setup lang="ts">
import { PolygonType, type ReadingOrder } from '@/models/editor'
import type { Region, TextRegion } from '@/models/editor/region'
import type { Glyph, TextLine, Word } from '@/models/editor/text'
import { findGlyphRecursive, findRegionRecursive, findTextLineRecursive, findWordRecursive, toPolygonElementType } from '@/utils/editor/pcgts-editor-primitives'
import { findParentTable } from '@/utils/editor/table/table-model'
import { PAGE_FRAME_LABELS, isPageFrameType } from '@/utils/editor/page-frame'
import type { AvailableItem } from '@/components/editor/reading-order'
import type { TreeItemData } from '@/components/editor/sidebar/structure-tree'
import type { RenderablePolyline } from '@/types/editor/rendering'
import type { SelectionFocusOptions } from '@/types/editor/canvas-controls'
import type { Commander } from '@/commands/editor/commander'
import type { CommandContext } from '@/commands/editor/types'
import type { PcGts } from '@/models/editor/document'
import type { Page } from '@/models/editor/page'
import type { MetadataApplyPayload, RegionKindChangePayload } from '@/types/editor/metadata'
import {
  ChangeRegionKindCommand,
  DeletePolygonCommand,
  DeletePolylineCommand,
  SetHiddenElementsCommand,
  createReparentCommand
} from '@/commands'
import { getEditorSession } from '@/session/editor/editor-session'
import { showEditorDeleteUndoToast } from '@/composables/editor/use-editor-delete-undo-toast'
import { resolveRegionLabelDisplayName } from '@/utils/editor/page-label-mapping'
import { cloneReadingOrder } from '@/commands/editor/reading-order-utils'

const editorStore = useEditorStore()
const editorUiStore = useEditorUiStore()
const DELETE_ELEMENT_LABELS = {
  region: 'Region',
  textline: 'Text Line',
  word: 'Word',
  glyph: 'Glyph'
} as const

const activePopoverButtonClass = 'bg-navy-600 text-white hover:bg-navy-500 active:bg-navy-700 dark:bg-navy-500 dark:hover:bg-navy-400 dark:active:bg-navy-600'

function showReadingOrderOverlay(): void {
  editorUiStore.setReadingOrderOverlayVisible(true)
}

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
  document?: PcGts | null
  page?: Page | null
  accordionPanels?: string[]
  isPageLocked?: boolean
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
  document: null,
  page: null,
  accordionPanels: () => [],
  isPageLocked: false
})

const emit = defineEmits<{
  'select-polygon': [polygonId: string | null, options?: SelectionFocusOptions]
  'select-polyline': [polylineId: string | null, options?: SelectionFocusOptions]
  'hover-polygon': [polygonId: string | null]
  'hover-polyline': [polylineId: string | null]
  'unhover-polygon': []
  'update:accordionPanels': [panels: string[]]
  'apply-reading-order': [readingOrder: ReadingOrder]
  'apply-metadata': [payload: MetadataApplyPayload]
}>()

const accordionModel = computed({
  get: () => props.accordionPanels,
  set: (val) => {
    const panels = val as string[]
    if (panels.includes('reading-order')) showReadingOrderOverlay()
    emit('update:accordionPanels', panels)
  }
})

const baseItems = [
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

const selectedTableId = computed(() => {
  const id = props.selectedPolygonId
  const regions = props.document?.page?.regions
  if (!id || !regions) return null
  const hit = findRegionRecursive(regions, id)
  if (hit) return hit.region.kind === 'TableRegion' ? hit.region.id : findParentTable(regions, id)?.id ?? null
  const line = findTextLineRecursive(regions, id)
  return line ? findParentTable(regions, line.parentTextRegion.id)?.id ?? null : null
})

const items = computed(() => selectedTableId.value
  ? [baseItems[0]!, { label: 'Table', icon: 'i-lucide-table', slot: 'table' }, ...baseItems.slice(1)]
  : baseItems)

const collapsedPopoverSlot = ref<string | null>(null)

const expandedRegions = ref<Set<string>>(new Set())
const hiddenPolygonIdSet = computed(() => new Set(props.hiddenPolygonIds ?? []))
const hiddenPolylineIdSet = computed(() => new Set(props.hiddenPolylineIds ?? []))

function openCollapsedPopover(slot: string) {
  collapsedPopoverSlot.value = slot
}

function closeCollapsedPopover() {
  collapsedPopoverSlot.value = null
}

function handleCollapsedPopoverOpenUpdate(slot: string, open: boolean) {
  if (open) {
    if (slot === 'reading-order') showReadingOrderOverlay()
    openCollapsedPopover(slot)
  } else if (collapsedPopoverSlot.value === slot) {
    closeCollapsedPopover()
  }
}

const regions = computed(() => [
  ...props.polygons.filter(polygon => isPageFrameType(polygon.type)),
  ...props.polygons.filter(polygon => polygon.type === PolygonType.REGION && !polygon.parentId)
])

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

const selectedTextElementType = computed<'word' | 'glyph' | null>(() => {
  if (props.selectedPolylineId || !props.selectedPolygonId) return null
  const type = props.polygons.find(p => p.id === props.selectedPolygonId)?.type
  return type === PolygonType.WORD || type === PolygonType.GLYPH ? type : null
})

const selectedElement = computed<Region | TextLine | Word | Glyph | RenderablePolyline | null>(() => {
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
    } else if (polygon.type === PolygonType.WORD) {
      return findWordRecursive(props.page.regions, props.selectedPolygonId)?.word ?? null
    } else if (polygon.type === PolygonType.GLYPH) {
      return findGlyphRecursive(props.page.regions, props.selectedPolygonId)?.glyph ?? null
    }
  }

  return null
})

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
  if (props.isPageLocked) return
  const cloned = cloneReadingOrder(newReadingOrder)
  localReadingOrder.value = cloned
  emit('apply-reading-order', cloned)
}

const allRegionsForReadingOrder = computed<AvailableItem[]>(() => {
  return props.polygons
    .filter(polygon => polygon.type === PolygonType.REGION || polygon.type === PolygonType.TEXTLINE)
    .map(polygon => ({
      id: polygon.id,
      label: polygon.type === PolygonType.REGION
        ? resolveRegionLabelDisplayName(editorStore.labelSet?.labels, polygon, polygon.label || polygon.id) ?? polygon.id
        : polygon.label || polygon.id,
      regionRef: polygon.id,
      parentId: polygon.parentId
    }))
})

const allRegionsForRelations = computed(() => {
  return props.polygons
    .filter(polygon => polygon.type === PolygonType.REGION)
    .map(polygon => ({
      value: polygon.id,
      label: `${resolveRegionLabelDisplayName(editorStore.labelSet?.labels, polygon, polygon.label || polygon.id) ?? polygon.id} (${polygon.id})`
    }))
})

function toggleExpanded(elementId: string): void {
  if (expandedRegions.value.has(elementId)) {
    expandedRegions.value.delete(elementId)
  } else {
    expandedRegions.value.add(elementId)
  }
}

function expandAll(): void {
  const expandableIds = new Set<string>()
  for (const polygon of props.polygons) {
    if (polygon.parentId) expandableIds.add(polygon.parentId)
  }
  for (const polyline of props.polylines ?? []) {
    if (polyline.parentId) {
      expandableIds.add(polyline.parentId)
    } else if (polyline.parentPolygonId) {
      expandableIds.add(polyline.parentPolygonId)
    }
  }
  expandedRegions.value = expandableIds
}

function collapseAll(): void {
  expandedRegions.value = new Set()
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

function moveItems(itemIds: string[], newParentId: string | null): void {
  if (!props.commander || props.isPageLocked) return

  const ctx = getCommandContext()
  const ids = new Set(itemIds)
  const moves = props.polygons
    .filter(polygon => ids.has(polygon.id))
    .map(({ id, type }) => ({ id, type, newParentId }))
  if (!ctx || moves.length === 0) return

  props.commander.execute(createReparentCommand(moves, `Move ${moves.length} elements`), ctx)
  if (newParentId) {
    expandAncestors(newParentId)
    expandedRegions.value.add(newParentId)
  }
}

function deletePolygon(polygonId: string): void {
  if (!props.commander || props.isPageLocked) return

  const ctx = getCommandContext()
  if (!ctx) return
  const commander = props.commander

  const polyline = props.polylines.find(p => p.id === polygonId)
  if (polyline) {
    const parentId = polyline.parentId
    const deleteCommand = new DeletePolylineCommand({
      polylineId: polygonId
    })

    commander.execute(deleteCommand, ctx)

    if (parentId) {
      emit('select-polygon', parentId, { zoomToFit: false })
    }
    showEditorDeleteUndoToast({
      title: `Deleted baseline "${polyline.label || polyline.id}"`,
      commander,
      command: deleteCommand,
      canvasId: ctx.canvasId
    })
    return
  }

  const polygon = props.polygons.find(p => p.id === polygonId)
  if (!polygon) return

  const parentId = polygon.parentId
  const displayLabel = polygon.type === PolygonType.REGION
    ? resolveRegionLabelDisplayName(editorStore.labelSet?.labels, polygon, polygon.label || polygon.id) ?? polygon.id
    : polygon.label || polygon.id

  const hasChildren = props.polygons.some(p => p.parentId === polygonId)
  const elementLabel = isPageFrameType(polygon.type)
    ? PAGE_FRAME_LABELS[polygon.type]
    : DELETE_ELEMENT_LABELS[toPolygonElementType(polygon.type)]

  const deleteCommand = new DeletePolygonCommand({
    polygonId: polygonId
  })

  commander.execute(deleteCommand, ctx)

  if (parentId) {
    emit('select-polygon', parentId, { zoomToFit: false })
  } else {
    emit('select-polygon', null)
  }
  showEditorDeleteUndoToast({
    title: `Deleted ${elementLabel.toLowerCase()} "${displayLabel}"`,
    description: hasChildren ? 'Also removed its child elements.' : undefined,
    commander,
    command: deleteCommand,
    canvasId: ctx.canvasId,
    onUndone: () => emit('select-polygon', polygonId, { zoomToFit: false })
  })
}

function handleMetadataApply(payload: MetadataApplyPayload) {
  if (props.isPageLocked) return
  emit('apply-metadata', payload)
}

function handleRegionKindChange(payload: RegionKindChangePayload) {
  if (!props.commander || props.isPageLocked) return
  const ctx = getCommandContext()
  props.commander.execute(
    new ChangeRegionKindCommand(payload),
    ctx
  )
}

watch(() => props.polygons.map(polygon => polygon.id), (polygonIds) => {
  if (expandedRegions.value.size === 0) return

  const validIds = new Set(polygonIds)
  const nextExpanded = new Set<string>()

  for (const id of expandedRegions.value) {
    if (validIds.has(id)) {
      nextExpanded.add(id)
    }
  }

  if (nextExpanded.size !== expandedRegions.value.size) {
    expandedRegions.value = nextExpanded
  }
}, { immediate: true })

watch(() => props.selectedPolygonIds, (newIds) => {
  if (!newIds?.length) return
  for (const id of newIds) {
    expandAncestors(id)
  }
})

watch(() => props.selectedPolylineIds, (newIds) => {
  if (!newIds?.length) return
  for (const id of newIds) {
    expandAncestors(id)
  }
})

watch(() => props.collapsed, (collapsed) => {
  if (!collapsed) closeCollapsedPopover()
})
</script>

<template>
  <div class="h-full flex flex-col">
    <div v-if="collapsed" class="flex flex-col items-center gap-1 py-1">
      <UPopover
        v-for="item in items"
        :key="item.slot"
        :open="collapsedPopoverSlot === item.slot"
        :dismissible="false"
        :content="{ side: 'left', align: 'start', sideOffset: 12 }"
        @update:open="(open: boolean) => handleCollapsedPopoverOpenUpdate(item.slot, open)"
      >
        <UTooltip :text="item.label" :content="{ side: 'left' }">
          <UButton
            variant="ghost"
            color="neutral"
            size="sm"
            :icon="item.icon"
            :aria-label="item.label"
            :class="collapsedPopoverSlot === item.slot ? activePopoverButtonClass : undefined"
          />
        </UTooltip>
        <template #content>
          <div :class="[item.slot === 'reading-order' || item.slot === 'relations' ? 'w-md' : 'w-80', 'max-h-[70vh] overflow-auto']">
            <div class="px-3 py-2 border-b border-default flex items-center justify-between gap-2">
              <span class="text-sm font-semibold">{{ item.label }}</span>
              <UButton
                type="button"
                variant="ghost"
                color="neutral"
                size="xs"
                icon="i-lucide-x"
                :aria-label="`Close ${item.label}`"
                @click.stop="closeCollapsedPopover"
              />
            </div>
            <template v-if="item.slot === 'structure'">
              <div data-tour="editor-layout-structure-panel">
                <EditorSidebarStructurePanel
                  :polygons="polygons"
                  :polylines="polylines"
                  :regions="regions"
                  :selected-polygon-ids="selectedPolygonIds"
                  :selected-polyline-ids="selectedPolylineIds"
                  :hidden-polygon-ids="hiddenPolygonIds"
                  :hidden-polyline-ids="hiddenPolylineIds"
                  :expanded-regions="expandedRegions"
                  :read-only="isPageLocked"
                  @select-polygon="selectPolygon"
                  @select-polyline="selectPolyline"
                  @hover-polygon="hoverPolygon"
                  @hover-polyline="hoverPolyline"
                  @unhover-polygon="unhoverPolygon"
                  @delete-item="deletePolygon"
                  @move-items="moveItems"
                  @toggle-visibility="togglePolygonVisibility"
                  @toggle-expanded="toggleExpanded"
                  @collapse-all="collapseAll"
                  @expand-all="expandAll"
                />
              </div>
            </template>
            <template v-else-if="item.slot === 'reading-order'">
              <div data-tour="editor-layout-reading-order-panel">
                <EditorReadingOrderPanel
                  :model-value="localReadingOrder"
                  :all-items="allRegionsForReadingOrder"
                  :read-only="isPageLocked"
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
                  :read-only="isPageLocked"
                />
              </div>
            </template>
            <template v-else-if="item.slot === 'table' && selectedTableId && editorStore.activeCanvasId">
              <div class="p-3">
                <EditorTableInspector
                  :canvas-id="editorStore.activeCanvasId"
                  :table-id="selectedTableId"
                  :read-only="isPageLocked"
                />
              </div>
            </template>
            <template v-else-if="item.slot === 'metadata'">
              <div data-tour="editor-layout-metadata-panel">
                <EditorSidebarMetadata
                  :document="document"
                  :page="page"
                  :selected-element="selectedElement"
                  :selected-text-element-type="selectedTextElementType"
                  :read-only="isPageLocked"
                  @apply="handleMetadataApply"
                  @change-region-kind="handleRegionKindChange"
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
      value-key="slot"
    >
      <template #leading="{ item }">
        <Icon class="size-5" :name="item.icon" />
      </template>

      <template #structure>
        <div data-tour="editor-layout-structure-panel">
          <EditorSidebarStructurePanel
            :polygons="polygons"
            :polylines="polylines"
            :regions="regions"
            :selected-polygon-ids="selectedPolygonIds"
            :selected-polyline-ids="selectedPolylineIds"
            :hidden-polygon-ids="hiddenPolygonIds"
            :hidden-polyline-ids="hiddenPolylineIds"
            :expanded-regions="expandedRegions"
            :read-only="isPageLocked"
            @select-polygon="selectPolygon"
            @select-polyline="selectPolyline"
            @hover-polygon="hoverPolygon"
            @hover-polyline="hoverPolyline"
            @unhover-polygon="unhoverPolygon"
            @delete-item="deletePolygon"
            @move-items="moveItems"
            @toggle-visibility="togglePolygonVisibility"
            @toggle-expanded="toggleExpanded"
            @collapse-all="collapseAll"
            @expand-all="expandAll"
          />
        </div>
      </template>
      <template #reading-order>
        <div data-tour="editor-layout-reading-order-panel" class="h-full">
          <EditorReadingOrderPanel
            :model-value="localReadingOrder"
            :all-items="allRegionsForReadingOrder"
            :read-only="isPageLocked"
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
            :read-only="isPageLocked"
          />
        </div>
      </template>
      <template #table>
        <div class="p-3">
          <EditorTableInspector
            v-if="selectedTableId && editorStore.activeCanvasId"
            :canvas-id="editorStore.activeCanvasId"
            :table-id="selectedTableId"
            :read-only="isPageLocked"
          />
        </div>
      </template>
      <template #metadata>
        <div data-tour="editor-layout-metadata-panel">
          <EditorSidebarMetadata
            :document="document"
            :page="page"
            :selected-element="selectedElement"
            :selected-text-element-type="selectedTextElementType"
            :read-only="isPageLocked"
            @apply="handleMetadataApply"
            @change-region-kind="handleRegionKindChange"
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
