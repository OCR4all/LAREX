<script setup lang="ts">
import { useVirtualizer } from '@tanstack/vue-virtual'
import type { VNodeRef } from 'vue'
import type { Subtask, UserProfile } from '~/types/index'

const props = defineProps<{
  taskId: string
  subtasks: Subtask[]
  taskAssignees?: UserProfile[]
}>()

const emit = defineEmits<{
  'update:subtasks': [subtasks: Subtask[]]
  'add-pages': []
}>()

const toast = useToast()

const localSubtasks = ref<Subtask[]>([...props.subtasks])
const newSubtaskTitle = ref('')
const newSubtaskDescription = ref('')
const isAdding = ref(false)
const addSubtaskOpen = ref(false)
const editingId = ref<string | null>(null)
const editingTitle = ref('')
const editingDescription = ref('')

const selectionMode = ref(false)
const selectedIds = ref<Set<string>>(new Set())
const isBulkProcessing = ref(false)
const bulkDescription = ref('')
const bulkDescriptionOpen = ref(false)
const pendingToggleIds = ref<Set<string>>(new Set())

const scrollerRef = ref<HTMLElement | null>(null)

const rowVirtualizer = useVirtualizer<HTMLElement, HTMLElement>(computed(() => ({
  count: localSubtasks.value.length,
  getScrollElement: () => scrollerRef.value,
  estimateSize: () => 48,
  overscan: 6,
  getItemKey: index => localSubtasks.value[index]?.id ?? index
})))

const virtualRows = computed(() => rowVirtualizer.value.getVirtualItems().flatMap((item) => {
  const subtask = localSubtasks.value[item.index]
  return subtask ? [{ item, subtask }] : []
}))

const totalVirtualSize = computed(() => rowVirtualizer.value.getTotalSize())

const measureVirtualRow: VNodeRef = (el) => {
  rowVirtualizer.value.measureElement(el instanceof HTMLElement ? el : null)
}

function commitSubtasks(subtasks: Subtask[]) {
  localSubtasks.value = subtasks
  emit('update:subtasks', subtasks)
}

const progress = computed(() => {
  const total = localSubtasks.value.length
  const completed = localSubtasks.value.filter(subtask => subtask.completed).length
  return {
    total,
    completed,
    percentage: total > 0 ? Math.round((completed * 100) / total) : 0
  }
})

const allSelected = computed(() =>
  localSubtasks.value.length > 0 && localSubtasks.value.every(s => selectedIds.value.has(s.id))
)

const someSelected = computed(() =>
  selectedIds.value.size > 0 && !allSelected.value
)

const selectedCount = computed(() => selectedIds.value.size)

function toggleSelectionMode() {
  selectionMode.value = !selectionMode.value
  if (!selectionMode.value) {
    selectedIds.value = new Set()
  }
}

function toggleSelectAll() {
  if (allSelected.value) {
    selectedIds.value = new Set()
  } else {
    selectedIds.value = new Set(localSubtasks.value.map(subtask => subtask.id))
  }
}

function toggleSelection(subtaskId: string) {
  const next = new Set(selectedIds.value)
  if (next.has(subtaskId)) {
    next.delete(subtaskId)
  } else {
    next.add(subtaskId)
  }
  selectedIds.value = next
}

async function bulkComplete() {
  if (selectedIds.value.size === 0) return

  isBulkProcessing.value = true
  const selected = new Set(selectedIds.value)
  try {
    const response = await $fetch<{ affected: number }>(`/api/tasks/${props.taskId}/subtasks/bulk/complete`, {
      method: 'POST',
      body: { subtaskIds: Array.from(selected) }
    })
    toast.add({ title: `Completed ${response.affected} task${response.affected !== 1 ? 's' : ''}`, color: 'success' })
    selectedIds.value = new Set()
    selectionMode.value = false
    commitSubtasks(localSubtasks.value.map(subtask => selected.has(subtask.id)
      ? { ...subtask, completed: true }
      : subtask))
  } catch (err: any) {
    toast.add({ title: 'Failed to complete tasks', description: err?.data?.message, color: 'error' })
  } finally {
    isBulkProcessing.value = false
  }
}

async function bulkDelete() {
  if (selectedIds.value.size === 0) return

  isBulkProcessing.value = true
  const deletedIds = new Set(selectedIds.value)
  try {
    const response = await $fetch<{ affected: number }>(`/api/tasks/${props.taskId}/subtasks/bulk/delete`, {
      method: 'POST',
      body: { subtaskIds: Array.from(deletedIds) }
    })
    toast.add({ title: `Deleted ${response.affected} task${response.affected !== 1 ? 's' : ''}`, color: 'success' })
    selectedIds.value = new Set()
    selectionMode.value = false
    commitSubtasks(localSubtasks.value.filter(subtask => !deletedIds.has(subtask.id)))
  } catch (err: any) {
    toast.add({ title: 'Failed to delete tasks', description: err?.data?.message, color: 'error' })
  } finally {
    isBulkProcessing.value = false
  }
}

const assigneeOptions = computed(() => {
  const options = [{ label: 'Unassigned', value: '' }]
  if (props.taskAssignees) {
    for (const user of props.taskAssignees) {
      const displayName = user.firstName && user.lastName
        ? `${user.firstName} ${user.lastName}`
        : user.username
      options.push({ label: displayName, value: user.id })
    }
  }
  return options
})

async function assignSubtask(subtask: Subtask, userId: string | null) {
  try {
    const updated = await $fetch<Subtask>(`/api/tasks/${props.taskId}/subtasks/${subtask.id}/assign`, {
      method: 'PUT',
      body: { assignedUserId: userId || null }
    })
    commitSubtasks(localSubtasks.value.map(item => item.id === subtask.id ? updated : item))
  } catch (err: any) {
    toast.add({ title: 'Failed to assign task', description: err?.data?.message, color: 'error' })
  }
}

async function bulkAssign(userId: string | null) {
  if (selectedIds.value.size === 0) return

  isBulkProcessing.value = true
  const selected = new Set(selectedIds.value)
  try {
    const response = await $fetch<{ affected: number }>(`/api/tasks/${props.taskId}/subtasks/bulk/assign`, {
      method: 'POST',
      body: { subtaskIds: Array.from(selected), assignedUserId: userId || null }
    })
    toast.add({ title: `Assigned ${response.affected} task${response.affected !== 1 ? 's' : ''}`, color: 'success' })
    const assignedTo = userId
      ? props.taskAssignees?.find(user => user.id === userId) ?? null
      : null
    selectedIds.value = new Set()
    selectionMode.value = false
    commitSubtasks(localSubtasks.value.map(subtask => selected.has(subtask.id)
      ? { ...subtask, assignedUserId: userId, assignedTo }
      : subtask))
  } catch (err: any) {
    toast.add({ title: 'Failed to assign tasks', description: err?.data?.message, color: 'error' })
  } finally {
    isBulkProcessing.value = false
  }
}

async function bulkSetDescription() {
  if (selectedIds.value.size === 0) return

  isBulkProcessing.value = true
  const selected = new Set(selectedIds.value)
  const description = bulkDescription.value.trim() || null
  try {
    const response = await $fetch<{ affected: number }>(`/api/tasks/${props.taskId}/subtasks/bulk/description`, {
      method: 'POST',
      body: {
        subtaskIds: Array.from(selected),
        description
      }
    })
    toast.add({ title: `Updated ${response.affected} task${response.affected !== 1 ? 's' : ''}`, color: 'success' })
    bulkDescription.value = ''
    bulkDescriptionOpen.value = false
    selectedIds.value = new Set()
    selectionMode.value = false
    commitSubtasks(localSubtasks.value.map(subtask => selected.has(subtask.id)
      ? { ...subtask, description }
      : subtask))
  } catch (err: any) {
    toast.add({ title: 'Failed to update tasks', description: err?.data?.message, color: 'error' })
  } finally {
    isBulkProcessing.value = false
  }
}

function closeAddSubtask() {
  addSubtaskOpen.value = false
  newSubtaskTitle.value = ''
  newSubtaskDescription.value = ''
}

watch(() => props.subtasks, (newVal) => {
  localSubtasks.value = [...newVal]
  const existingIds = new Set(newVal.map(s => s.id))
  selectedIds.value = new Set([...selectedIds.value].filter(id => existingIds.has(id)))
}, { immediate: true })

async function addSubtask() {
  if (!newSubtaskTitle.value.trim()) return

  isAdding.value = true
  try {
    const created = await $fetch<Subtask>(`/api/tasks/${props.taskId}/subtasks`, {
      method: 'POST',
      body: {
        title: newSubtaskTitle.value.trim(),
        description: newSubtaskDescription.value.trim() || null
      }
    })
    closeAddSubtask()
    commitSubtasks([...localSubtasks.value, created])
  } catch (err: any) {
    toast.add({ title: 'Failed to add task', description: err?.data?.message, color: 'error' })
  } finally {
    isAdding.value = false
  }
}

async function toggleSubtask(subtask: Subtask) {
  const index = localSubtasks.value.findIndex(s => s.id === subtask.id)
  if (index === -1 || pendingToggleIds.value.has(subtask.id)) return

  const previous = localSubtasks.value[index]!
  const nextPending = new Set(pendingToggleIds.value)
  nextPending.add(subtask.id)
  pendingToggleIds.value = nextPending
  commitSubtasks(localSubtasks.value.map(item => item.id === subtask.id
    ? { ...item, completed: !item.completed }
    : item))

  try {
    const updated = await $fetch<Subtask>(`/api/tasks/${props.taskId}/subtasks/${subtask.id}/toggle`, {
      method: 'PUT'
    })
    commitSubtasks(localSubtasks.value.map(item => item.id === subtask.id ? updated : item))
  } catch (err: any) {
    commitSubtasks(localSubtasks.value.map(item => item.id === subtask.id ? previous : item))
    toast.add({ title: 'Failed to toggle task', description: err?.data?.message, color: 'error' })
  } finally {
    const next = new Set(pendingToggleIds.value)
    next.delete(subtask.id)
    pendingToggleIds.value = next
  }
}

function startEditing(subtask: Subtask) {
  editingId.value = subtask.id
  editingTitle.value = subtask.title
  editingDescription.value = subtask.description || ''
}

function cancelEditing() {
  editingId.value = null
  editingTitle.value = ''
  editingDescription.value = ''
}

async function saveEdit(subtask: Subtask) {
  const title = editingTitle.value.trim()
  const description = editingDescription.value.trim()
  const titleChanged = title.length > 0 && title !== subtask.title
  const descriptionChanged = description !== (subtask.description || '')

  if (!titleChanged && !descriptionChanged) {
    cancelEditing()
    return
  }

  try {
    const updated = await $fetch<Subtask>(`/api/tasks/${props.taskId}/subtasks/${subtask.id}`, {
      method: 'PUT',
      body: {
        title: titleChanged ? title : subtask.title,
        description: description
      }
    })
    commitSubtasks(localSubtasks.value.map(item => item.id === subtask.id ? updated : item))
    cancelEditing()
  } catch (err: any) {
    toast.add({ title: 'Failed to update task', description: err?.data?.message, color: 'error' })
  }
}

async function deleteSubtask(subtask: Subtask) {
  try {
    await $fetch(`/api/tasks/${props.taskId}/subtasks/${subtask.id}`, {
      method: 'DELETE'
    })
    commitSubtasks(localSubtasks.value.filter(item => item.id !== subtask.id))
  } catch (err: any) {
    toast.add({ title: 'Failed to delete task', description: err?.data?.message, color: 'error' })
  }
}
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center justify-between gap-3">
      <div>
        <p class="text-sm font-semibold text-highlighted">
          {{ localSubtasks.length }} task{{ localSubtasks.length === 1 ? '' : 's' }}
        </p>
        <p v-if="progress.total > 0" class="text-xs text-muted">
          {{ progress.completed }} completed
        </p>
      </div>

      <div class="flex items-center gap-2">
        <UButton
          icon="i-lucide-files"
          color="neutral"
          variant="outline"
          size="sm"
          @click="emit('add-pages')"
        >
          Add pages
        </UButton>
        <UPopover
          v-model:open="addSubtaskOpen"
          :content="{ align: 'end', sideOffset: 8 }"
        >
          <UButton
            icon="i-lucide-plus"
            color="primary"
            variant="soft"
            size="sm"
          >
            Add task
          </UButton>

          <template #content>
            <UForm
              class="w-80 max-w-[calc(100vw-2rem)] space-y-3 p-3"
              @submit="addSubtask"
            >
              <div>
                <p class="text-sm font-semibold text-highlighted">
                  Add task
                </p>
                <p class="mt-0.5 text-xs text-muted">
                  Add a general Task to this Assignment.
                </p>
              </div>

              <UFormField label="Title" required>
                <UInput
                  v-model="newSubtaskTitle"
                  placeholder="What needs to be done?"
                  size="sm"
                  class="w-full"
                  :disabled="isAdding"
                  autofocus
                />
              </UFormField>

              <UFormField label="Description">
                <UTextarea
                  v-model="newSubtaskDescription"
                  placeholder="Optional description"
                  :rows="3"
                  size="sm"
                  class="w-full"
                  :disabled="isAdding"
                />
              </UFormField>

              <div class="flex justify-end gap-2">
                <UButton
                  type="button"
                  color="neutral"
                  variant="ghost"
                  size="sm"
                  :disabled="isAdding"
                  @click="closeAddSubtask"
                >
                  Cancel
                </UButton>
                <UButton
                  type="submit"
                  icon="i-lucide-plus"
                  color="primary"
                  size="sm"
                  :loading="isAdding"
                  :disabled="!newSubtaskTitle.trim()"
                >
                  Add
                </UButton>
              </div>
            </UForm>
          </template>
        </UPopover>
      </div>
    </div>

    <div v-if="progress.total > 0" class="space-y-2">
      <div class="flex items-center justify-between text-sm">
        <span class="text-muted">Progress</span>
        <span class="font-medium">{{ progress.completed }}/{{ progress.total }} ({{ progress.percentage }}%)</span>
      </div>
      <UProgress :model-value="progress.percentage" color="primary" size="sm" />
    </div>

    <div v-if="localSubtasks.length > 0" class="flex items-center justify-between gap-2 py-2 border-b border-default">
      <div class="flex items-center gap-2">
        <UButton
          :icon="selectionMode ? 'i-lucide-x' : 'i-lucide-check-square'"
          size="xs"
          color="neutral"
          variant="ghost"
          @click="toggleSelectionMode"
        >
          {{ selectionMode ? 'Cancel' : 'Select' }}
        </UButton>

        <template v-if="selectionMode">
          <UCheckbox
            :model-value="allSelected"
            :indeterminate="someSelected"
            label="Select all"
            @update:model-value="toggleSelectAll"
          />
          <span v-if="selectedCount > 0" class="text-sm text-muted">
            {{ selectedCount }} selected
          </span>
        </template>
      </div>

      <div v-if="selectionMode && selectedCount > 0" class="flex items-center gap-1">
        <UButton
          icon="i-lucide-check"
          size="xs"
          color="success"
          variant="soft"
          :loading="isBulkProcessing"
          :disabled="isBulkProcessing"
          @click="bulkComplete"
        >
          Complete
        </UButton>
        <UPopover v-model:open="bulkDescriptionOpen">
          <UButton
            icon="i-lucide-align-left"
            size="xs"
            color="neutral"
            variant="soft"
            :loading="isBulkProcessing"
            :disabled="isBulkProcessing"
          >
            Set description
          </UButton>
          <template #content>
            <div class="p-3 w-64 space-y-2">
              <UTextarea
                v-model="bulkDescription"
                :rows="3"
                placeholder="Set a description for selected tasks"
              />
              <div class="flex justify-end gap-2">
                <UButton
                  size="xs"
                  color="neutral"
                  variant="ghost"
                  @click="() => { bulkDescriptionOpen = false }"
                >
                  Cancel
                </UButton>
                <UButton
                  size="xs"
                  color="primary"
                  :loading="isBulkProcessing"
                  :disabled="isBulkProcessing"
                  @click="bulkSetDescription"
                >
                  Apply
                </UButton>
              </div>
            </div>
          </template>
        </UPopover>
        <UDropdownMenu
          v-if="taskAssignees && taskAssignees.length > 0"
          :items="assigneeOptions.map(opt => ({ label: opt.label, onSelect: () => bulkAssign(opt.value || null) }))"
        >
          <UButton
            icon="i-lucide-user-plus"
            size="xs"
            color="info"
            variant="soft"
            :loading="isBulkProcessing"
            :disabled="isBulkProcessing"
          >
            Assign
          </UButton>
        </UDropdownMenu>
        <UButton
          icon="i-lucide-trash-2"
          size="xs"
          color="error"
          variant="soft"
          :loading="isBulkProcessing"
          :disabled="isBulkProcessing"
          @click="bulkDelete"
        >
          Delete
        </UButton>
      </div>
    </div>

    <div
      ref="scrollerRef"
      class="max-h-[60vh] overflow-x-hidden overflow-y-auto overscroll-contain pr-1 [overflow-anchor:none]"
    >
      <div
        class="relative w-full [contain:strict]"
        :style="{ height: `${totalVirtualSize}px` }"
      >
        <div
          v-for="{ item, subtask } in virtualRows"
          :key="String(item.key)"
          :ref="measureVirtualRow"
          :data-index="item.index"
          class="absolute left-0 top-0 w-full will-change-transform"
          :style="{ transform: `translateY(${item.start}px)` }"
        >
          <TaskDetailSubtaskRow
            :subtask="subtask"
            :selection-mode="selectionMode"
            :selected="selectedIds.has(subtask.id)"
            :pending="pendingToggleIds.has(subtask.id)"
            :editing="editingId === subtask.id"
            :editing-title="editingId === subtask.id ? editingTitle : ''"
            :editing-description="editingId === subtask.id ? editingDescription : ''"
            :assignee-options="assigneeOptions"
            @update:editing-title="editingTitle = $event"
            @update:editing-description="editingDescription = $event"
            @select="toggleSelection(subtask.id)"
            @toggle="toggleSubtask(subtask)"
            @edit="startEditing(subtask)"
            @cancel="cancelEditing"
            @save="saveEdit(subtask)"
            @delete="deleteSubtask(subtask)"
            @assign="assignSubtask(subtask, $event)"
          />
        </div>
      </div>
    </div>

    <div v-if="localSubtasks.length === 0" class="py-6 text-center text-sm text-muted">
      <UIcon name="i-lucide-list-checks" class="size-8 mb-2 mx-auto" />
      <p>No Tasks yet. Add a task or select pages to get started.</p>
    </div>
  </div>
</template>
