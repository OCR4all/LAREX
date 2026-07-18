<script setup lang="ts">
import * as z from 'zod'
import type { FormSubmitEvent } from '@nuxt/ui'
import type { Task, TaskPriority, TaskStatus, UserProfile, WorkspaceMember } from '~/types/index'

const props = defineProps<{
  workspaceId: string
  task?: Task
  taskId?: string
  isAdmin: boolean
  currentUserId: string
  onUpdated?: () => void | Promise<void>
}>()

const emit = defineEmits<{
  close: []
}>()

const toast = useToast()
const { refreshTaskOverview } = useTaskOverviewRefresh()

const isLoading = ref(false)
const isSubmitting = ref(false)

const formId = useId()

const taskLocal = ref<Task | null>(props.task ?? null)

const schema = z.object({
  title: z.preprocess(
    value => typeof value === 'string' ? value : '',
    z.string().trim().min(1, { error: 'Title is required' }).max(255, { error: 'Title is too long' })
  ),
  description: z.string().optional(),
  status: z.enum(['OPEN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED']),
  priority: z.enum(['LOW', 'MEDIUM', 'HIGH', 'URGENT']),
  dueDate: z.string().optional(),
  syncLinkedPageStates: z.boolean()
})

type Schema = z.output<typeof schema>

const state = reactive<Partial<Schema>>({
  title: '',
  description: '',
  status: 'OPEN',
  priority: 'MEDIUM',
  dueDate: '',
  syncLinkedPageStates: false
})

const assignedUserIds = ref<string[]>([])
const assignedUsers = ref<UserProfile[]>([])

const statusItems = [
  { label: 'Open', value: 'OPEN' as const },
  { label: 'In progress', value: 'IN_PROGRESS' as const },
  { label: 'Completed', value: 'COMPLETED' as const },
  { label: 'Cancelled', value: 'CANCELLED' as const }
]

const priorityItems = [
  { label: 'Low', value: 'LOW' as const },
  { label: 'Medium', value: 'MEDIUM' as const },
  { label: 'High', value: 'HIGH' as const },
  { label: 'Urgent', value: 'URGENT' as const }
]

const isEditing = computed(() => !!taskLocal.value?.id)
const titleText = computed(() => isEditing.value ? 'Edit Task' : 'Create Task')
const canEditTaskDetails = computed(() => props.isAdmin || taskLocal.value?.createdByUserId === props.currentUserId)

const { data: workspaceMembers } = useFetch<WorkspaceMember[]>(
  () => `/api/workspaces/${props.workspaceId}/members/accepted`,
  {
    default: () => []
  }
)

const availableMembers = computed(() => {
  return workspaceMembers.value.map(m => ({
    id: m.userId,
    username: m.username || m.userId,
    email: m.email,
    firstName: m.firstName,
    lastName: m.lastName,
    avatar: m.avatar,
    role: m.role,
    displayName: m.displayName
  }))
})

const searchQuery = ref('')
const searchResults = computed(() => {
  if (!props.isAdmin) return []
  if (!searchQuery.value || searchQuery.value.length < 1) return []

  const query = searchQuery.value.toLowerCase()
  return availableMembers.value
    .filter((m) => {
      if (assignedUserIds.value.includes(m.id)) return false
      return (
        m.username?.toLowerCase().includes(query)
        || m.email?.toLowerCase().includes(query)
        || m.firstName?.toLowerCase().includes(query)
        || m.lastName?.toLowerCase().includes(query)
        || m.displayName?.toLowerCase().includes(query)
      )
    })
    .slice(0, 10)
})

function addAssignee(user: UserProfile & { role?: string, displayName?: string }) {
  assignedUserIds.value = [...assignedUserIds.value, user.id]
  assignedUsers.value = [...assignedUsers.value, user]
  searchQuery.value = ''
}

function removeAssignee(userId: string) {
  assignedUserIds.value = assignedUserIds.value.filter(id => id !== userId)
  assignedUsers.value = assignedUsers.value.filter(u => u.id !== userId)
}

function applyTaskToState(task: Task) {
  state.title = task.title
  state.description = task.description || ''
  state.status = task.status
  state.priority = task.priority
  state.dueDate = task.dueDate ? task.dueDate.slice(0, 16) : ''
  state.syncLinkedPageStates = task.syncLinkedPageStates ?? false

  assignedUserIds.value = [...(task.assignedUserIds || [])]
  assignedUsers.value = [...(task.assignedUsers || [])]
}

async function loadTaskById(taskId: string) {
  isLoading.value = true
  try {
    const task = await $fetch<Task>(`/api/tasks/${taskId}`)
    taskLocal.value = task
    applyTaskToState(task)
  } catch (error: unknown) {
    toast.add({ title: 'Failed to load task', description: extractApiErrorMessage(error, 'An error occurred'), color: 'error' })
  } finally {
    isLoading.value = false
  }
}

watch(() => props.task, (t) => {
  if (t) {
    taskLocal.value = t
    applyTaskToState(t)
  }
}, { immediate: true })

watch(() => props.taskId, (taskId) => {
  if (taskId) {
    loadTaskById(taskId)
  }
}, { immediate: true })

onMounted(() => {
  if (!props.isAdmin && !props.task && !props.taskId) {
    assignedUserIds.value = [props.currentUserId]
    assignedUsers.value = []
  }
})

function toLocalDateTime(value: string | undefined) {
  if (!value) return null
  const trimmed = value.trim()
  if (!trimmed) return null
  if (trimmed.length === 16) return `${trimmed}:00`
  return trimmed
}

async function onSubmit(event: FormSubmitEvent<Schema>) {
  isSubmitting.value = true
  try {
    const dueDate = toLocalDateTime(event.data.dueDate)
    const payload = {
      title: event.data.title.trim(),
      description: event.data.description?.trim() || null,
      priority: event.data.priority as TaskPriority,
      dueDate,
      assignedUserIds: props.isAdmin ? assignedUserIds.value : [props.currentUserId],
      syncLinkedPageStates: props.isAdmin ? event.data.syncLinkedPageStates : false
    }

    if (!taskLocal.value?.id) {
      const created = await $fetch<Task>(`/api/workspaces/${props.workspaceId}/tasks`, {
        method: 'POST',
        body: payload
      })
      if (event.data.status !== 'OPEN') {
        await $fetch<Task>(`/api/tasks/${created.id}/status`, {
          method: 'PUT',
          body: { status: event.data.status as TaskStatus }
        })
      }
      taskLocal.value = created
      applyTaskToState(created)
      await refreshTaskOverview(props.workspaceId)
      toast.add({ title: 'Task created', color: 'success' })
      await props.onUpdated?.()
      emit('close')
      return
    }

    const taskId = taskLocal.value.id
    const updated = canEditTaskDetails.value
      ? await $fetch<Task>(`/api/tasks/${taskId}`, {
          method: 'PUT',
          body: {
            title: payload.title,
            description: payload.description,
            priority: payload.priority,
            dueDate: payload.dueDate,
            syncLinkedPageStates: payload.syncLinkedPageStates
          }
        })
      : taskLocal.value

    if (props.isAdmin) {
      await $fetch<Task>(`/api/tasks/${taskId}/assignees`, {
        method: 'PUT',
        body: { assignedUserIds: assignedUserIds.value }
      })
    }

    if (event.data.status !== updated.status) {
      await $fetch<Task>(`/api/tasks/${taskId}/status`, {
        method: 'PUT',
        body: { status: event.data.status as TaskStatus }
      })
    }

    await refreshTaskOverview(props.workspaceId)
    toast.add({ title: 'Task updated', color: 'success' })
    await props.onUpdated?.()
    emit('close')
  } catch (error: unknown) {
    toast.add({ title: 'Failed to save task', description: extractApiErrorMessage(error, 'An error occurred'), color: 'error' })
  } finally {
    isSubmitting.value = false
  }
}

function getDisplayName(user: UserProfile & { displayName?: string }) {
  if (user.displayName) return user.displayName
  if (user.firstName && user.lastName) return `${user.firstName} ${user.lastName}`
  return user.username
}
</script>

<template>
  <UiResponsiveSlideover
    data-tour="task-form"
    :close="{ onClick: () => emit('close') }"
  >
    <template #header>
      <UiSlideoverHeader
        :title="titleText"
        :icon="isEditing ? 'i-lucide-pencil' : 'i-lucide-clipboard-plus'"
        :description="isEditing ? 'Update task details, scheduling, and assignment.' : 'Define the task, schedule, and responsible workspace members.'"
      />
    </template>

    <template #body>
      <div v-if="isLoading" class="flex items-center justify-center py-8">
        <UIcon name="i-lucide-loader-2" class="size-6 animate-spin text-muted" />
      </div>

      <UForm
        v-else
        :id="formId"
        :schema="schema"
        :state="state"
        class="space-y-4"
        @submit="onSubmit"
      >
        <UiSlideoverSection
          title="Task Details"
          description="Core information, status, priority, and due date."
          icon="i-lucide-clipboard-list"
        >
          <div class="space-y-4">
            <UFormField label="Title" name="title" required>
              <UInput
                v-model="state.title"
                data-tour="task-form-title"
                :disabled="isSubmitting || !canEditTaskDetails"
                placeholder="Task title"
              />
            </UFormField>

            <UFormField label="Description" name="description">
              <UTextarea
                v-model="state.description"
                :disabled="isSubmitting || !canEditTaskDetails"
                :rows="4"
                placeholder="Optional details"
              />
            </UFormField>

            <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <UFormField label="Status" name="status">
                <USelect
                  v-model="state.status"
                  data-tour="task-form-status"
                  :items="statusItems"
                  value-key="value"
                  :disabled="isSubmitting"
                />
              </UFormField>
              <UFormField label="Priority" name="priority">
                <USelect
                  v-model="state.priority"
                  :items="priorityItems"
                  value-key="value"
                  :disabled="isSubmitting || !canEditTaskDetails"
                />
              </UFormField>
            </div>

            <UFormField label="Due date" name="dueDate">
              <UiDateTimePicker
                v-model="state.dueDate"
                :disabled="isSubmitting || !canEditTaskDetails"
                placeholder="Select due date"
              />
            </UFormField>

            <UFormField
              v-if="isAdmin"
              label="Linked page states"
              name="syncLinkedPageStates"
              hint="Keep explicitly linked pages aligned with this task's status."
            >
              <USwitch
                v-model="state.syncLinkedPageStates"
                label="Synchronize page workflow states"
                :disabled="isSubmitting"
              />
            </UFormField>
          </div>
        </UiSlideoverSection>

        <UiSlideoverSection
          title="Assignees"
          description="Choose the workspace members responsible for this task."
          icon="i-lucide-users"
        >
          <UFormField
            data-tour="task-form-assignees"
            name="assignees"
            :hint="isAdmin ? 'Search workspace members' : 'Only you'"
          >
            <div class="space-y-2">
              <div v-if="isAdmin" class="relative">
                <UInput
                  v-model="searchQuery"
                  placeholder="Search workspace members..."
                  icon="i-lucide-search"
                  :disabled="isSubmitting"
                />
                <div
                  v-if="searchResults.length > 0"
                  class="absolute z-10 mt-1 max-h-60 w-full overflow-auto rounded-sm border border-default bg-default shadow-lg"
                >
                  <button
                    v-for="user in searchResults"
                    :key="user.id"
                    type="button"
                    class="flex w-full items-center gap-3 px-4 py-2 text-left hover:bg-elevated/50"
                    @click="addAssignee(user)"
                  >
                    <AppAvatar
                      :seed="user.id"
                      :src="user.avatar"
                      :alt="getDisplayName(user)"
                      size="sm"
                    />
                    <div class="min-w-0 flex-1">
                      <p class="truncate text-sm font-medium">
                        {{ getDisplayName(user) }}
                      </p>
                      <p class="truncate text-xs text-muted">
                        {{ user.email || user.username }}
                        <span v-if="user.role" class="ml-1 text-xs opacity-60">({{ user.role }})</span>
                      </p>
                    </div>
                  </button>
                </div>
              </div>

              <div class="flex flex-wrap gap-2">
                <template v-if="assignedUsers.length > 0">
                  <div
                    v-for="user in assignedUsers"
                    :key="user.id"
                    class="inline-flex items-center gap-2 rounded-sm border border-default bg-elevated/50 px-2 py-1"
                  >
                    <AppAvatar
                      :seed="user.id"
                      :src="user.avatar"
                      :alt="getDisplayName(user)"
                      size="xs"
                    />
                    <span class="text-xs">{{ user.username }}</span>
                    <UButton
                      v-if="isAdmin"
                      icon="i-lucide-x"
                      size="xs"
                      color="neutral"
                      variant="ghost"
                      :disabled="isSubmitting"
                      @click="removeAssignee(user.id)"
                    />
                  </div>
                </template>
                <template v-else>
                  <div
                    v-if="isAdmin && assignedUserIds.length > 0"
                    class="inline-flex items-center gap-2 rounded-sm border border-default bg-elevated/50 px-2 py-1"
                  >
                    <UIcon name="i-lucide-users" class="size-4 text-muted" />
                    <span class="text-xs">{{ assignedUserIds.length }} selected</span>
                  </div>
                  <div
                    v-else
                    class="inline-flex items-center gap-2 rounded-sm border border-default bg-elevated/50 px-2 py-1"
                  >
                    <UIcon name="i-lucide-user" class="size-4 text-muted" />
                    <span class="text-xs">You</span>
                  </div>
                </template>
              </div>
            </div>
          </UFormField>
        </UiSlideoverSection>
      </UForm>
    </template>

    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton
          data-tour="task-form-close"
          color="neutral"
          variant="outline"
          :disabled="isSubmitting"
          @click="emit('close')"
        >
          Close
        </UButton>
        <UButton
          type="submit"
          :form="formId"
          data-tour="task-form-save"
          icon="i-lucide-save"
          :loading="isSubmitting"
          :disabled="isSubmitting"
        >
          Save
        </UButton>
      </div>
    </template>
  </UiResponsiveSlideover>
</template>
