<script setup lang="ts">
import type { LabelDefinition } from '@/models/editor/labels'
import type { RegionKind } from '@/models/editor/region'
import type { TreeItemData } from '@/components/editor/sidebar/structure-tree'
import { getTreeItemDisplayLabel, getTreeItemDisplayType } from '@/components/editor/sidebar/structure-tree'
import { getRegionColor } from '@/utils/editor/region-colors'
import { findRegionLabelDefinitionForRegion } from '@/utils/editor/page-label-mapping'

interface TreeItemProps {
  item: TreeItemData
  level?: number
  hasChildren?: boolean
  isExpanded?: boolean
  hoveredId?: string | null
  selectedPolygonIdSet?: Set<string>
  selectedPolylineIdSet?: Set<string>
  hiddenPolygonIdSet?: Set<string>
  hiddenPolylineIdSet?: Set<string>
  readOnly?: boolean
  /** Enable focus management for keyboard navigation */
  isFocusable?: boolean
}

const editorStore = useEditorStore()

const props = withDefaults(defineProps<TreeItemProps>(), {
  level: 0,
  hasChildren: false,
  isExpanded: false,
  hoveredId: null,
  selectedPolygonIdSet: () => new Set<string>(),
  selectedPolylineIdSet: () => new Set<string>(),
  hiddenPolygonIdSet: () => new Set<string>(),
  hiddenPolylineIdSet: () => new Set<string>(),
  readOnly: false,
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
}>()

const BASE_ROW_PADDING_PX = 8
const INDENT_PX = 16

const rowStyle = computed(() => ({
  paddingLeft: `${BASE_ROW_PADDING_PX + props.level * INDENT_PX}px`
}))

const itemType = computed(() => {
  if (props.item.type) {
    return props.item.type.toLowerCase()
  }
  return 'baseline'
})

const isBaselineItem = computed(() => {
  const type = props.item.type?.toUpperCase?.() ?? ''
  return type === 'BASELINE' || props.item.type === 'baseline'
})

const isItemSelected = computed<boolean>(() => {
  if (isBaselineItem.value) return props.selectedPolylineIdSet.has(props.item.id)
  return props.selectedPolygonIdSet.has(props.item.id)
})

const isItemHovered = computed<boolean>(() => {
  return props.item.id === props.hoveredId
})

function resolveRegionForItem(item: TreeItemData): { kind?: string, subtype?: string, custom?: string } {
  return {
    kind: item.regionKind,
    subtype: item.regionSubtype,
    custom: item.regionCustom
  }
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

function getItemLabel(item: TreeItemData): string {
  return getTreeItemDisplayLabel(item, findLabelDefinitionForItem(item)?.name)
}

function getItemType(item: TreeItemData): string {
  return getTreeItemDisplayType(item)
}

function getItemVisibilityIconName(item: TreeItemData): string {
  const type = item.type?.toUpperCase?.() ?? ''
  const isBaseline = type === 'BASELINE' || item.type === 'baseline'
  return (isBaseline ? props.hiddenPolylineIdSet : props.hiddenPolygonIdSet).has(item.id)
    ? 'i-lucide-eye-off'
    : 'i-lucide-eye'
}

function getVisibilityTitle(item: TreeItemData): string {
  const typeUpper = item.type?.toUpperCase?.() ?? ''
  const isBaseline = typeUpper === 'BASELINE' || item.type === 'baseline'
  const visible = !(isBaseline ? props.hiddenPolylineIdSet : props.hiddenPolygonIdSet).has(item.id)
  const type = item.type ? item.type.toLowerCase() : 'baseline'
  return visible ? `Hide ${type}` : `Show ${type}`
}

function getLabelColor(item: TreeItemData): string {
  const labelDef = findLabelDefinitionForItem(item)
  if (labelDef?.color) return labelDef.color

  const normalizedType = item.type?.toUpperCase?.() ?? ''
  if (normalizedType === 'TEXTLINE') {
    const lineLabel = editorStore.labelSet?.labels.find(label => label.scope === 'line')
    if (lineLabel?.color) return lineLabel.color
  }

  const { kind, subtype } = resolveRegionForItem(item)
  if (kind) {
    return getRegionColor(kind as RegionKind, subtype ?? undefined)
  }
  return '#666'
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
  if (props.readOnly) return
  emit('delete-item', item.id)
}

function toggleVisibility(item: TreeItemData): void {
  emit('toggle-visibility', item.id)
}

function toggleExpanded() {
  if (!props.hasChildren) return
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
      if (props.hasChildren && !props.isExpanded) {
        toggleExpanded()
      }
      break
    case 'ArrowLeft':
      event.preventDefault()
      if (props.hasChildren && props.isExpanded) {
        toggleExpanded()
      }
      break
    case 'Delete':
    case 'Backspace':
      event.preventDefault()
      if (props.readOnly) break
      deleteItem(props.item)
      break
    case 'h':
    case 'H':
      event.preventDefault()
      toggleVisibility(props.item)
      break
  }
}
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
      class="group flex items-center py-1.5 pr-2 rounded-sm text-sm cursor-pointer transition-colors focus:outline-none focus:ring-2 focus:ring-default focus:ring-offset-1"
      :class="[
        {
          'bg-accent text-accent-foreground': isItemSelected,
          'bg-muted/50': isItemHovered && !isItemSelected,
          'hover:bg-muted/50': !isItemSelected
        }
      ]"
      :style="rowStyle"
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

      <span
        class="w-3 h-3 shrink-0 rounded-full mr-2 border border-white/20 shadow-sm"
        :style="{ backgroundColor: getLabelColor(item) }"
        :title="getItemLabel(item)"
      />

      <div class="flex min-w-0 flex-1 items-baseline gap-2">
        <span class="min-w-0 flex-1 truncate text-xs font-mono" :title="item.id">
          {{ item.id }}
        </span>
        <span class="min-w-0 shrink truncate text-xs font-medium" :title="getItemLabel(item)">
          {{ getItemLabel(item) }}
        </span>
        <span class="shrink-0 text-xs text-muted" :title="getItemType(item)">
          {{ getItemType(item) }}
        </span>
      </div>

      <div class="flex items-center gap-1 opacity-0 group-hover:opacity-100 focus-within:opacity-100 transition-opacity">
        <button
          type="button"
          class="h-5 w-5 flex items-center justify-center rounded-sm hover:bg-muted transition-colors focus:outline-none focus:ring-1 focus:ring-default"
          :title="getVisibilityTitle(item)"
          :aria-label="getVisibilityTitle(item)"
          tabindex="-1"
          @click.stop="toggleVisibility(item)"
        >
          <Icon :name="getItemVisibilityIconName(item)" class="h-3 w-3" />
        </button>
        <button
          v-if="!readOnly"
          type="button"
          class="h-5 w-5 flex items-center justify-center rounded-sm hover:bg-muted hover:text-error transition-colors focus:outline-none focus:ring-1 focus:ring-default"
          :title="`Delete ${itemType}`"
          :aria-label="`Delete ${itemType}`"
          tabindex="-1"
          @click.stop="deleteItem(item)"
        >
          <Icon name="i-lucide-trash-2" class="h-3 w-3" />
        </button>
      </div>
    </div>
  </div>
</template>
