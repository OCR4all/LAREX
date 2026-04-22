<script setup lang="ts">
import { VueDraggable } from 'vue-draggable-plus'
import type {
  ReadingOrder,
  ReadingOrderNode,
  ReadingOrderGroup,
  OrderedGroup,
  UnorderedGroup,
  RegionRef
} from '@/models/editor'
import { useEditorUiStore } from '@/stores/editor/editor.ui.store'
import { createScopedLogger } from '@/services/editor/logger-service'

const log = createScopedLogger('ReadingOrder')

export interface AvailableItem {
  id: string
  label: string
  regionRef: string
  parentId?: string
}

export interface Props {
  modelValue: ReadingOrder
  allItems: AvailableItem[]
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:modelValue': [value: ReadingOrder]
  'groupCreated': [group: ReadingOrderGroup]
  'groupDissolved': [groupId: string]
}>()

const uiStore = useEditorUiStore()
const overlayVisible = computed(() => uiStore.readingOrderOverlay.visible)
const overlaySettings = computed(() => uiStore.readingOrderOverlay)

function toggleOverlay(): void {
  uiStore.toggleReadingOrderOverlay()
}

function toggleOverlaySetting(key: 'showArrows' | 'showGroupBounds' | 'showOrderNumbers' | 'showAllRegions' | 'showLabels'): void {
  uiStore.updateReadingOrderOverlaySettings({ [key]: !overlaySettings.value[key] })
}

function handleHoverRegion(regionId: string): void {
  uiStore.setTemporaryHoverPolygonId(regionId)
}

function handleUnhoverRegion(): void {
  uiStore.setTemporaryHoverPolygonId(null)
}

const selectedIds = ref<Set<string>>(new Set())
const lastClickedId = ref<string | null>(null)
const expandedGroups = ref<Set<string>>(new Set())
let idCounter = 0
const isMac = ref(false)

onMounted(() => {
  isMac.value = typeof window !== 'undefined' && /Mac|iPod|iPhone|iPad/.test(window.navigator?.platform ?? '')
})

function isGroup(node: ReadingOrderNode): node is ReadingOrderGroup {
  return 'elements' in node && Array.isArray((node as ReadingOrderGroup).elements)
}

function isOrderedGroup(node: ReadingOrderNode): node is OrderedGroup {
  return isGroup(node) && (node.kind === 'OrderedGroup' || node.kind === 'OrderedGroupIndexed')
}

function isUnorderedGroup(node: ReadingOrderNode): node is UnorderedGroup {
  return isGroup(node) && (node.kind === 'UnorderedGroup' || node.kind === 'UnorderedGroupIndexed')
}

function isRegionRef(node: ReadingOrderNode): node is RegionRef {
  return 'regionRef' in node && !('elements' in node)
}

const includedRegionRefIds = computed(() => {
  const ids = new Set<string>()

  function collectIds(node: ReadingOrderNode): void {
    if (isGroup(node)) {
      node.elements.forEach(collectIds)
    } else {
      const regionRefNode = node as RegionRef
      ids.add(regionRefNode.regionRef)
    }
  }

  props.modelValue.root.elements.forEach(collectIds)
  return ids
})

const availableItems = computed(() => {
  return props.allItems.filter((item) => {
    if (includedRegionRefIds.value.has(item.id)) return false

    return !(!overlaySettings.value.showAllRegions && item.parentId)
  })
})

const availableItemsAsRegionRefs = computed<RegionRef[]>(() => {
  return availableItems.value.map(item => ({
    kind: 'RegionRef' as const,
    id: item.id,
    regionRef: item.regionRef
  }))
})

const hasSelection = computed(() => selectedIds.value.size >= 1)
const selectedIdsArray = computed(() => Array.from(selectedIds.value))
const accordionItems = computed(() => [
  {
    label: 'Reading Order',
    icon: 'i-lucide-list-ordered',
    value: 'reading-order',
    slot: 'reading-order'
  },
  {
    label: `Available (${availableItems.value.length})`,
    icon: 'i-lucide-plus',
    value: 'available',
    slot: 'available'
  }
])

function generateId(prefix: string = 'ro'): string {
  return `${prefix}_${Date.now()}_${++idCounter}`
}

function handleItemClick(event: MouseEvent, itemId: string, _parentElements: ReadingOrderNode[]): void {
  if (event.shiftKey && lastClickedId.value) {
    const flatList = getFlatIdList(props.modelValue.root.elements)
    const lastIndex = flatList.indexOf(lastClickedId.value)
    const currentIndex = flatList.indexOf(itemId)

    if (lastIndex !== -1 && currentIndex !== -1) {
      const start = Math.min(lastIndex, currentIndex)
      const end = Math.max(lastIndex, currentIndex)

      for (let i = start; i <= end; i++) {
        const id = flatList[i]
        if (id !== undefined) {
          selectedIds.value.add(id)
        }
      }
    }
  } else if (event.ctrlKey || event.metaKey) {
    if (selectedIds.value.has(itemId)) {
      selectedIds.value.delete(itemId)
    } else {
      selectedIds.value.add(itemId)
    }
  } else {
    selectedIds.value.clear()
    selectedIds.value.add(itemId)
  }

  lastClickedId.value = itemId
}

function getFlatIdList(elements: ReadingOrderNode[]): string[] {
  const ids: string[] = []

  function collect(nodes: ReadingOrderNode[]): void {
    for (const node of nodes) {
      ids.push(node.id)
      if (isGroup(node)) {
        collect(node.elements)
      }
    }
  }

  collect(elements)
  return ids
}

function clearSelection(): void {
  selectedIds.value.clear()
  lastClickedId.value = null
}

function toggleGroupExpanded(groupId: string): void {
  if (expandedGroups.value.has(groupId)) {
    expandedGroups.value.delete(groupId)
  } else {
    expandedGroups.value.add(groupId)
  }
}

function emitUpdate(): void {
  const seen = new Set<string>()
  const cloned = deepClone(props.modelValue)

  function dedupe(elements: ReadingOrderNode[]): ReadingOrderNode[] {
    const result: ReadingOrderNode[] = []
    for (const node of elements) {
      if (seen.has(node.id)) continue
      seen.add(node.id)

      if (isGroup(node)) {
        node.elements = dedupe(node.elements)
      }
      result.push(node)
    }
    return result
  }

  cloned.root.elements = dedupe(cloned.root.elements)
  emit('update:modelValue', cloned)
}

function handleRootElementsUpdate(newElements: ReadingOrderNode[]): void {
  const updated = deepClone(props.modelValue)
  updated.root.elements = newElements

  const seen = new Set<string>()
  function dedupe(elements: ReadingOrderNode[]): ReadingOrderNode[] {
    const result: ReadingOrderNode[] = []
    for (const node of elements) {
      const key = isRegionRef(node) ? node.regionRef : node.id
      if (seen.has(key)) continue
      seen.add(key)

      if (isGroup(node)) {
        node.elements = dedupe(node.elements)
      }
      result.push(node)
    }
    return result
  }

  updated.root.elements = dedupe(updated.root.elements)
  log.debug(`Updated: ${updated.root.elements.length} elements`)
  emit('update:modelValue', updated)
}

function groupSelectedItems(ordered: boolean = true): void {
  if (selectedIds.value.size < 1) return

  const newGroup: ReadingOrderGroup = ordered
    ? {
        kind: 'OrderedGroup',
        id: generateId('og'),
        elements: []
      }
    : {
        kind: 'UnorderedGroup',
        id: generateId('ug'),
        elements: []
      }

  const itemsToGroup: ReadingOrderNode[] = []
  const insertPos: { value: { parent: ReadingOrderNode[], index: number } | null } = { value: null }

  function findAndRemove(elements: ReadingOrderNode[]): void {
    for (let i = elements.length - 1; i >= 0; i--) {
      const node = elements[i]
      if (!node) continue

      if (selectedIds.value.has(node.id)) {
        if (insertPos.value === null) {
          insertPos.value = { parent: elements, index: i }
        }
        const removed = elements.splice(i, 1)[0]
        if (removed) {
          itemsToGroup.unshift(removed)
        }
      } else if (isGroup(node)) {
        findAndRemove(node.elements)
      }
    }
  }

  const clonedOrder = deepClone(props.modelValue)
  findAndRemove(clonedOrder.root.elements)

  const pos = insertPos.value
  if (pos !== null && itemsToGroup.length > 0) {
    newGroup.elements = itemsToGroup
    pos.parent.splice(pos.index, 0, newGroup)

    expandedGroups.value.add(newGroup.id)

    selectedIds.value.clear()
    selectedIds.value.add(newGroup.id)

    emit('update:modelValue', clonedOrder)
    emit('groupCreated', newGroup)
  }
}

function dissolveGroup(groupId: string): void {
  const clonedOrder = deepClone(props.modelValue)

  function findAndDissolve(elements: ReadingOrderNode[]): boolean {
    for (let i = 0; i < elements.length; i++) {
      const node = elements[i]
      if (!node) continue

      if (node.id === groupId && isGroup(node)) {
        const children = node.elements
        elements.splice(i, 1, ...children)
        return true
      } else if (isGroup(node)) {
        if (findAndDissolve(node.elements)) {
          return true
        }
      }
    }
    return false
  }

  if (findAndDissolve(clonedOrder.root.elements)) {
    expandedGroups.value.delete(groupId)
    selectedIds.value.delete(groupId)

    emit('update:modelValue', clonedOrder)
    emit('groupDissolved', groupId)
  }
}

function addItemToList(item: AvailableItem): void {
  log.debug('addItemToList called for:', item.id)

  if (includedRegionRefIds.value.has(item.id)) {
    log.debug('Item already in list, skipping')
    return
  }

  const newRef: RegionRef = {
    kind: 'RegionRef',
    id: item.id,
    regionRef: item.regionRef
  }

  const clonedOrder = deepClone(props.modelValue)
  clonedOrder.root.elements.push(newRef)

  log.debug('Emitting update with', clonedOrder.root.elements.length + ' elements')
  emit('update:modelValue', clonedOrder)
}

function addItemToListById(id: string): void {
  const item = props.allItems.find(i => i.id === id)
  if (item) {
    addItemToList(item)
  }
}

function removeItem(itemId: string): void {
  const clonedOrder = deepClone(props.modelValue)

  function findAndRemove(elements: ReadingOrderNode[]): boolean {
    for (let i = 0; i < elements.length; i++) {
      const node = elements[i]
      if (!node) continue

      if (node.id === itemId) {
        elements.splice(i, 1)
        return true
      } else if (isGroup(node)) {
        if (findAndRemove(node.elements)) {
          return true
        }
      }
    }
    return false
  }

  if (findAndRemove(clonedOrder.root.elements)) {
    selectedIds.value.delete(itemId)
    emit('update:modelValue', clonedOrder)
  }
}

function removeSelectedItems(): void {
  const clonedOrder = deepClone(props.modelValue)
  const idsToRemove = new Set(selectedIds.value)

  function findAndRemove(elements: ReadingOrderNode[]): void {
    for (let i = elements.length - 1; i >= 0; i--) {
      const node = elements[i]
      if (!node) continue

      if (idsToRemove.has(node.id)) {
        elements.splice(i, 1)
      } else if (isGroup(node)) {
        findAndRemove(node.elements)
      }
    }
  }

  findAndRemove(clonedOrder.root.elements)

  emit('update:modelValue', clonedOrder)
  clearSelection()
}

function deepClone<T>(obj: T): T {
  return JSON.parse(JSON.stringify(obj))
}

function getItemLabel(id: string): string {
  const item = props.allItems.find(a => a.id === id)
  if (item) {
    return item.label
  }
  return id
}

const sortableOptions = {
  animation: 150,
  fallbackOnBody: true,
  swapThreshold: 0.65,
  group: {
    name: 'reading-order',
    pull: true,
    put: true
  },
  handle: '.drag-handle',
  ghostClass: 'sortable-ghost',
  chosenClass: 'sortable-chosen',
  dragClass: 'sortable-drag'
}

const availableItemsSortableOptions = {
  animation: 150,
  group: {
    name: 'reading-order',
    pull: 'clone' as const,
    put: false
  },
  sort: false,
  handle: '.drag-handle',
  ghostClass: 'sortable-ghost',
  clone: (item: RegionRef) => {
    return {
      kind: 'RegionRef' as const,
      id: generateId('rr'),
      regionRef: item.regionRef || item.id // Use regionRef if available, fallback to id
    }
  }
}

defineExpose({
  clearSelection,
  groupSelectedItems,
  dissolveGroup,
  selectedIds: selectedIdsArray
})
</script>

<template>
  <div class="reading-order-list flex flex-col h-full bg-elevated">
    <div class="toolbar flex items-center gap-2 p-2 border-b border-default bg-muted/50">
      <UButton
        size="xs"
        :variant="overlayVisible ? 'solid' : 'ghost'"
        :color="overlayVisible ? 'primary' : 'neutral'"
        title="Show/hide reading order visualization in editor"
        @click="toggleOverlay"
      >
        <Icon :name="overlayVisible ? 'i-lucide-eye' : 'i-lucide-eye-off'" class="w-4 h-4" />
      </UButton>

      <UButton
        size="xs"
        :variant="overlaySettings.showLabels ? 'solid' : 'ghost'"
        :color="overlaySettings.showLabels ? 'primary' : 'neutral'"
        :disabled="!overlayVisible"
        title="Show/hide labels on reading order overlay"
        @click="toggleOverlaySetting('showLabels')"
      >
        <Icon name="i-lucide-tag" class="w-4 h-4" />
      </UButton>

      <div class="w-px h-4 bg-default" />

      <UButton
        size="xs"
        variant="ghost"
        :disabled="!hasSelection"
        title="Group selected as Ordered (preserves sequence)"
        @click="groupSelectedItems(true)"
      >
        <Icon name="i-lucide-list-ordered" class="w-4 h-4 mr-1" />
        <span class="text-xs">Ordered</span>
      </UButton>

      <UButton
        size="xs"
        variant="ghost"
        :disabled="!hasSelection"
        title="Group selected as Unordered (no specific sequence)"
        @click="groupSelectedItems(false)"
      >
        <Icon name="i-lucide-list" class="w-4 h-4 mr-1" />
        <span class="text-xs">Unordered</span>
      </UButton>

      <div class="flex-1" />

      <span v-if="selectedIds.size > 0" class="text-xs text-muted">
        {{ selectedIds.size }} selected
      </span>

      <UButton
        size="xs"
        variant="ghost"
        color="error"
        :disabled="selectedIds.size === 0"
        title="Remove selected items from reading order"
        @click="removeSelectedItems"
      >
        <Icon name="i-lucide-trash-2" class="w-4 h-4" />
      </UButton>
    </div>

    <div class="flex-1 flex flex-col overflow-hidden px-4">
      <UAccordion
        :items="accordionItems"
        type="multiple"
        class="flex-1 overflow-auto"
      >
        <template #reading-order>
          <div class="p-2">
            <EditorReadingOrderNodeRecursive
              :elements="modelValue.root.elements"
              :depth="0"
              :base-index="1"
              :selected-ids="selectedIds"
              :expanded-groups="expandedGroups"
              :all-items="allItems"
              :sortable-options="sortableOptions"
              @update:elements="handleRootElementsUpdate"
              @item-click="handleItemClick"
              @toggle-expanded="toggleGroupExpanded"
              @dissolve-group="dissolveGroup"
              @remove-item="removeItem"
              @hover-region="handleHoverRegion"
              @unhover-region="handleUnhoverRegion"
            />

            <div
              v-if="modelValue.root.elements.length === 0"
              class="text-center py-8 text-muted"
            >
              <p class="text-sm">
                Drag items here to add to reading order
              </p>
              <p class="text-xs mt-1">
                or use the + button on available items
              </p>
            </div>
          </div>
        </template>

        <template #available>
          <div class="p-2">
            <VueDraggable
              :model-value="availableItemsAsRegionRefs"
              v-bind="availableItemsSortableOptions"
              class="space-y-1"
            >
              <div
                v-for="regionRef in availableItemsAsRegionRefs"
                :key="regionRef.id"
                class="available-item flex items-center gap-1 p-2 rounded-sm bg-elevated hover:bg-accented cursor-pointer select-none shadow-sm"
                @mouseenter="handleHoverRegion(regionRef.regionRef)"
                @mouseleave="handleUnhoverRegion"
              >
                <Icon name="i-lucide-grip-vertical" class="drag-handle w-4 h-4 text-muted cursor-grab shrink-0" />
                <span class="flex-1 text-sm truncate" :title="`${getItemLabel(regionRef.id)} (${regionRef.id})`">{{ getItemLabel(regionRef.id) }} <span class="text-muted">({{ regionRef.id }})</span></span>
                <UButton
                  size="xs"
                  variant="ghost"
                  color="primary"
                  title="Add to reading order"
                  @click="addItemToListById(regionRef.id)"
                >
                  <Icon name="i-lucide-plus" class="w-3 h-3" />
                </UButton>
              </div>
            </VueDraggable>

            <div v-if="availableItems.length === 0" class="text-center py-4 text-muted text-sm">
              All regions are in reading order
            </div>
          </div>
        </template>
      </UAccordion>
    </div>

    <div class="p-2 border-t border-default bg-muted/50">
      <p class="text-xs text-muted">
        <strong>Tip:</strong> Hold <kbd class="px-1 py-0.5 bg-accented rounded-sm text-xs">Shift</kbd>
        and click to select a range. Use
        <kbd class="px-1 py-0.5 bg-accented rounded-sm text-xs">{{ isMac ? '⌘' : 'Ctrl' }}</kbd>+click
        to toggle individual selections.
      </p>
    </div>
  </div>
</template>

<style scoped>
.reading-order-list {
  --sortable-ghost-opacity: 0.4;
}

:deep(.sortable-ghost) {
  opacity: var(--sortable-ghost-opacity);
  background: color-mix(in srgb, var(--color-burnt-sienna-500) 20%, transparent) !important;
  border-radius: 0.375rem;
}

:deep(.sortable-chosen) {
  background: color-mix(in srgb, var(--color-burnt-sienna-500) 10%, transparent);
}

:deep(.sortable-drag) {
  opacity: 1;
  background: var(--color-cararra-50);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  border-radius: 0.375rem;
}

:deep(.dark .sortable-drag) {
  background: var(--color-cararra-900);
}

.drag-handle:hover {
  color: var(--color-cararra-500);
}

kbd {
  font-family: inherit;
}
</style>
