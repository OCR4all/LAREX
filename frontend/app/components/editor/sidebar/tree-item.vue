<script setup lang="ts">
import type { Vertex } from '@/models/editor/types'
import type { LabelDefinition } from '@/models/editor/labels'
import type { RegionKind } from '@/models/editor/region'
import { getRegionColor, getRegionKindIcon } from '@/utils/editor/region-colors'
import { findRegionLabelDefinitionForRegion } from '@/utils/editor/page-label-mapping'

export interface TreeItemData {
  id: string
  type?: string
  parentId?: string
  parentPolygonId?: string // For polylines that reference a parent polygon
  label?: string
  points?: Vertex[]
  regionKind?: string
  regionSubtype?: string
  regionCustom?: string
}

export interface TreeItemProps {
  item: TreeItemData
  level?: number
  selectedPolygonIds?: string[]
  selectedPolylineIds?: string[]
  hoveredId?: string | null
  polygons: TreeItemData[]
  polylines?: TreeItemData[]
  expandedRegions: Set<string>
  hiddenPolygonIds?: string[]
  hiddenPolylineIds?: string[]
  /** Enable focus management for keyboard navigation */
  isFocusable?: boolean
}

const editorStore = useEditorStore()

const props = withDefaults(defineProps<TreeItemProps>(), {
  level: 0,
  selectedPolygonIds: () => [],
  selectedPolylineIds: () => [],
  hoveredId: null,
  polylines: () => [],
  hiddenPolygonIds: () => [],
  hiddenPolylineIds: () => [],
  isFocusable: true
})

const emit = defineEmits<{
  'select-item': [id: string]
  'select-polyline': [id: string]
  'hover-item': [id: string]
  'hover-polyline': [id: string]
  'unhover-item': []
  'delete-item': [id: string]
  'toggle-visibility': [id: string]
  'toggle-expanded': [id: string]
  'navigate-next': []
  'navigate-prev': []
  'navigate-parent': []
  'navigate-first-child': []
}>()

const itemRow = ref<HTMLElement | null>(null)

const isExpanded = computed(() => props.expandedRegions.has(props.item.id))

const itemType = computed(() => {
  if (props.item.type) {
    return props.item.type.toLowerCase()
  }
  return 'baseline'
})

const hasChildren = computed<boolean>(() => {
  const polygonChildren = props.polygons.filter(p => p.parentId === props.item.id)
  if (polygonChildren.length > 0) return true

  const polylineChildren = props.polylines.filter(p =>
    p.parentId === props.item.id || p.parentPolygonId === props.item.id
  )
  return polylineChildren.length > 0
})

const children = computed<TreeItemData[]>(() => {
  const allChildren: TreeItemData[] = []

  const polygonChildren = props.polygons.filter(p => p.parentId === props.item.id)
  allChildren.push(...polygonChildren)

  const polylineChildren = props.polylines.filter(p =>
    p.parentId === props.item.id || p.parentPolygonId === props.item.id
  )
  allChildren.push(...polylineChildren)

  const typeOrder: Record<string, number> = { REGION: 0, TEXTLINE: 1, BASELINE: 2 }
  return allChildren.sort((a, b) => {
    if (a.type !== b.type) {
      const aType = a.type?.toUpperCase() ?? ''
      const bType = b.type?.toUpperCase() ?? ''
      return (typeOrder[aType] || 999) - (typeOrder[bType] || 999)
    }
    return (a.label || '').localeCompare(b.label || '')
  })
})

const isItemSelected = computed<boolean>(() => {
  const type = props.item.type?.toUpperCase?.() ?? ''
  const isBaseline = type === 'BASELINE' || props.item.type === 'baseline'
  if (isBaseline) return props.selectedPolylineIds.includes(props.item.id)
  return props.selectedPolygonIds.includes(props.item.id)
})

const isItemHovered = computed<boolean>(() => {
  return props.item.id === props.hoveredId
})

function isSelected(item: TreeItemData): boolean {
  const type = item.type?.toUpperCase?.() ?? ''
  const isBaseline = type === 'BASELINE' || item.type === 'baseline'
  if (isBaseline) return props.selectedPolylineIds.includes(item.id)
  return props.selectedPolygonIds.includes(item.id)
}

function isHovered(item: TreeItemData): boolean {
  return item.id === props.hoveredId
}

function resolveRegionForItem(item: TreeItemData): { kind?: string, subtype?: string, custom?: string } {
  const polygon = props.polygons.find(p => p.id === item.id)
  const kind = polygon?.regionKind ?? item.regionKind
  const subtype = polygon?.regionSubtype ?? item.regionSubtype
  const custom = polygon?.regionCustom ?? item.regionCustom
  return { kind, subtype, custom }
}

function findLabelDefinitionForItem(item: TreeItemData): LabelDefinition | null {
  const labelSet = editorStore.labelSet
  if (!labelSet) return null
  const { kind, subtype, custom } = resolveRegionForItem(item)
  if (!kind) return null
  return findRegionLabelDefinitionForRegion(labelSet.labels as LabelDefinition[], {
    regionKind: kind,
    regionSubtype: subtype,
    regionCustom: custom
  }) ?? null
}

function getItemIconName(item: TreeItemData): string {
  const normalizedType = item.type?.toUpperCase()
  if (normalizedType === 'REGION') {
    const labelDef = findLabelDefinitionForItem(item)
    if (labelDef?.icon) return labelDef.icon
    const { kind } = resolveRegionForItem(item)
    if (kind) return getRegionKindIcon(kind as RegionKind)
  }
  const icons: Record<string, string> = {
    REGION: 'i-lucide-folder',
    TEXTLINE: 'i-lucide-type',
    BASELINE: 'i-lucide-ruler'
  }
  return icons[normalizedType ?? ''] || 'i-lucide-file-text'
}

function getItemVisibilityIconName(item: TreeItemData): string {
  const type = item.type?.toUpperCase?.() ?? ''
  const isBaseline = type === 'BASELINE' || item.type === 'baseline'
  const hiddenSet = new Set(isBaseline ? props.hiddenPolylineIds : props.hiddenPolygonIds)
  return hiddenSet.has(item.id) ? 'i-lucide-eye-off' : 'i-lucide-eye'
}

function getVisibilityTitle(item: TreeItemData): string {
  const typeUpper = item.type?.toUpperCase?.() ?? ''
  const isBaseline = typeUpper === 'BASELINE' || item.type === 'baseline'
  const hiddenSet = new Set(isBaseline ? props.hiddenPolylineIds : props.hiddenPolygonIds)
  const visible = !hiddenSet.has(item.id)
  const type = item.type ? item.type.toLowerCase() : 'baseline'
  return visible ? `Hide ${type}` : `Show ${type}`
}

function getLabelColor(item: TreeItemData): string {
  const labelDef = findLabelDefinitionForItem(item)
  if (labelDef?.color) return labelDef.color
  const { kind, subtype } = resolveRegionForItem(item)
  if (kind) {
    return getRegionColor(kind as RegionKind, subtype ?? undefined)
  }
  return '#666'
}

function shouldShowLabelIndicator(item: TreeItemData): boolean {
  const { kind } = resolveRegionForItem(item)
  return !!kind
}

function selectItem(item: TreeItemData): void {
  if (item.type === 'baseline' || item.type === 'BASELINE') {
    emit('select-polyline', item.id)
  } else {
    emit('select-item', item.id)
  }
}

function hoverItem(item: TreeItemData): void {
  if (item.type === 'baseline' || item.type === 'BASELINE') {
    emit('hover-polyline', item.id)
  } else {
    emit('hover-item', item.id)
  }
}

function unhoverItem(): void {
  emit('unhover-item')
}

function deleteItem(item: TreeItemData): void {
  emit('delete-item', item.id)
}

function toggleVisibility(item: TreeItemData): void {
  emit('toggle-visibility', item.id)
}

function toggleExpanded() {
  emit('toggle-expanded', props.item.id)
}

function handleKeyDown(event: KeyboardEvent): void {
  switch (event.key) {
    case 'Enter':
    case ' ':
      event.preventDefault()
      selectItem(props.item)
      break
    case 'ArrowRight':
      event.preventDefault()
      if (hasChildren.value) {
        if (!isExpanded.value) {
          toggleExpanded()
        } else {
          emit('navigate-first-child')
        }
      }
      break
    case 'ArrowLeft':
      event.preventDefault()
      if (hasChildren.value && isExpanded.value) {
        toggleExpanded()
      } else {
        emit('navigate-parent')
      }
      break
    case 'ArrowDown':
      event.preventDefault()
      emit('navigate-next')
      break
    case 'ArrowUp':
      event.preventDefault()
      emit('navigate-prev')
      break
    case 'Delete':
    case 'Backspace':
      event.preventDefault()
      deleteItem(props.item)
      break
    case 'h':
    case 'H':
      event.preventDefault()
      toggleVisibility(props.item)
      break
  }
}

function focus(): void {
  itemRow.value?.focus()
}

defineExpose({ focus })
</script>

<template>
  <div
    class="tree-item select-none"
    role="treeitem"
    :aria-expanded="hasChildren ? isExpanded : undefined"
    :aria-selected="isItemSelected"
    :aria-level="level + 1"
  >
    <div
      ref="itemRow"
      class="group flex items-center px-2 py-1.5 rounded-sm text-sm cursor-pointer transition-colors focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-1"
      :class="[
        {
          'bg-accent text-accent-foreground': isItemSelected,
          'bg-muted/50': isItemHovered && !isItemSelected,
          'hover:bg-muted/50': !isItemSelected
        }
      ]"
      :tabindex="isFocusable ? 0 : -1"
      @click="selectItem(item)"
      @mouseenter="hoverItem(item)"
      @mouseleave="unhoverItem()"
      @keydown="handleKeyDown"
    >
      <button
        v-if="hasChildren"
        type="button"
        class="h-4 w-4 mr-1 p-0 flex items-center justify-center rounded-sm hover:bg-muted transition-colors"
        :aria-label="isExpanded ? 'Collapse' : 'Expand'"
        tabindex="-1"
        @click.stop="toggleExpanded"
      >
        <Icon :name="isExpanded ? 'i-lucide-chevron-down' : 'i-lucide-chevron-right'" class="h-3 w-3" />
      </button>
      <span v-else class="w-5 mr-1" />

      <Icon :name="getItemIconName(item)" class="h-3.5 w-3.5 mr-2 text-muted-foreground" />

      <span
        v-if="shouldShowLabelIndicator(item)"
        class="w-2.5 h-2.5 rounded-sm mr-2 border border-white/20 shadow-sm"
        :style="{ backgroundColor: getLabelColor(item) }"
        :title="item.label"
      />

      <span class="flex-1 truncate text-xs font-medium">{{ item.id }}</span>

      <div class="flex items-center gap-1 opacity-0 group-hover:opacity-100 focus-within:opacity-100 transition-opacity">
        <button
          type="button"
          class="h-5 w-5 flex items-center justify-center rounded-sm hover:bg-muted transition-colors focus:outline-none focus:ring-1 focus:ring-ring"
          :title="getVisibilityTitle(item)"
          :aria-label="getVisibilityTitle(item)"
          tabindex="-1"
          @click.stop="toggleVisibility(item)"
        >
          <Icon :name="getItemVisibilityIconName(item)" class="h-3 w-3" />
        </button>
        <button
          type="button"
          class="h-5 w-5 flex items-center justify-center rounded-sm hover:bg-muted hover:text-destructive transition-colors focus:outline-none focus:ring-1 focus:ring-ring"
          :title="`Delete ${itemType}`"
          :aria-label="`Delete ${itemType}`"
          tabindex="-1"
          @click.stop="deleteItem(item)"
        >
          <Icon name="i-lucide-trash-2" class="h-3 w-3" />
        </button>
      </div>
    </div>

    <div
      v-if="hasChildren && isExpanded"
      class="pl-4 border-l border-border ml-2.5 my-1"
      role="group"
    >
      <TreeItem
        v-for="child in children"
        :key="child.id"
        :item="child"
        :level="level + 1"
        :selected-polygon-ids="selectedPolygonIds"
        :selected-polyline-ids="selectedPolylineIds"
        :hovered-id="hoveredId"
        :polygons="polygons"
        :polylines="polylines"
        :expanded-regions="expandedRegions"
        :hidden-polygon-ids="hiddenPolygonIds"
        :hidden-polyline-ids="hiddenPolylineIds"
        :is-focusable="isFocusable"
        @select-item="$emit('select-item', $event)"
        @select-polyline="$emit('select-polyline', $event)"
        @hover-item="$emit('hover-item', $event)"
        @hover-polyline="$emit('hover-polyline', $event)"
        @unhover-item="$emit('unhover-item')"
        @delete-item="$emit('delete-item', $event)"
        @toggle-visibility="$emit('toggle-visibility', $event)"
        @toggle-expanded="$emit('toggle-expanded', $event)"
        @navigate-next="$emit('navigate-next')"
        @navigate-prev="$emit('navigate-prev')"
        @navigate-parent="$emit('navigate-parent')"
        @navigate-first-child="$emit('navigate-first-child')"
      />
    </div>
  </div>
</template>
