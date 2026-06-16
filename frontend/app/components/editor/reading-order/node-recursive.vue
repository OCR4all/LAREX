<script setup lang="ts">
import { VueDraggable } from 'vue-draggable-plus'

import type {
  ReadingOrderNode,
  ReadingOrderGroup,
  OrderedGroup,
  RegionRef
} from '@/models/editor'

export interface AvailableItem {
  id: string
  label: string
  regionRef: string
  parentId?: string
}

export interface Props {
  elements: ReadingOrderNode[]
  depth?: number
  baseIndex?: number
  selectedIds: Set<string>
  expandedGroups: Set<string>
  allItems: AvailableItem[]
  sortableOptions: Record<string, unknown>
  readOnly?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  depth: 0,
  baseIndex: 1
})

const emit = defineEmits<{
  'update:elements': [elements: ReadingOrderNode[]]
  'itemClick': [event: MouseEvent, itemId: string, parentElements: ReadingOrderNode[]]
  'toggleExpanded': [groupId: string]
  'dissolveGroup': [groupId: string]
  'removeItem': [itemId: string]
  'hoverRegion': [regionId: string]
  'unhoverRegion': []
}>()

type SortableChangeEvent = {
  oldIndex?: number
  newIndex?: number
  data?: ReadingOrderNode
  clonedData?: ReadingOrderNode
}

function handleSortableAdd(event: SortableChangeEvent): void {
  if (props.readOnly) return
  const item = event.clonedData ?? event.data
  if (!item) return

  const newElements = [...props.elements]
  const insertIndex = typeof event.newIndex === 'number' ? event.newIndex : newElements.length
  newElements.splice(insertIndex, 0, item)
  emit('update:elements', newElements)
}

function handleSortableRemove(event: SortableChangeEvent): void {
  void event
}

function handleSortableUpdate(event: SortableChangeEvent): void {
  if (props.readOnly) return
  if (typeof event.oldIndex !== 'number' || typeof event.newIndex !== 'number') return

  const newElements = [...props.elements]
  const [moved] = newElements.splice(event.oldIndex, 1)
  if (moved) {
    newElements.splice(event.newIndex, 0, moved)
    emit('update:elements', newElements)
  }
}

function isGroup(node: ReadingOrderNode): node is ReadingOrderGroup {
  return 'elements' in node && Array.isArray((node as ReadingOrderGroup).elements)
}

function isOrderedGroup(node: ReadingOrderNode): node is OrderedGroup {
  return isGroup(node) && (node.kind === 'OrderedGroup' || node.kind === 'OrderedGroupIndexed')
}

function getItemCountBefore(index: number): number {
  let count = 0
  for (let i = 0; i < index && i < props.elements.length; i++) {
    const node = props.elements[i]
    if (node) {
      count += countItems(node)
    }
  }
  return count
}

function countItems(node: ReadingOrderNode): number {
  if (isGroup(node)) {
    let count = 0
    for (const child of node.elements) {
      count += countItems(child)
    }
    return count
  }
  return 1
}

function getDisplayIndex(i: number): number {
  return props.baseIndex + getItemCountBefore(i)
}

function getChildBaseIndex(i: number): number {
  return props.baseIndex + getItemCountBefore(i)
}

function isGroupExpanded(groupId: string): boolean {
  return props.expandedGroups.has(groupId)
}

function getItemLabel(node: ReadingOrderNode): string {
  if (isGroup(node)) {
    const type = isOrderedGroup(node) ? 'Ordered' : 'Unordered'
    const itemCount = countItems(node)
    return `${type} Group (${itemCount} items)`
  }

  const regionRefNode = node as RegionRef
  const item = props.allItems.find(a => a.id === regionRefNode.id)
  if (item) {
    return `${item.label} (${item.id})`
  }

  return regionRefNode.regionRef || regionRefNode.id
}

function getGroupTypeIconName(node: ReadingOrderNode): string {
  return isOrderedGroup(node) ? 'i-lucide-list-ordered' : 'i-lucide-list'
}

function getGroupElements(node: ReadingOrderNode): ReadingOrderNode[] {
  if (isGroup(node)) {
    return node.elements
  }
  return []
}

function handleItemClick(event: MouseEvent, itemId: string): void {
  emit('itemClick', event, itemId, props.elements)
}

function handleToggleExpanded(groupId: string): void {
  emit('toggleExpanded', groupId)
}

function handleDissolveGroup(groupId: string): void {
  if (props.readOnly) return
  emit('dissolveGroup', groupId)
}

function handleRemoveItem(itemId: string): void {
  if (props.readOnly) return
  emit('removeItem', itemId)
}

function handleHoverRegion(node: ReadingOrderNode): void {
  if (!isGroup(node)) {
    const ref = node as RegionRef
    emit('hoverRegion', ref.regionRef)
  }
}

function handleUnhoverRegion(): void {
  emit('unhoverRegion')
}

function handleChildElementsUpdate(node: ReadingOrderNode, newElements: ReadingOrderNode[]): void {
  if (props.readOnly) return
  const updatedElements = props.elements.map(el =>
    el.id === node.id && isGroup(el) ? { ...el, elements: newElements } : el
  )
  emit('update:elements', updatedElements)
}

function forwardItemClick(event: MouseEvent, itemId: string, parentElements: ReadingOrderNode[]): void {
  emit('itemClick', event, itemId, parentElements)
}

function forwardToggleExpanded(groupId: string): void {
  emit('toggleExpanded', groupId)
}

function forwardDissolveGroup(groupId: string): void {
  emit('dissolveGroup', groupId)
}

function forwardRemoveItem(itemId: string): void {
  emit('removeItem', itemId)
}

function forwardHoverRegion(regionId: string): void {
  emit('hoverRegion', regionId)
}

function forwardUnhoverRegion(): void {
  emit('unhoverRegion')
}
</script>

<template>
  <VueDraggable
    :key="elements.map(e => e.id).join(',')"
    :model-value="elements"
    v-bind="sortableOptions"
    class="reading-order-elements"
    :class="[
      depth === 0
        ? 'min-h-25 rounded-sm border-2 border-dashed border-default p-1'
        : 'min-h-10 rounded-sm border border-dashed border-default p-1'
    ]"
    @add="handleSortableAdd"
    @remove="handleSortableRemove"
    @update="handleSortableUpdate"
  >
    <template v-for="(node, i) in elements" :key="node.id">
      <div v-if="isGroup(node)" class="group-item mb-1">
        <div
          class="group-header flex items-center gap-1 p-2 rounded-sm cursor-pointer select-none"
          :class="{
            'bg-primary/20': selectedIds.has(node.id),
            'hover:bg-accented': !selectedIds.has(node.id)
          }"
          @click="handleItemClick($event, node.id)"
        >
          <Icon name="i-lucide-grip-vertical" class="drag-handle w-4 h-4 text-muted cursor-grab shrink-0" />

          <button
            class="p-0.5 hover:bg-accented rounded-sm shrink-0"
            @click.stop="handleToggleExpanded(node.id)"
          >
            <Icon :name="isGroupExpanded(node.id) ? 'i-lucide-chevron-down' : 'i-lucide-chevron-right'" class="w-4 h-4" />
          </button>

          <span class="text-xs font-mono text-muted w-12 shrink-0">
            {{ getDisplayIndex(i) }}-{{ getDisplayIndex(i) + countItems(node) - 1 }}
          </span>

          <Icon
            :name="getGroupTypeIconName(node)"
            class="w-4 h-4 shrink-0"
            :class="isOrderedGroup(node) ? 'text-primary' : 'text-primary/70'"
          />

          <span class="flex-1 text-sm truncate">{{ getItemLabel(node) }}</span>

          <UButton
            size="xs"
            variant="ghost"
            class="shrink-0"
            title="Dissolve group (keep items)"
            :disabled="readOnly"
            @click.stop="handleDissolveGroup(node.id)"
          >
            <Icon name="i-lucide-ungroup" class="w-3 h-3" />
          </UButton>
        </div>

        <div v-if="isGroupExpanded(node.id)" class="group-children ml-6 mt-1">
          <EditorReadingOrderNodeRecursive
            :elements="getGroupElements(node)"
            :depth="depth + 1"
            :base-index="getChildBaseIndex(i)"
            :selected-ids="selectedIds"
            :expanded-groups="expandedGroups"
            :all-items="allItems"
            :sortable-options="sortableOptions"
            :read-only="readOnly"
            @update:elements="handleChildElementsUpdate(node, $event)"
            @item-click="forwardItemClick"
            @toggle-expanded="forwardToggleExpanded"
            @dissolve-group="forwardDissolveGroup"
            @remove-item="forwardRemoveItem"
            @hover-region="forwardHoverRegion"
            @unhover-region="forwardUnhoverRegion"
          />
        </div>
      </div>

      <div
        v-else
        class="item flex items-center gap-1 p-2 rounded-sm cursor-pointer select-none mb-1"
        :class="{
          'bg-primary/20': selectedIds.has(node.id),
          'hover:bg-accented': !selectedIds.has(node.id)
        }"
        @click="handleItemClick($event, node.id)"
        @mouseenter="handleHoverRegion(node)"
        @mouseleave="handleUnhoverRegion"
      >
        <Icon name="i-lucide-grip-vertical" class="drag-handle w-4 h-4 text-muted cursor-grab shrink-0" />

        <span class="text-xs font-mono text-muted w-6 shrink-0 text-right">
          {{ getDisplayIndex(i) }}.
        </span>

        <span class="flex-1 text-sm truncate">{{ getItemLabel(node) }}</span>

        <UButton
          size="xs"
          variant="ghost"
          color="error"
          class="shrink-0"
          title="Remove from reading order"
          :disabled="readOnly"
          @click.stop="handleRemoveItem(node.id)"
        >
          <Icon name="i-lucide-trash-2" class="w-3 h-3" />
        </UButton>
      </div>
    </template>
  </VueDraggable>
</template>

<style scoped>
.group-children {
  border-left: 2px solid var(--color-smoke-200);
  padding-left: 0.5rem;
}

.dark .group-children {
  border-left-color: var(--color-smoke-700);
}
</style>
