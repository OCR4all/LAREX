<script setup lang="ts">
import type { Subtask, SubtaskProgress, UserProfile } from '~/types/index'
import { VueDraggable } from 'vue-draggable-plus'

const props = defineProps<{
  taskId: string
  subtasks: Subtask[]
  progress: SubtaskProgress
  taskAssignees?: UserProfile[]
}>()

const emit = defineEmits<{
  refresh: []
}>()

const toast = useToast()

const localSubtasks = ref<Subtask[]>([])
const newSubtaskTitle = ref('')
const newSubtaskDescription = ref('')
const isAdding = ref(false)
const editingId = ref<string | null>(null)
const editingTitle = ref('')
const editingDescription = ref('')

const selectionMode = ref(false)
const selectedIds = ref<Set<string>>(new Set())
const isBulkProcessing = ref(false)
const bulkDescription = ref('')
const bulkDescriptionOpen = ref(false)

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
    selectedIds.value.clear()
  }
}

function toggleSelectAll() {
  if (allSelected.value) {
    selectedIds.value.clear()
  } else {
    localSubtasks.value.forEach(s => selectedIds.value.add(s.id))
  }
}

function toggleSelection(subtaskId: string) {
  if (selectedIds.value.has(subtaskId)) {
    selectedIds.value.delete(subtaskId)
  } else {
    selectedIds.value.add(subtaskId)
  }
}

async function bulkComplete() {
  if (selectedIds.value.size === 0) return

  isBulkProcessing.value = true
  try {
    const response = await $fetch<{ affected: number }>(`/api/tasks/${props.taskId}/subtasks/bulk/complete`, {
      method: 'POST',
      body: { subtaskIds: Array.from(selectedIds.value) }
    })
    toast.add({ title: `Completed ${response.affected} subtask${response.affected !== 1 ? 's' : ''}`, color: 'success' })
    selectedIds.value.clear()
    selectionMode.value = false
    emit('refresh')
  } catch (err: any) {
    toast.add({ title: 'Failed to complete subtasks', description: err?.data?.message, color: 'error' })
  } finally {
    isBulkProcessing.value = false
  }
}

async function bulkDelete() {
  if (selectedIds.value.size === 0) return

  isBulkProcessing.value = true
  try {
    const response = await $fetch<{ affected: number }>(`/api/tasks/${props.taskId}/subtasks/bulk/delete`, {
      method: 'POST',
      body: { subtaskIds: Array.from(selectedIds.value) }
    })
    toast.add({ title: `Deleted ${response.affected} subtask${response.affected !== 1 ? 's' : ''}`, color: 'success' })
    selectedIds.value.clear()
    selectionMode.value = false
    emit('refresh')
  } catch (err: any) {
    toast.add({ title: 'Failed to delete subtasks', description: err?.data?.message, color: 'error' })
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
    await $fetch(`/api/tasks/${props.taskId}/subtasks/${subtask.id}/assign`, {
      method: 'PUT',
      body: { assignedUserId: userId || null }
    })
    emit('refresh')
  } catch (err: any) {
    toast.add({ title: 'Failed to assign subtask', description: err?.data?.message, color: 'error' })
  }
}

async function bulkAssign(userId: string | null) {
  if (selectedIds.value.size === 0) return

  isBulkProcessing.value = true
  try {
    const response = await $fetch<{ affected: number }>(`/api/tasks/${props.taskId}/subtasks/bulk/assign`, {
      method: 'POST',
      body: { subtaskIds: Array.from(selectedIds.value), assignedUserId: userId || null }
    })
    toast.add({ title: `Assigned ${response.affected} subtask${response.affected !== 1 ? 's' : ''}`, color: 'success' })
    selectedIds.value.clear()
    selectionMode.value = false
    emit('refresh')
  } catch (err: any) {
    toast.add({ title: 'Failed to assign subtasks', description: err?.data?.message, color: 'error' })
  } finally {
    isBulkProcessing.value = false
  }
}

async function bulkSetDescription() {
  if (selectedIds.value.size === 0) return

  isBulkProcessing.value = true
  try {
    const response = await $fetch<{ affected: number }>(`/api/tasks/${props.taskId}/subtasks/bulk/description`, {
      method: 'POST',
      body: {
        subtaskIds: Array.from(selectedIds.value),
        description: bulkDescription.value.trim() || null
      }
    })
    toast.add({ title: `Updated ${response.affected} subtask${response.affected !== 1 ? 's' : ''}`, color: 'success' })
    bulkDescription.value = ''
    bulkDescriptionOpen.value = false
    selectedIds.value.clear()
    selectionMode.value = false
    emit('refresh')
  } catch (err: any) {
    toast.add({ title: 'Failed to update subtasks', description: err?.data?.message, color: 'error' })
  } finally {
    isBulkProcessing.value = false
  }
}

function getAssigneeName(subtask: Subtask): string {
  if (!subtask.assignedTo) return 'Unassigned'
  const user = subtask.assignedTo
  return user.firstName && user.lastName
    ? `${user.firstName} ${user.lastName}`
    : user.username
}

function getDisplayDescription(subtask: Subtask) {
  return subtask.description || subtask.taskDescription || null
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
    await $fetch(`/api/tasks/${props.taskId}/subtasks`, {
      method: 'POST',
      body: {
        title: newSubtaskTitle.value.trim(),
        description: newSubtaskDescription.value.trim() || null
      }
    })
    newSubtaskTitle.value = ''
    newSubtaskDescription.value = ''
    emit('refresh')
  } catch (err: any) {
    toast.add({ title: 'Failed to add subtask', description: err?.data?.message, color: 'error' })
  } finally {
    isAdding.value = false
  }
}

async function toggleSubtask(subtask: Subtask) {
  const index = localSubtasks.value.findIndex(s => s.id === subtask.id)
  if (index !== -1) {
    const current = localSubtasks.value[index]
    if (current) {
      localSubtasks.value[index] = { ...current, completed: !current.completed }
    }
  }

  try {
    await $fetch(`/api/tasks/${props.taskId}/subtasks/${subtask.id}/toggle`, {
      method: 'PUT'
    })
    emit('refresh')
  } catch (err: any) {
    if (index !== -1) {
      const current = localSubtasks.value[index]
      if (current) {
        localSubtasks.value[index] = { ...current, completed: !current.completed }
      }
    }
    toast.add({ title: 'Failed to toggle subtask', description: err?.data?.message, color: 'error' })
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
    await $fetch(`/api/tasks/${props.taskId}/subtasks/${subtask.id}`, {
      method: 'PUT',
      body: {
        title: titleChanged ? title : subtask.title,
        description: description
      }
    })
    emit('refresh')
    cancelEditing()
  } catch (err: any) {
    toast.add({ title: 'Failed to update subtask', description: err?.data?.message, color: 'error' })
  }
}

async function deleteSubtask(subtask: Subtask) {
  try {
    await $fetch(`/api/tasks/${props.taskId}/subtasks/${subtask.id}`, {
      method: 'DELETE'
    })
    emit('refresh')
  } catch (err: any) {
    toast.add({ title: 'Failed to delete subtask', description: err?.data?.message, color: 'error' })
  }
}

async function onDragEnd() {
  const subtaskIds = localSubtasks.value.map(s => s.id)

  try {
    await $fetch(`/api/tasks/${props.taskId}/subtasks/reorder`, {
      method: 'PUT',
      body: { subtaskIds }
    })
  } catch (err: any) {
    localSubtasks.value = [...props.subtasks]
    toast.add({ title: 'Failed to reorder subtasks', description: err?.data?.message, color: 'error' })
  }
}
</script>

<template>
  <div class="space-y-4">
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
                placeholder="Set a description for selected subtasks"
              />
              <div class="flex justify-end gap-2">
                <UButton
                  size="xs"
                  color="neutral"
                  variant="ghost"
                  @click="bulkDescriptionOpen = false"
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

    <VueDraggable
      v-model="localSubtasks"
      item-key="id"
      handle=".drag-handle"
      :animation="150"
      ghost-class="opacity-50"
      :disabled="selectionMode"
      @end="onDragEnd"
    >
      <div
        v-for="subtask in localSubtasks"
        :key="subtask.id"
        class="group flex items-center gap-2 py-2 px-2 -mx-2 rounded-sm hover:bg-elevated/30"
        :class="{ 'bg-primary/5': selectionMode && selectedIds.has(subtask.id) }"
      >
        <UCheckbox
          v-if="selectionMode"
          :model-value="selectedIds.has(subtask.id)"
          @update:model-value="toggleSelection(subtask.id)"
        />

        <UIcon
          v-if="!selectionMode"
          name="i-lucide-grip-vertical"
          class="drag-handle size-4 text-muted cursor-grab opacity-0 group-hover:opacity-100 transition-opacity"
        />

        <UCheckbox
          :model-value="subtask.completed"
          :disabled="selectionMode"
          @update:model-value="toggleSubtask(subtask)"
        />

        <div v-if="editingId === subtask.id" class="flex-1 flex flex-col gap-2">
          <UInput
            v-model="editingTitle"
            size="sm"
            class="flex-1"
            autofocus
            @keyup.enter="saveEdit(subtask)"
            @keyup.escape="cancelEditing"
          />
          <UTextarea
            v-model="editingDescription"
            size="sm"
            :rows="2"
            placeholder="Add a description"
          />
          <div class="flex items-center gap-2">
            <UButton
              icon="i-lucide-check"
              color="success"
              variant="ghost"
              size="xs"
              @click="saveEdit(subtask)"
            />
            <UButton
              icon="i-lucide-x"
              color="neutral"
              variant="ghost"
              size="xs"
              @click="cancelEditing"
            />
          </div>
        </div>

        <div
          v-else
          class="flex-1 min-w-0 flex flex-col gap-0.5"
        >
          <div class="flex items-center gap-2 min-w-0">
            <span
              class="text-sm cursor-pointer truncate"
              :class="{ 'line-through text-muted': subtask.completed }"
              @dblclick="startEditing(subtask)"
            >
              {{ subtask.title }}
            </span>

            <NuxtLink
              v-if="subtask.pageId && subtask.pageName"
              :to="`/project/${subtask.projectId}`"
              class="shrink-0"
              @click.stop
            >
              <UBadge
                color="neutral"
                variant="subtle"
                size="xs"
                class="cursor-pointer hover:bg-elevated"
              >
                <UIcon name="i-lucide-file" class="size-3 mr-1" />
                {{ subtask.pageName }}
              </UBadge>
            </NuxtLink>
          </div>
          <p v-if="getDisplayDescription(subtask)" class="text-xs text-muted truncate">
            {{ getDisplayDescription(subtask) }}
          </p>
        </div>

        <div v-if="!selectionMode && taskAssignees && taskAssignees.length > 0" class="shrink-0">
          <UDropdownMenu
            :items="assigneeOptions.map(opt => ({ label: opt.label, onSelect: () => assignSubtask(subtask, opt.value || null) }))"
          >
            <UButton
              size="xs"
              color="neutral"
              variant="ghost"
              class="gap-1"
            >
              <UAvatar
                v-if="subtask.assignedTo"
                :alt="getAssigneeName(subtask)"
                size="2xs"
              />
              <UIcon v-else name="i-lucide-user" class="size-3 text-muted" />
              <span class="text-xs text-muted max-w-20 truncate hidden sm:inline">
                {{ subtask.assignedTo ? getAssigneeName(subtask) : '' }}
              </span>
            </UButton>
          </UDropdownMenu>
        </div>

        <div v-if="editingId !== subtask.id && !selectionMode" class="opacity-0 group-hover:opacity-100 transition-opacity flex items-center gap-1">
          <UButton
            icon="i-lucide-pencil"
            color="neutral"
            variant="ghost"
            size="xs"
            @click="startEditing(subtask)"
          />
          <UButton
            icon="i-lucide-trash-2"
            color="error"
            variant="ghost"
            size="xs"
            @click="deleteSubtask(subtask)"
          />
        </div>
      </div>
    </VueDraggable>

    <UForm class="space-y-2" @submit="addSubtask">
      <div class="flex items-center gap-2">
        <UInput
          v-model="newSubtaskTitle"
          placeholder="Add a subtask..."
          size="sm"
          class="flex-1"
          :disabled="isAdding"
        />
        <UButton
          type="submit"
          icon="i-lucide-plus"
          color="primary"
          variant="soft"
          size="sm"
          :loading="isAdding"
          :disabled="!newSubtaskTitle.trim()"
        >
          Add
        </UButton>
      </div>
      <UTextarea
        v-model="newSubtaskDescription"
        placeholder="Optional description"
        :rows="2"
        size="sm"
        :disabled="isAdding"
      />
    </UForm>

    <div v-if="localSubtasks.length === 0 && !newSubtaskTitle" class="text-center py-6 text-sm text-muted">
      <UIcon name="i-lucide-list-checks" class="size-8 mb-2 mx-auto" />
      <p>No subtasks yet. Add one above.</p>
    </div>
  </div>
</template>
