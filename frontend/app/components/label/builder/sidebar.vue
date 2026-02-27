<script setup lang="ts">
import { computed, ref } from 'vue'
import { useLabelBuilder } from '../../../composables/use-label-builder'

const { isSystem = false } = defineProps<{
  isSystem?: boolean
}>()
defineEmits(['select', 'create', 'delete', 'duplicate'])

const {
  filteredLabels,
  labels,
  activeLabel,
  filters,
  searchQuery,
  getErrors,
  selectedLabelIds,
  toggleSelection,
  clearSelection,
  canGroup,
  groupSelectedLabels,
  dissolveGroup,
  moveSelectedToGroup,
  mergeGroups
} = useLabelBuilder()

const groupNameInput = ref('')
const showGroupDialog = ref(false)
const expandedGroups = ref<Set<string>>(new Set())

const labelsWithGroups = computed(() => {
  const groupMetas = labels.value.filter(l => l.isGroup)
  const groupedLabels = new Map<string, typeof filteredLabels.value>()

  for (const label of filteredLabels.value) {
    if (label.isGroup) continue
    if (label.group) {
      if (!groupedLabels.has(label.group)) {
        groupedLabels.set(label.group, [])
      }
      groupedLabels.get(label.group)!.push(label)
    }
  }

  const result = []
  for (const groupMeta of groupMetas) {
    const groupLabels = groupedLabels.get(groupMeta.id) || []
    result.push({ groupMeta, labels: groupLabels })
  }

  return {
    grouped: result,
    ungrouped: filteredLabels.value.filter(l => !l.isGroup && !l.group)
  }
})

const isSelected = (id: string) => selectedLabelIds.value.has(id)

const handleGroup = () => {
  if (!canGroup.value) return
  showGroupDialog.value = true
}

const confirmGroup = () => {
  const name = groupNameInput.value.trim() || 'Group'
  const groupId = groupSelectedLabels(name)
  if (groupId) {
    const next = new Set(expandedGroups.value)
    next.add(groupId)
    expandedGroups.value = next
  }
  groupNameInput.value = ''
  showGroupDialog.value = false
}

const cancelGroup = () => {
  groupNameInput.value = ''
  showGroupDialog.value = false
}

const isGroupExpanded = (groupId: string) => expandedGroups.value.has(groupId)

const toggleGroupExpanded = (groupId: string) => {
  const next = new Set(expandedGroups.value)
  if (next.has(groupId)) {
    next.delete(groupId)
  } else {
    next.add(groupId)
  }
  expandedGroups.value = next
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

const getMergeItems = (groupId: string) => {
  const items = labelsWithGroups.value.grouped
    .filter(group => group.groupMeta.id !== groupId)
    .map(group => ({
      label: `Merge into ${group.groupMeta.name}`,
      icon: 'i-lucide-merge',
      onSelect: () => handleMergeGroup(group.groupMeta.id, groupId)
    }))
  return items.length > 0 ? items : [{ label: 'No other groups', disabled: true }]
}

const handleMoveSelectedToGroup = (groupId: string) => {
  moveSelectedToGroup(groupId)
}
</script>

<template>
  <aside data-tour="label-builder-sidebar" class="w-80 bg-neutral-50/30 dark:bg-neutral-800/50 border-r border-neutral-200 dark:border-neutral-700 flex flex-col shrink-0">
    <div class="p-4 border-b border-neutral-200/50 dark:border-neutral-700/50 space-y-3">
      <UInput
        v-model="searchQuery"
        placeholder="Search labels..."
        autofocus
      />
      <div class="flex gap-2 text-xs font-bold text-neutral-500">
        <UCheckbox v-model="filters.region" label="Regions" />
        <UCheckbox v-model="filters.line" label="Lines" />
      </div>
      <USeparator class="my-2" />
      <UButton
        v-if="canGroup"
        block
        color="primary"
        variant="solid"
        icon="i-lucide-folder-plus"
        :disabled="isSystem"
        @click="handleGroup"
      >
        Group Selected ({{ selectedLabelIds.size }})
      </UButton>
      <div v-if="selectedLabelIds.size > 0" class="flex items-center justify-center gap-2">
        <UBadge color="primary" variant="solid">
          {{ selectedLabelIds.size }}
        </UBadge>
        <UButton
          size="xs"
          color="neutral"
          variant="ghost"
          @click="clearSelection"
        >
          Clear
        </UButton>
      </div>
    </div>

    <div class="flex-1 overflow-y-auto custom-scroll p-3 space-y-4">
      <template v-if="labelsWithGroups.grouped.length > 0">
        <div
          v-for="{ groupMeta, labels: groupLabels } in labelsWithGroups.grouped"
          :key="groupMeta.id"
          class="rounded-sm border border-neutral-200 dark:border-neutral-700 bg-neutral-50/60 dark:bg-neutral-900/40 p-2"
        >
          <div class="flex items-center gap-2 px-1 py-1">
            <UButton
              icon="i-lucide-chevron-down"
              size="xs"
              color="neutral"
              variant="ghost"
              :class="isGroupExpanded(groupMeta.id) ? 'rotate-0' : '-rotate-90'"
              @click="toggleGroupExpanded(groupMeta.id)"
            />
            <div class="flex-1 min-w-0 flex items-center gap-2">
              <span class="text-xs font-bold text-neutral-600 dark:text-neutral-300 uppercase tracking-wider truncate">
                {{ groupMeta.name }}
              </span>
              <UBadge size="xs" color="neutral" variant="soft">
                {{ groupLabels.length }}
              </UBadge>
            </div>
            <div class="flex items-center gap-1">
              <UButton
                v-if="!isSystem && selectedLabelIds.size > 0"
                size="xs"
                color="primary"
                variant="ghost"
                @click="handleMoveSelectedToGroup(groupMeta.id)"
              >
                Add Selected
              </UButton>
              <UDropdownMenu v-if="!isSystem" :items="getMergeItems(groupMeta.id)">
                <UButton
                  size="xs"
                  color="neutral"
                  variant="ghost"
                  icon="i-lucide-merge"
                />
              </UDropdownMenu>
              <UButton
                v-if="!isSystem"
                icon="i-lucide-ungroup"
                size="xs"
                color="neutral"
                variant="ghost"
                @click="handleDissolveGroup(groupMeta.id)"
              />
            </div>
          </div>
          <div v-if="isGroupExpanded(groupMeta.id)" class="mt-2 space-y-2 border-l-2 border-primary-500/40 dark:border-primary-500/30 pl-3">
            <div
              v-for="label in groupLabels"
              :key="label.id"
              class="group relative p-3 rounded-sm border cursor-pointer hover:shadow-md transition-all"
              :class="[
                activeLabel?.id === label.id ? 'bg-primary-900/10 dark:bg-primary-900/20 border-primary-500/70 dark:border-primary-500/50' : 'bg-neutral-100 dark:bg-neutral-800 border-neutral-300 dark:border-neutral-700',
                getErrors(label).length > 0 ? 'border-l-4 border-l-red-500' : '',
                isSelected(label.id) ? 'ring-2 ring-primary-500' : ''
              ]"
              @click="$emit('select', label)"
            >
              <UCheckbox
                :model-value="isSelected(label.id)"
                class="absolute left-2 top-1/2 -translate-y-1/2"
                @click.stop
                @update:model-value="toggleSelection(label.id)"
              />
              <div class="flex items-center gap-3 pl-8">
                <div class="w-8 h-8 rounded-sm flex items-center justify-center shrink-0 border border-white/10 relative" :style="{ backgroundColor: label.color + '20' }">
                  <div class="w-2 h-2 rounded-sm" :style="{ backgroundColor: label.color }" />
                  <div class="absolute -top-1 -right-1 w-3 h-3 rounded-sm text-[8px] flex items-center justify-center font-bold text-white shadow-sm" :class="label.scope === 'line' ? 'bg-emerald-600' : 'bg-primary-600'">
                    {{ label.scope === 'line' ? '≡' : '□' }}
                  </div>
                </div>
                <div class="flex-1 min-w-0">
                  <h3 class="text-sm font-semibold truncate text-neutral-900 dark:text-neutral-200 max-w-8/12">
                    {{ label.name || 'Untitled' }}
                  </h3>
                  <p class="text-[10px] text-neutral-700 dark:text-neutral-500 font-mono">
                    {{ label.scope === 'region' ? label.mapping?.pageXml?.regionType : 'TextLine' }}
                  </p>
                </div>
              </div>
              <div v-if="!isSystem" class="absolute right-2 top-2 hidden group-hover:flex items-center gap-1">
                <UButton
                  icon="i-lucide-copy-plus"
                  size="sm"
                  color="success"
                  variant="solid"
                  @click.stop="$emit('duplicate', label)"
                />
                <UButton
                  icon="i-lucide-circle-x"
                  size="sm"
                  color="error"
                  variant="solid"
                  @click.stop="$emit('delete', label.id)"
                />
              </div>
            </div>
            <div v-if="groupLabels.length === 0" class="text-xs text-neutral-500 px-2 py-2">
              No labels in this group
            </div>
          </div>
        </div>
      </template>

      <div v-if="labelsWithGroups.ungrouped.length > 0">
        <div v-if="labelsWithGroups.grouped.length > 0" class="text-xs font-bold text-neutral-500 uppercase tracking-wider mb-2 px-1">
          Ungrouped
        </div>
        <div class="space-y-2">
          <div
            v-for="label in labelsWithGroups.ungrouped"
            :key="label.id"
            class="group relative p-3 rounded-sm border cursor-pointer hover:shadow-md transition-all"
            :class="[
              activeLabel?.id === label.id ? 'bg-primary-900/10 dark:bg-primary-900/20 border-primary-500/70 dark:border-primary-500/50' : 'bg-neutral-100 dark:bg-neutral-800 border-neutral-300 dark:border-neutral-700',
              getErrors(label).length > 0 ? 'border-l-4 border-l-red-500' : '',
              isSelected(label.id) ? 'ring-2 ring-primary-500' : ''
            ]"
            @click="$emit('select', label)"
          >
            <UCheckbox
              :model-value="isSelected(label.id)"
              class="absolute left-2 top-1/2 -translate-y-1/2"
              @click.stop
              @update:model-value="toggleSelection(label.id)"
            />
            <div class="flex items-center gap-3 pl-8">
              <div class="w-8 h-8 rounded-sm flex items-center justify-center shrink-0 border border-white/10 relative" :style="{ backgroundColor: label.color + '20' }">
                <div class="w-2 h-2 rounded-sm" :style="{ backgroundColor: label.color }" />
                <div class="absolute -top-1 -right-1 w-3 h-3 rounded-sm text-[8px] flex items-center justify-center font-bold text-white shadow-sm" :class="label.scope === 'line' ? 'bg-emerald-600' : 'bg-primary-600'">
                  {{ label.scope === 'line' ? '≡' : '□' }}
                </div>
              </div>
              <div class="flex-1 min-w-0">
                <h3 class="text-sm font-semibold truncate text-neutral-900 dark:text-neutral-200 max-w-8/12">
                  {{ label.name || 'Untitled' }}
                </h3>
                <p class="text-[10px] text-neutral-700 dark:text-neutral-500 font-mono">
                  {{ label.scope === 'region' ? label.mapping?.pageXml?.regionType : 'TextLine' }}
                </p>
              </div>
            </div>
            <div v-if="!isSystem" class="absolute right-2 top-2 hidden group-hover:flex items-center gap-1">
              <UButton
                icon="i-lucide-copy-plus"
                size="sm"
                color="success"
                variant="solid"
                @click.stop="$emit('duplicate', label)"
              />
              <UButton
                icon="i-lucide-circle-x"
                size="sm"
                color="error"
                variant="solid"
                @click.stop="$emit('delete', label.id)"
              />
            </div>
          </div>
        </div>
      </div>

      <UButton
        v-if="!isSystem"
        variant="ghost"
        icon="i-mdi-tag-plus-outline"
        class="w-full"
        size="xl"
        :ui="{
          base: 'w-full py-3 border-2 border-dashed border-neutral-700 rounded-sm text-neutral-500 text-sm font-bold hover:border-primary-500/50 hover:text-primary-400 transition-colors'
        }"
        @click="$emit('create')"
      >
        Create New Label
      </UButton>

      <div v-if="showGroupDialog" class="fixed inset-0 bg-black/50 z-50 flex items-center justify-center" @click="cancelGroup">
        <UCard class="w-80 max-w-full" @click.stop>
          <template #header>
            <div class="flex items-center gap-2">
              <UIcon name="i-lucide-folder-plus" class="w-5 h-5" />
              <span class="font-semibold">Create Group</span>
            </div>
          </template>

          <UFormField label="Group name">
            <UInput
              v-model="groupNameInput"
              placeholder="Enter group name"
              autofocus
              @keyup.enter="confirmGroup"
            />
          </UFormField>

          <template #footer>
            <div class="flex justify-end gap-2">
              <UButton color="neutral" variant="ghost" @click="cancelGroup">
                Cancel
              </UButton>
              <UButton color="primary" @click="confirmGroup">
                Create
              </UButton>
            </div>
          </template>
        </UCard>
      </div>
    </div>
  </aside>
</template>
