<script setup lang="ts">
import { VueDraggable } from 'vue-draggable-plus'
import {
  isGroupMeta,
  type EditableLabelDefinition,
  type GroupMeta
} from '~/composables/use-label-builder'

const { isSystem = false } = defineProps<{
  isSystem?: boolean
}>()
defineEmits(['select', 'create', 'delete'])

const {
  meta,
  filteredLabels,
  labels,
  activeLabel,
  searchQuery,
  getErrors,
  selectedLabelIds,
  selectLabelRange,
  setDefaultLabel,
  dissolveGroup,
  mergeGroups,
  moveLabel,
  moveLabelByOffset,
  moveGroup,
  moveGroupByOffset
} = useLabelBuilder()

const expandedGroups = ref<Set<string>>(new Set())
const isDragging = ref(false)
const isDraggingGroup = ref(false)
const reorderAnnouncement = ref('')
type LabelGroup = { id: string, groupMeta: GroupMeta, labels: EditableLabelDefinition[] }

const labelsWithGroups = computed<{ grouped: LabelGroup[], ungrouped: EditableLabelDefinition[] }>(() => {
  const groupMetas = labels.value.filter(isGroupMeta)
  const groupMetaById = new Map(groupMetas.map(group => [group.id, group]))
  const visibleLabels = filteredLabels.value.filter((entry): entry is EditableLabelDefinition => !isGroupMeta(entry))
  const groupedLabels = new Map<string, EditableLabelDefinition[]>()

  for (const label of visibleLabels) {
    if (label.group) {
      if (!groupedLabels.has(label.group)) {
        groupedLabels.set(label.group, [])
      }
      groupedLabels.get(label.group)!.push(label)
    }
  }

  const orderedGroupIds = [...groupedLabels.keys()]
  if (!searchQuery.value.trim()) {
    for (const groupMeta of groupMetas) {
      if (!orderedGroupIds.includes(groupMeta.id)) orderedGroupIds.push(groupMeta.id)
    }
  }

  const result: LabelGroup[] = []
  for (const groupId of orderedGroupIds) {
    const groupMeta = groupMetaById.get(groupId)
    if (!groupMeta) continue
    const groupLabels = groupedLabels.get(groupMeta.id) || []
    result.push({ id: groupMeta.id, groupMeta, labels: groupLabels })
  }

  return {
    grouped: result,
    ungrouped: visibleLabels.filter(label => !label.group)
  }
})

const isSelected = (id: string) => selectedLabelIds.value.has(id)
const isDefaultLabel = (id: string) => meta.defaultLabelId === id

const toggleDefaultLabel = (id: string) => {
  setDefaultLabel(isDefaultLabel(id) ? null : id)
}

const visibleLabelIds = computed(() => {
  const ids: string[] = []
  for (const group of labelsWithGroups.value.grouped) {
    if (isGroupExpanded(group.groupMeta.id)) {
      ids.push(...group.labels.map(label => label.id))
    }
  }
  ids.push(...labelsWithGroups.value.ungrouped.map(label => label.id))
  return ids
})

const handleSelectionClick = (event: MouseEvent, labelId: string) => {
  selectLabelRange(labelId, visibleLabelIds.value, event.shiftKey)
}

const allGroupsExpanded = computed(() => labelsWithGroups.value.grouped.length > 0
  && labelsWithGroups.value.grouped.every(group => expandedGroups.value.has(group.id)))

const isGroupExpanded = (groupId: string) => Boolean(searchQuery.value.trim()) || expandedGroups.value.has(groupId)

const toggleGroupExpanded = (groupId: string) => {
  if (searchQuery.value.trim()) return
  const next = new Set(expandedGroups.value)
  if (next.has(groupId)) {
    next.delete(groupId)
  } else {
    next.add(groupId)
  }
  expandedGroups.value = next
}

const expandAllGroups = () => {
  expandedGroups.value = new Set(labelsWithGroups.value.grouped.map(group => group.id))
}

const collapseAllGroups = () => {
  expandedGroups.value = new Set()
}

const handleDissolveGroup = (groupId: string) => {
  const next = new Set(expandedGroups.value)
  next.delete(groupId)
  expandedGroups.value = next
  dissolveGroup(groupId)
}

const handleMergeGroup = (targetGroupId: string, sourceGroupId: string) => {
  const next = new Set(expandedGroups.value)
  next.delete(sourceGroupId)
  next.add(targetGroupId)
  expandedGroups.value = next
  mergeGroups(targetGroupId, sourceGroupId)
}

const getGroupActionItems = (groupId: string, groupName: string) => {
  const mergeItems = labelsWithGroups.value.grouped
    .filter(group => group.groupMeta.id !== groupId)
    .map(group => ({
      label: `Merge into ${group.groupMeta.name}`,
      icon: 'i-lucide-merge',
      onSelect: () => handleMergeGroup(group.groupMeta.id, groupId)
    }))
  return [
    mergeItems.length > 0 ? mergeItems : [{ label: 'No other groups to merge into', disabled: true }],
    [{
      label: `Dissolve ${groupName}`,
      icon: 'i-lucide-ungroup',
      onSelect: () => handleDissolveGroup(groupId)
    }]
  ]
}

const getGroupIssueCount = (groupLabels: EditableLabelDefinition[]): number => {
  return groupLabels.reduce((count, label) => count + getErrors(label).length, 0)
}

const getGroupRegionTypeCount = (groupLabels: EditableLabelDefinition[]): number => {
  return new Set(groupLabels.map(label => label.mapping.pageXml.regionType).filter(Boolean)).size
}

type SortableChangeEvent = {
  oldIndex?: number
  newIndex?: number
  item?: HTMLElement
}

const sortableDisabled = computed(() => isSystem || searchQuery.value.trim().length > 0)

const announceReorder = async (message: string) => {
  reorderAnnouncement.value = ''
  await nextTick()
  reorderAnnouncement.value = message
}

const getLabelSiblings = (label: EditableLabelDefinition): EditableLabelDefinition[] => {
  const group = label.group ?? null
  return labels.value.filter((entry): entry is EditableLabelDefinition =>
    !isGroupMeta(entry) && (entry.group ?? null) === group
  )
}

const canMoveLabel = (label: EditableLabelDefinition, offset: -1 | 1): boolean => {
  const siblings = getLabelSiblings(label)
  const index = siblings.findIndex(entry => entry.id === label.id)
  const targetIndex = index + offset
  return !sortableDisabled.value && index >= 0 && targetIndex >= 0 && targetIndex < siblings.length
}

const handleMoveByOffset = (label: EditableLabelDefinition, offset: -1 | 1) => {
  if (!moveLabelByOffset(label.id, offset)) return
  const siblings = getLabelSiblings(label)
  const position = siblings.findIndex(entry => entry.id === label.id) + 1
  void announceReorder(`${label.name || 'Untitled'} moved to position ${position} of ${siblings.length}.`)
}

const handleMoveToGroup = (label: EditableLabelDefinition, targetGroup: string | null) => {
  if (sortableDisabled.value || (label.group ?? null) === targetGroup) return
  moveLabel(label.id, targetGroup, Number.MAX_SAFE_INTEGER)
  if (targetGroup) {
    const next = new Set(expandedGroups.value)
    next.add(targetGroup)
    expandedGroups.value = next
  }
  const destination = targetGroup ?? 'Ungrouped'
  void announceReorder(`${label.name || 'Untitled'} moved to ${destination}.`)
}

const getReorderItems = (label: EditableLabelDefinition) => {
  const destinations = [
    {
      label: 'Move to Ungrouped',
      icon: 'i-lucide-folder-minus',
      disabled: sortableDisabled.value || !label.group,
      onSelect: () => handleMoveToGroup(label, null)
    },
    ...labelsWithGroups.value.grouped
      .filter(group => group.groupMeta.id !== label.group)
      .map(group => ({
        label: `Move to ${group.groupMeta.name}`,
        icon: 'i-lucide-folder-input',
        disabled: sortableDisabled.value,
        onSelect: () => handleMoveToGroup(label, group.groupMeta.id)
      }))
  ]

  return [
    [
      {
        label: 'Move up',
        icon: 'i-lucide-arrow-up',
        disabled: !canMoveLabel(label, -1),
        onSelect: () => handleMoveByOffset(label, -1)
      },
      {
        label: 'Move down',
        icon: 'i-lucide-arrow-down',
        disabled: !canMoveLabel(label, 1),
        onSelect: () => handleMoveByOffset(label, 1)
      }
    ],
    destinations
  ]
}

const canMoveGroup = (groupId: string, offset: -1 | 1): boolean => {
  const index = labelsWithGroups.value.grouped.findIndex(group => group.id === groupId)
  const targetIndex = index + offset
  return !sortableDisabled.value && index >= 0 && targetIndex >= 0 && targetIndex < labelsWithGroups.value.grouped.length
}

const handleMoveGroupByOffset = (groupId: string, groupName: string, offset: -1 | 1) => {
  if (!moveGroupByOffset(groupId, offset)) return
  const groups = labelsWithGroups.value.grouped
  const position = groups.findIndex(entry => entry.id === groupId) + 1
  void announceReorder(`${groupName} group moved to position ${position} of ${groups.length}.`)
}

const getGroupReorderItems = (groupId: string, groupName: string) => [[
  {
    label: 'Move group up',
    icon: 'i-lucide-arrow-up',
    disabled: !canMoveGroup(groupId, -1),
    onSelect: () => handleMoveGroupByOffset(groupId, groupName, -1)
  },
  {
    label: 'Move group down',
    icon: 'i-lucide-arrow-down',
    disabled: !canMoveGroup(groupId, 1),
    onSelect: () => handleMoveGroupByOffset(groupId, groupName, 1)
  }
]]

const clearSearch = () => {
  searchQuery.value = ''
}

const getDraggedLabelId = (event: SortableChangeEvent): string | null => {
  return event.item?.dataset.labelId ?? null
}

const handleGroupSortableUpdate = (event: SortableChangeEvent) => {
  const groupId = event.item?.dataset.groupId
  if (!groupId || sortableDisabled.value || event.newIndex === undefined) return
  if (!moveGroup(groupId, event.newIndex)) return
  const groups = labelsWithGroups.value.grouped
  const group = groups.find(entry => entry.id === groupId)
  const position = groups.findIndex(entry => entry.id === groupId) + 1
  if (group) void announceReorder(`${group.groupMeta.name} group moved to position ${position} of ${groups.length}.`)
}

const handleGroupSortableStart = () => {
  isDraggingGroup.value = true
}

const handleGroupSortableEnd = () => {
  isDraggingGroup.value = false
}

const handleSortableAdd = (targetGroup: string | null, event: SortableChangeEvent) => {
  const labelId = getDraggedLabelId(event)
  if (!labelId || sortableDisabled.value) return
  moveLabel(labelId, targetGroup, event.newIndex ?? Number.MAX_SAFE_INTEGER)
  const label = labels.value.find((entry): entry is EditableLabelDefinition => !isGroupMeta(entry) && entry.id === labelId)
  if (label) void announceReorder(`${label.name || 'Untitled'} moved to ${targetGroup ?? 'Ungrouped'}.`)
}

const handleSortableUpdate = (targetGroup: string | null, event: SortableChangeEvent) => {
  const labelId = getDraggedLabelId(event)
  if (!labelId || sortableDisabled.value) return
  moveLabel(labelId, targetGroup, event.newIndex ?? 0)
  const label = labels.value.find((entry): entry is EditableLabelDefinition => !isGroupMeta(entry) && entry.id === labelId)
  if (label) {
    const siblings = getLabelSiblings(label)
    const position = siblings.findIndex(entry => entry.id === label.id) + 1
    void announceReorder(`${label.name || 'Untitled'} moved to position ${position} of ${siblings.length}.`)
  }
}

const handleSortableStart = () => {
  isDragging.value = true
}

const handleSortableEnd = () => {
  isDragging.value = false
}
</script>

<template>
  <aside data-tour="label-builder-sidebar" class="flex w-80 shrink-0 flex-col border-r border-default bg-muted/20">
    <div class="space-y-2 border-b border-default p-3">
      <UInput
        v-model="searchQuery"
        placeholder="Search labels..."
        icon="i-lucide-search"
        size="sm"
      >
        <template v-if="searchQuery" #trailing>
          <UButton
            icon="i-lucide-x"
            color="neutral"
            variant="link"
            size="xs"
            aria-label="Clear label search"
            @click="clearSearch"
          />
        </template>
      </UInput>
      <UButton
        v-if="!isSystem"
        label="Create label"
        icon="i-lucide-plus"
        variant="soft"
        size="sm"
        class="w-full justify-center"
        @click="$emit('create')"
      />
      <div v-if="searchQuery" class="px-1 text-[11px] text-muted">
        Clear the search to reorder labels.
      </div>
    </div>

    <div class="custom-scroll flex-1 space-y-3 overflow-y-auto p-3">
      <div v-if="labelsWithGroups.grouped.length > 0" class="flex items-center justify-between px-1">
        <span class="text-[11px] font-semibold uppercase tracking-wide text-muted">
          Groups
        </span>
        <div v-if="!searchQuery" class="flex items-center gap-0.5">
          <UButton
            icon="i-lucide-chevrons-down"
            size="xs"
            color="neutral"
            variant="ghost"
            :disabled="allGroupsExpanded"
            aria-label="Expand all label groups"
            @click="expandAllGroups"
          />
          <UButton
            icon="i-lucide-chevrons-up"
            size="xs"
            color="neutral"
            variant="ghost"
            :disabled="!expandedGroups.size"
            aria-label="Collapse all label groups"
            @click="collapseAllGroups"
          />
        </div>
      </div>
      <VueDraggable
        v-if="labelsWithGroups.grouped.length > 0"
        :model-value="labelsWithGroups.grouped"
        item-key="id"
        handle=".label-group-drag-handle"
        :disabled="sortableDisabled"
        :animation="180"
        ghost-class="label-group-sortable-ghost"
        chosen-class="label-sortable-chosen"
        role="list"
        aria-label="Label groups"
        class="space-y-3 rounded-lg transition-colors"
        :class="isDraggingGroup ? 'bg-primary/5 ring-1 ring-inset ring-primary/30' : ''"
        @start="handleGroupSortableStart"
        @end="handleGroupSortableEnd"
        @update="handleGroupSortableUpdate"
      >
        <div
          v-for="{ groupMeta, labels: groupLabels } in labelsWithGroups.grouped"
          :key="groupMeta.id"
          :data-group-id="groupMeta.id"
          role="listitem"
          class="rounded-lg border bg-default/60 p-2 transition-colors"
          :class="getGroupIssueCount(groupLabels) > 0 ? 'border-error/40' : 'border-default'"
        >
          <div class="flex items-center gap-1 px-1 py-1">
            <UDropdownMenu
              v-if="!isSystem"
              :items="getGroupReorderItems(groupMeta.id, groupMeta.name)"
              :content="{ align: 'start' }"
            >
              <UButton
                icon="i-lucide-grip-vertical"
                size="xs"
                color="neutral"
                variant="ghost"
                class="label-group-drag-handle shrink-0 cursor-grab text-muted active:cursor-grabbing"
                :disabled="sortableDisabled"
                :aria-label="`Reorder ${groupMeta.name} group. Use Arrow Up or Arrow Down, or press Enter for options.`"
                @click.stop
                @keydown.up.stop.prevent="handleMoveGroupByOffset(groupMeta.id, groupMeta.name, -1)"
                @keydown.down.stop.prevent="handleMoveGroupByOffset(groupMeta.id, groupMeta.name, 1)"
              />
            </UDropdownMenu>
            <UButton
              icon="i-lucide-chevron-down"
              size="xs"
              color="neutral"
              variant="ghost"
              :class="isGroupExpanded(groupMeta.id) ? 'rotate-0' : '-rotate-90'"
              :aria-label="`${isGroupExpanded(groupMeta.id) ? 'Collapse' : 'Expand'} ${groupMeta.name} group`"
              @click="toggleGroupExpanded(groupMeta.id)"
            />
            <div class="min-w-0 flex-1">
              <div class="flex items-center gap-1.5">
                <span class="truncate text-xs font-semibold text-default">
                  {{ groupMeta.name }}
                </span>
                <UBadge
                  v-if="getGroupIssueCount(groupLabels) > 0"
                  :label="String(getGroupIssueCount(groupLabels))"
                  size="xs"
                  color="error"
                  variant="subtle"
                  :aria-label="`${getGroupIssueCount(groupLabels)} issues in ${groupMeta.name}`"
                />
              </div>
              <p class="truncate text-[10px] text-muted">
                {{ groupLabels.length }} label{{ groupLabels.length === 1 ? '' : 's' }} ·
                {{ getGroupRegionTypeCount(groupLabels) }} PAGE type{{ getGroupRegionTypeCount(groupLabels) === 1 ? '' : 's' }}
              </p>
            </div>
            <div class="flex -space-x-1" aria-hidden="true">
              <span
                v-for="label in groupLabels.slice(0, 4)"
                :key="label.id"
                class="size-3 rounded-full border border-default"
                :style="{ backgroundColor: label.color }"
              />
            </div>
            <UDropdownMenu v-if="!isSystem" :items="getGroupActionItems(groupMeta.id, groupMeta.name)" :content="{ align: 'end' }">
              <UButton
                size="xs"
                color="neutral"
                variant="ghost"
                icon="i-lucide-ellipsis"
                :aria-label="`More actions for ${groupMeta.name} group`"
              />
            </UDropdownMenu>
          </div>
          <VueDraggable
            v-if="isGroupExpanded(groupMeta.id)"
            :model-value="groupLabels"
            item-key="id"
            handle=".label-drag-handle"
            :group="{ name: 'label-builder-labels' }"
            :disabled="sortableDisabled"
            :animation="150"
            ghost-class="label-sortable-ghost"
            chosen-class="label-sortable-chosen"
            role="list"
            :aria-label="`${groupMeta.name} labels`"
            class="mt-2 min-h-8 space-y-1.5 border-l-2 border-primary/30 pl-2 transition-colors"
            :class="isDragging ? 'rounded-md bg-primary/5 ring-1 ring-inset ring-primary/30' : ''"
            @start="handleSortableStart"
            @end="handleSortableEnd"
            @add="handleSortableAdd(groupMeta.id, $event)"
            @update="handleSortableUpdate(groupMeta.id, $event)"
          >
            <div
              v-for="label in groupLabels"
              :key="label.id"
              :data-label-id="label.id"
              role="listitem"
              class="group relative cursor-pointer rounded-md border px-2 py-2.5 transition-colors"
              :class="[
                activeLabel?.id === label.id ? 'border-primary/60 bg-primary/10' : 'border-default bg-default hover:bg-elevated/70',
                getErrors(label).length > 0 ? 'border-l-2 border-l-error' : '',
                isSelected(label.id) ? 'ring-2 ring-primary/50' : ''
              ]"
              @click="$emit('select', label)"
            >
              <UCheckbox
                :model-value="isSelected(label.id)"
                class="absolute left-2 top-1/2 -translate-y-1/2"
                :aria-label="`Select ${label.name || 'Untitled'}`"
                @click.stop.prevent="handleSelectionClick($event, label.id)"
              />
              <div class="flex items-center gap-2 pl-7 pr-14">
                <UDropdownMenu v-if="!isSystem" :items="getReorderItems(label)" :content="{ align: 'start' }">
                  <UButton
                    icon="i-lucide-grip-vertical"
                    color="neutral"
                    variant="ghost"
                    size="xs"
                    class="label-drag-handle shrink-0 cursor-grab text-muted active:cursor-grabbing"
                    :disabled="sortableDisabled"
                    :aria-label="`Reorder ${label.name || 'Untitled'}. Use Arrow Up or Arrow Down, or press Enter for more options.`"
                    @click.stop
                    @keydown.up.stop.prevent="handleMoveByOffset(label, -1)"
                    @keydown.down.stop.prevent="handleMoveByOffset(label, 1)"
                  />
                </UDropdownMenu>
                <span class="h-8 w-1 shrink-0 rounded-full" :style="{ backgroundColor: label.color }" />
                <button
                  type="button"
                  class="min-w-0 flex-1 text-left"
                  :aria-label="`Edit ${label.name || 'Untitled'}`"
                  @click.stop="$emit('select', label)"
                >
                  <div class="flex min-w-0 items-center gap-1.5">
                    <h3 class="truncate text-sm font-medium text-default">
                      {{ label.name || 'Untitled' }}
                    </h3>
                    <UBadge
                      v-if="isDefaultLabel(label.id)"
                      label="Default"
                      size="xs"
                      color="success"
                      variant="subtle"
                    />
                  </div>
                  <p class="truncate font-mono text-[10px] text-muted">
                    {{ label.mapping?.pageXml?.regionType }}
                  </p>
                </button>
              </div>
              <div
                v-if="!isSystem"
                class="absolute right-1.5 top-1/2 flex -translate-y-1/2 items-center gap-0.5"
              >
                <UButton
                  icon="i-lucide-circle-check"
                  size="xs"
                  :color="isDefaultLabel(label.id) ? 'success' : 'neutral'"
                  :variant="isDefaultLabel(label.id) ? 'soft' : 'ghost'"
                  :class="isDefaultLabel(label.id) ? '' : 'invisible group-focus-within:visible group-hover:visible'"
                  :aria-label="isDefaultLabel(label.id) ? `Use first label as fallback instead of ${label.name || 'Untitled'}` : `Set ${label.name || 'Untitled'} as default label`"
                  @click.stop="toggleDefaultLabel(label.id)"
                />
                <UButton
                  icon="i-lucide-trash-2"
                  size="xs"
                  color="error"
                  variant="ghost"
                  class="invisible group-focus-within:visible group-hover:visible"
                  :aria-label="`Delete ${label.name || 'Untitled'}`"
                  @click.stop="$emit('delete', label.id)"
                />
              </div>
            </div>
            <div v-if="groupLabels.length === 0" class="px-2 py-2 text-xs text-muted">
              No labels in this group
            </div>
          </VueDraggable>
        </div>
      </VueDraggable>

      <div v-if="labelsWithGroups.ungrouped.length > 0 || (!isSystem && labelsWithGroups.grouped.length > 0)">
        <div v-if="labelsWithGroups.grouped.length > 0" class="mb-2 px-1 text-xs font-semibold text-muted">
          Ungrouped
        </div>
        <VueDraggable
          :model-value="labelsWithGroups.ungrouped"
          item-key="id"
          handle=".label-drag-handle"
          :group="{ name: 'label-builder-labels' }"
          :disabled="sortableDisabled"
          :animation="150"
          ghost-class="label-sortable-ghost"
          chosen-class="label-sortable-chosen"
          role="list"
          aria-label="Ungrouped labels"
          class="min-h-8 space-y-1.5 transition-colors"
          :class="isDragging ? 'rounded-md bg-primary/5 ring-1 ring-inset ring-primary/30' : ''"
          @start="handleSortableStart"
          @end="handleSortableEnd"
          @add="handleSortableAdd(null, $event)"
          @update="handleSortableUpdate(null, $event)"
        >
          <div
            v-for="label in labelsWithGroups.ungrouped"
            :key="label.id"
            :data-label-id="label.id"
            role="listitem"
            class="group relative cursor-pointer rounded-md border px-2 py-2.5 transition-colors"
            :class="[
              activeLabel?.id === label.id ? 'border-primary/60 bg-primary/10' : 'border-default bg-default hover:bg-elevated/70',
              getErrors(label).length > 0 ? 'border-l-2 border-l-error' : '',
              isSelected(label.id) ? 'ring-2 ring-primary/50' : ''
            ]"
            @click="$emit('select', label)"
          >
            <UCheckbox
              :model-value="isSelected(label.id)"
              class="absolute left-2 top-1/2 -translate-y-1/2"
              :aria-label="`Select ${label.name || 'Untitled'}`"
              @click.stop.prevent="handleSelectionClick($event, label.id)"
            />
            <div class="flex items-center gap-2 pl-7 pr-14">
              <UDropdownMenu v-if="!isSystem" :items="getReorderItems(label)" :content="{ align: 'start' }">
                <UButton
                  icon="i-lucide-grip-vertical"
                  color="neutral"
                  variant="ghost"
                  size="xs"
                  class="label-drag-handle shrink-0 cursor-grab text-muted active:cursor-grabbing"
                  :disabled="sortableDisabled"
                  :aria-label="`Reorder ${label.name || 'Untitled'}. Use Arrow Up or Arrow Down, or press Enter for more options.`"
                  @click.stop
                  @keydown.up.stop.prevent="handleMoveByOffset(label, -1)"
                  @keydown.down.stop.prevent="handleMoveByOffset(label, 1)"
                />
              </UDropdownMenu>
              <span class="h-8 w-1 shrink-0 rounded-full" :style="{ backgroundColor: label.color }" />
              <button
                type="button"
                class="min-w-0 flex-1 text-left"
                :aria-label="`Edit ${label.name || 'Untitled'}`"
                @click.stop="$emit('select', label)"
              >
                <div class="flex min-w-0 items-center gap-1.5">
                  <h3 class="truncate text-sm font-medium text-default">
                    {{ label.name || 'Untitled' }}
                  </h3>
                  <UBadge
                    v-if="isDefaultLabel(label.id)"
                    label="Default"
                    size="xs"
                    color="success"
                    variant="subtle"
                  />
                </div>
                <p class="truncate font-mono text-[10px] text-muted">
                  {{ label.mapping?.pageXml?.regionType }}
                </p>
              </button>
            </div>
            <div
              v-if="!isSystem"
              class="absolute right-1.5 top-1/2 flex -translate-y-1/2 items-center gap-0.5"
            >
              <UButton
                icon="i-lucide-circle-check"
                size="xs"
                :color="isDefaultLabel(label.id) ? 'success' : 'neutral'"
                :variant="isDefaultLabel(label.id) ? 'soft' : 'ghost'"
                :class="isDefaultLabel(label.id) ? '' : 'invisible group-focus-within:visible group-hover:visible'"
                :aria-label="isDefaultLabel(label.id) ? `Use first label as fallback instead of ${label.name || 'Untitled'}` : `Set ${label.name || 'Untitled'} as default label`"
                @click.stop="toggleDefaultLabel(label.id)"
              />
              <UButton
                icon="i-lucide-trash-2"
                size="xs"
                color="error"
                variant="ghost"
                class="invisible group-focus-within:visible group-hover:visible"
                :aria-label="`Delete ${label.name || 'Untitled'}`"
                @click.stop="$emit('delete', label.id)"
              />
            </div>
          </div>
        </VueDraggable>
      </div>
    </div>
    <p class="sr-only" aria-live="polite" aria-atomic="true">
      {{ reorderAnnouncement }}
    </p>
  </aside>
</template>

<style scoped>
:deep(.label-sortable-ghost) {
  opacity: 0.35;
  outline: 2px solid var(--ui-primary);
  outline-offset: 2px;
}

:deep(.label-group-sortable-ghost) {
  opacity: 0.4;
  outline: 2px solid var(--ui-primary);
  outline-offset: 3px;
}

:deep(.label-sortable-chosen) {
  box-shadow: 0 8px 24px rgb(0 0 0 / 15%);
}
</style>
