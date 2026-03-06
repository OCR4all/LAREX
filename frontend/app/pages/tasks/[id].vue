<script setup lang="ts">
import type { Task, TaskStatus, TaskComment, TaskActivityLog, TaskLinks, Subtask, SubtaskProgress, TaskReminder } from '~/types/index'
import { formatDistanceToNow, isPast, parseISO, format, addHours, addDays, set } from 'date-fns'
import { globalKey } from '@/utils/fetch-keys'
import type { DropdownMenuItem } from '@nuxt/ui'
import { LazyTaskSlideoverEdit, LazyUiDeleteSlideover, LazyTaskSlideoverLinkItems, LazyTaskModalConvertToSubtasks } from '#components'

const route = useRoute()
const router = useRouter()
const toast = useToast()
const overlay = useOverlay()
const { refreshTaskOverview } = useTaskOverviewRefresh()
const { refreshTaskCaches } = useDataRefresh()
const { allow } = useActionVisibility()

const taskId = computed(() => route.params.id as string)

const { data: task, pending: taskPending } = await useFetch<Task | null>(
  () => `/api/tasks/${taskId.value}`,
  {
    key: globalKey('tasks', taskId.value, 'detail'),
    default: () => null
  }
)

const workspaceId = computed(() => task.value?.workspaceId)
const taskCapabilities = useResourceCapabilities(task, 'task')

const { data: comments, refresh: refreshComments } = await useFetch<TaskComment[]>(
  () => `/api/tasks/${taskId.value}/comments`,
  {
    key: globalKey('tasks', taskId.value, 'comments'),
    default: () => []
  }
)

const activityItems = ref<TaskActivityLog[]>([])
const activityPage = ref(0)
const activitySize = 50
const activityHasMore = ref(true)
const activityLoading = ref(false)

async function loadActivity(reset = false) {
  if (activityLoading.value) return
  if (reset) {
    activityPage.value = 0
    activityItems.value = []
    activityHasMore.value = true
  }

  if (!activityHasMore.value) return

  activityLoading.value = true
  try {
    const data = await $fetch<TaskActivityLog[]>(`/api/tasks/${taskId.value}/activity`, {
      query: {
        page: activityPage.value,
        size: activitySize
      }
    })

    if (reset) {
      activityItems.value = data
    } else {
      const existingIds = new Set(activityItems.value.map(item => item.id))
      for (const item of data) {
        if (!existingIds.has(item.id)) {
          activityItems.value.push(item)
        }
      }
    }

    activityHasMore.value = data.length === activitySize
    activityPage.value += 1
  } catch (err: any) {
    toast.add({ title: 'Failed to load activity', description: err?.data?.message, color: 'error' })
  } finally {
    activityLoading.value = false
  }
}

async function refreshActivity() {
  await loadActivity(true)
}

const { data: links, refresh: refreshLinks } = await useFetch<TaskLinks>(
  () => `/api/tasks/${taskId.value}/links`,
  {
    key: globalKey('tasks', taskId.value, 'links'),
    default: () => ({ projectLinks: [], pageLinks: [] })
  }
)

const { data: subtasks } = await useFetch<Subtask[]>(
  () => `/api/tasks/${taskId.value}/subtasks`,
  {
    key: globalKey('tasks', taskId.value, 'subtasks'),
    default: () => []
  }
)

const { data: subtaskProgress } = await useFetch<SubtaskProgress>(
  () => `/api/tasks/${taskId.value}/subtasks/progress`,
  {
    key: globalKey('tasks', taskId.value, 'subtasks-progress'),
    default: () => ({ total: 0, completed: 0, percentage: 0 })
  }
)

const { data: reminders } = await useFetch<TaskReminder[]>(
  () => `/api/tasks/${taskId.value}/reminders`,
  {
    key: globalKey('tasks', taskId.value, 'reminders'),
    default: () => []
  }
)

await refreshActivity()

watch(taskId, () => {
  refreshActivity()
})

async function refreshAllSubtaskData() {
  await refreshTaskCaches(taskId.value, workspaceId.value)
}

const { user } = useUserSession()
const currentUserId = computed(() => user.value?.id || '')

const canEdit = computed(() => allow(taskCapabilities.value.canEdit))

const statusColor = (status: TaskStatus) => {
  switch (status) {
    case 'OPEN': return 'neutral'
    case 'IN_PROGRESS': return 'info'
    case 'COMPLETED': return 'success'
    case 'CANCELLED': return 'error'
  }
}

const priorityColor = (priority: Task['priority']) => {
  switch (priority) {
    case 'LOW': return 'neutral'
    case 'MEDIUM': return 'info'
    case 'HIGH': return 'warning'
    case 'URGENT': return 'error'
  }
}

const isOverdue = computed(() => {
  if (!task.value?.dueDate) return false
  if (task.value.status === 'COMPLETED' || task.value.status === 'CANCELLED') return false
  return isPast(parseISO(task.value.dueDate))
})

const formattedDueDate = computed(() => {
  if (!task.value?.dueDate) return null
  return format(parseISO(task.value.dueDate), 'PPp')
})

const dueDateRelative = computed(() => {
  if (!task.value?.dueDate) return null
  return formatDistanceToNow(parseISO(task.value.dueDate), { addSuffix: true })
})

const reminderTimeInput = ref('')
const isCreatingReminder = ref(false)

function toDatetimeLocal(date: Date) {
  return format(date, "yyyy-MM-dd'T'HH:mm")
}

function setReminderPreset(date: Date) {
  reminderTimeInput.value = toDatetimeLocal(date)
}

async function createReminder() {
  if (!canEdit.value) return
  if (!reminderTimeInput.value) return
  isCreatingReminder.value = true
  try {
    const reminderTime = new Date(reminderTimeInput.value).toISOString()
    await $fetch(`/api/tasks/${taskId.value}/reminders`, {
      method: 'POST',
      body: { reminderTime }
    })
    reminderTimeInput.value = ''
    await refreshTaskCaches(taskId.value, workspaceId.value)
    toast.add({ title: 'Reminder added', color: 'success' })
  } catch (err: any) {
    toast.add({ title: 'Failed to add reminder', description: err?.data?.message, color: 'error' })
  } finally {
    isCreatingReminder.value = false
  }
}

async function deleteReminder(reminderId: string) {
  if (!canEdit.value) return
  try {
    await $fetch(`/api/tasks/${taskId.value}/reminders/${reminderId}`, { method: 'DELETE' })
    await refreshTaskCaches(taskId.value, workspaceId.value)
    toast.add({ title: 'Reminder removed', color: 'success' })
  } catch (err: any) {
    toast.add({ title: 'Failed to remove reminder', description: err?.data?.message, color: 'error' })
  }
}

const sortedReminders = computed(() => {
  return [...(reminders.value ?? [])].sort((a, b) => parseISO(a.reminderTime).getTime() - parseISO(b.reminderTime).getTime())
})

async function updateStatus(newStatus: TaskStatus) {
  if (!allow(taskCapabilities.value.canUpdateStatus)) return
  if (!task.value || task.value.status === newStatus) return

  try {
    await $fetch(`/api/tasks/${taskId.value}/status`, {
      method: 'PUT',
      body: { status: newStatus }
    })
    await refreshTaskCaches(taskId.value, workspaceId.value)
    await refreshActivity()
    toast.add({ title: 'Status updated', color: 'success' })
  } catch (err: any) {
    toast.add({ title: 'Failed to update status', description: err?.data?.message, color: 'error' })
  }
}

function goBack() {
  navigateTo('/tasks')
}

const breadcrumbItems = computed(() => [
  {
    label: 'Home',
    icon: 'i-lucide-home',
    to: '/'
  },
  {
    label: 'Tasks',
    icon: 'i-lucide-check-square',
    to: '/tasks'
  },
  {
    label: task.value?.title || 'Task Details'
  }
])

const editSlideover = overlay.create(LazyTaskSlideoverEdit)
const deleteConfirmSlideover = overlay.create(LazyUiDeleteSlideover)
const linkItemsSlideover = overlay.create(LazyTaskSlideoverLinkItems)
const convertToSubtasksModal = overlay.create(LazyTaskModalConvertToSubtasks)

async function openEditSlideover() {
  if (!task.value || !workspaceId.value) return

  const instance = editSlideover.open({
    workspaceId: workspaceId.value,
    taskId: taskId.value,
    isAdmin: allow(taskCapabilities.value.canAssignOthers),
    currentUserId: currentUserId.value,
    onUpdated: async () => {
      await refreshTaskCaches(taskId.value, workspaceId.value)
      await refreshActivity()
    }
  })
  await instance.result
}

const linkedPageIds = computed(() => (links.value?.pageLinks ?? []).map(l => l.pageId))

const pagesByProject = computed(() => {
  const groups = new Map<string, { projectId: string; projectName: string; pages: typeof links.value.pageLinks }>()

  for (const link of links.value?.pageLinks ?? []) {
    const projectId = link.projectId || 'unknown'
    const projectName = link.projectName || 'Unknown Project'

    if (!groups.has(projectId)) {
      groups.set(projectId, { projectId, projectName, pages: [] })
    }
    groups.get(projectId)!.pages.push(link)
  }

  return Array.from(groups.values())
    .sort((a, b) => a.projectName.localeCompare(b.projectName))
    .map(group => ({
      ...group,
      pages: group.pages.slice().sort((a, b) => a.pageName.localeCompare(b.pageName))
    }))
})

async function openLinkItemsSlideover() {
  if (!canEdit.value) return
  if (!workspaceId.value) return

  const instance = linkItemsSlideover.open({
    taskId: taskId.value,
    workspaceId: workspaceId.value,
    linkedPageIds: linkedPageIds.value,
    onLinked: async (linkedPages: { pageId: string; pageName: string; projectId: string; projectName: string }[]) => {
      await refreshLinks()

      if (linkedPages.length > 0) {
        const convertInstance = convertToSubtasksModal.open({
          taskId: taskId.value,
          pages: linkedPages,
          taskAssignees: task.value?.assignedUsers ?? [],
          taskDescription: task.value?.description,
          onConverted: () => refreshAllSubtaskData()
        })
        await convertInstance.result
      }
    }
  })
  await instance.result
}

async function handleDelete() {
  if (!allow(taskCapabilities.value.canDelete)) return
  if (!task.value || !workspaceId.value) return

  const instance = deleteConfirmSlideover.open({
    name: task.value.title,
    entityType: 'Task'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  try {
    await $fetch(`/api/workspaces/${workspaceId.value}/tasks/bulk`, {
      method: 'DELETE',
      body: { taskIds: [taskId.value] }
    })
    await refreshTaskOverview(workspaceId.value)
    toast.add({ title: 'Task deleted', color: 'success' })
    await router.push('/tasks')
  } catch (e: any) {
    toast.add({ title: 'Failed to delete task', description: e?.data?.message, color: 'error' })
  }
}

const actionItems = computed<DropdownMenuItem[]>(() => {
  const items: DropdownMenuItem[] = []

  if (allow(taskCapabilities.value.canDelete)) {
    items.push({
      label: 'Delete task',
      icon: 'i-lucide-trash',
      color: 'error' as const,
      onSelect: handleDelete
    })
  }

  return items
})

const activeTab = ref('subtasks')
const tabItems = [
  { value: 'subtasks', label: 'Subtasks', icon: 'i-lucide-list-checks' },
  { value: 'comments', label: 'Comments', icon: 'i-lucide-message-square' },
  { value: 'activity', label: 'Activity', icon: 'i-lucide-activity' },
  { value: 'links', label: 'Links', icon: 'i-lucide-link' }
]
</script>

<template>
  <UDashboardPanel id="task-detail">
    <template #header>
      <UDashboardNavbar>
        <template #leading>
          <LazyUDashboardSidebarCollapse />
        </template>
        <template #right>
          <UFieldGroup v-if="task && canEdit">
            <UButton
              label="Edit"
              color="neutral"
              variant="outline"
              icon="i-lucide-pencil"
              @click="openEditSlideover"
            />
            <UDropdownMenu v-if="actionItems.length > 0" :items="actionItems" :content="{ align: 'end' }">
              <UButton
                color="neutral"
                variant="outline"
                icon="i-lucide-chevron-down"
              />
            </UDropdownMenu>
          </UFieldGroup>
        </template>
      </UDashboardNavbar>
      <UDashboardToolbar>
        <template #left>
          <UBreadcrumb :items="breadcrumbItems" />
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div v-if="taskPending" class="flex items-center justify-center py-16">
        <UIcon name="i-lucide-loader-2" class="size-8 animate-spin text-muted" />
      </div>

      <div v-else-if="!task" class="flex flex-col items-center justify-center py-16 text-muted">
        <UIcon name="i-lucide-alert-circle" class="size-12 mb-4" />
        <p>Task not found</p>
        <UButton class="mt-4" @click="goBack">Go back to tasks</UButton>
      </div>

      <div v-else class="grid grid-cols-1 lg:grid-cols-3 gap-6 p-6">
        <div class="lg:col-span-2 space-y-6">
          <div class="space-y-4">
            <div class="flex items-start justify-between gap-4">
              <h1 class="text-2xl font-semibold">{{ task.title }}</h1>
              <div class="flex items-center gap-2">
                <UBadge :color="statusColor(task.status)" variant="subtle" size="lg">
                  {{ task.status.replace('_', ' ') }}
                </UBadge>
                <UBadge :color="priorityColor(task.priority)" variant="outline" size="lg">
                  {{ task.priority }}
                </UBadge>
              </div>
            </div>

            <div v-if="task.description" class="prose prose-sm max-w-none text-muted">
              <p class="whitespace-pre-wrap">{{ task.description }}</p>
            </div>
            <p v-else class="text-sm text-muted italic">No description provided</p>
          </div>

          <div class="flex flex-wrap gap-2">
            <UButton
              v-if="allow(taskCapabilities.canUpdateStatus) && task.status !== 'IN_PROGRESS'"
              color="info"
              variant="soft"
              icon="i-lucide-play"
              @click="updateStatus('IN_PROGRESS')"
            >
              Start Progress
            </UButton>
            <UButton
              v-if="allow(taskCapabilities.canUpdateStatus) && task.status !== 'COMPLETED'"
              color="success"
              variant="soft"
              icon="i-lucide-check"
              @click="updateStatus('COMPLETED')"
            >
              Mark Complete
            </UButton>
            <UButton
              v-if="allow(taskCapabilities.canUpdateStatus) && (task.status === 'COMPLETED' || task.status === 'IN_PROGRESS')"
              color="neutral"
              variant="soft"
              icon="i-lucide-rotate-ccw"
              @click="updateStatus('OPEN')"
            >
              Reopen
            </UButton>
          </div>

          <UTabs v-model="activeTab" :items="tabItems" class="w-full" />

          <div v-if="activeTab === 'subtasks'">
            <TaskDetailSubtasks
              :task-id="taskId"
              :subtasks="subtasks ?? []"
              :progress="subtaskProgress ?? { total: 0, completed: 0, percentage: 0 }"
              :task-assignees="task?.assignedUsers ?? []"
              @refresh="refreshAllSubtaskData"
            />
          </div>

          <div v-else-if="activeTab === 'comments'">
            <TaskDetailComments
              :task-id="taskId"
              :comments="comments ?? []"
              @refresh="refreshComments"
            />
          </div>

          <div v-else-if="activeTab === 'activity'">
            <div v-if="activityLoading && activityItems.length === 0" class="flex items-center justify-center py-8">
              <UIcon name="i-lucide-loader-2" class="size-6 animate-spin text-muted" />
            </div>
            <template v-else>
              <TaskDetailActivity :activity="activityItems" />
              <div v-if="activityHasMore" class="flex justify-center mt-4">
                <UButton
                  color="neutral"
                  variant="outline"
                  :loading="activityLoading"
                  @click="loadActivity()"
                >
                  Load more
                </UButton>
              </div>
            </template>
          </div>

          <div v-else-if="activeTab === 'links'">
            <TaskDetailLinks
              :task-id="taskId"
              :workspace-id="workspaceId!"
              :links="links ?? { projectLinks: [], pageLinks: [] }"
              :task-description="task?.description"
              @refresh="refreshLinks"
              @refresh-subtasks="refreshAllSubtaskData"
            />
          </div>
        </div>

        <div class="space-y-6">
          <div class="bg-elevated/30 rounded-sm p-4 space-y-3">
            <div class="flex items-center justify-between">
              <h3 class="text-sm font-medium flex items-center gap-2">
                <UIcon name="i-lucide-link" class="size-4" />
                Linked Pages
                <UBadge v-if="links?.pageLinks?.length" size="xs" color="neutral" variant="subtle">
                  {{ links.pageLinks.length }}
                </UBadge>
              </h3>
              <UButton
                v-if="canEdit"
                icon="i-lucide-plus"
                size="xs"
                color="neutral"
                variant="ghost"
                title="Link Pages"
                @click="openLinkItemsSlideover"
              />
            </div>

            <div v-if="pagesByProject.length > 0" class="space-y-2">
              <UCollapsible
                v-for="group in pagesByProject"
                :key="group.projectId"
                :default-open="true"
              >
                <UButton
                  class="w-full justify-between"
                  color="neutral"
                  variant="ghost"
                  size="xs"
                >
                  <template #leading>
                    <UIcon name="i-lucide-folder" class="size-3.5 text-muted" />
                  </template>
                  <span class="truncate">{{ group.projectName }}</span>
                  <template #trailing>
                    <UBadge size="xs" color="neutral" variant="subtle">{{ group.pages.length }}</UBadge>
                  </template>
                </UButton>
                <template #content>
                  <div class="pl-4 space-y-0.5 mt-1">
                    <NuxtLink
                      v-for="page in group.pages.slice(0, 3)"
                      :key="page.id"
                      :to="`/project/${page.projectId}`"
                      class="flex items-center gap-2 py-1.5 px-2 -mx-2 rounded-sm hover:bg-default transition-colors"
                    >
                      <UIcon name="i-lucide-file" class="size-3.5 text-muted shrink-0" />
                      <span class="text-sm truncate">{{ page.pageName }}</span>
                    </NuxtLink>
                    <button
                      v-if="group.pages.length > 3"
                      class="text-xs text-primary hover:underline pl-5 py-1"
                      @click="activeTab = 'links'"
                    >
                      +{{ group.pages.length - 3 }} more
                    </button>
                  </div>
                </template>
              </UCollapsible>
            </div>

            <p v-else class="text-sm text-muted">
              No linked pages
            </p>
          </div>

          <div class="bg-elevated/30 rounded-sm p-4 space-y-3">
            <h3 class="text-sm font-medium">Assignees</h3>
            <div v-if="task.assignedUsers?.length" class="space-y-2">
              <div
                v-for="assignee in task.assignedUsers"
                :key="assignee.id"
                class="flex items-center gap-2"
              >
                <UAvatar :alt="assignee.username" size="sm" />
                <div class="flex-1 min-w-0">
                  <p class="text-sm font-medium truncate">
                    {{ assignee.firstName && assignee.lastName ? `${assignee.firstName} ${assignee.lastName}` : assignee.username }}
                  </p>
                </div>
              </div>
            </div>
            <p v-else class="text-sm text-muted">No assignees</p>
          </div>

          <div class="bg-elevated/30 rounded-sm p-4 space-y-2">
            <h3 class="text-sm font-medium">Due Date</h3>
            <div v-if="task.dueDate" class="space-y-1">
              <p class="text-sm" :class="isOverdue ? 'text-error font-medium' : ''">
                {{ formattedDueDate }}
              </p>
              <p class="text-xs text-muted" :class="isOverdue ? 'text-error' : ''">
                {{ dueDateRelative }}
                <span v-if="isOverdue" class="ml-1">(overdue)</span>
              </p>
            </div>
            <p v-else class="text-sm text-muted">No due date set</p>
          </div>

          <div class="bg-elevated/30 rounded-sm p-4 space-y-3">
            <h3 class="text-sm font-medium">Reminders</h3>

            <div class="space-y-2">
              <UiDateTimePicker
                v-model="reminderTimeInput"
                size="sm"
                :disabled="isCreatingReminder || !canEdit"
                placeholder="Pick reminder date and time"
              />
              <div class="flex flex-wrap gap-2">
                <UButton
                  size="xs"
                  color="neutral"
                  variant="soft"
                  @click="setReminderPreset(addHours(new Date(), 1))"
                >
                  In 1 hour
                </UButton>
                <UButton
                  size="xs"
                  color="neutral"
                  variant="soft"
                  @click="setReminderPreset(set(addDays(new Date(), 1), { hours: 9, minutes: 0, seconds: 0, milliseconds: 0 }))"
                >
                  Tomorrow 9am
                </UButton>
                <UButton
                  v-if="task.dueDate"
                  size="xs"
                  color="neutral"
                  variant="soft"
                  @click="setReminderPreset(addDays(parseISO(task.dueDate), -1))"
                >
                  1 day before due
                </UButton>
              </div>
              <UButton
                size="xs"
                color="primary"
                :loading="isCreatingReminder"
                :disabled="!reminderTimeInput || !canEdit"
                @click="createReminder"
              >
                Add reminder
              </UButton>
            </div>

            <div v-if="sortedReminders.length === 0" class="text-sm text-muted">
              No reminders yet
            </div>
            <div v-else class="space-y-2">
              <div
                v-for="reminder in sortedReminders"
                :key="reminder.id"
                class="flex items-center justify-between gap-2"
              >
                <div class="text-sm">
                  {{ format(parseISO(reminder.reminderTime), 'PPp') }}
                </div>
                <UButton
                  v-if="canEdit"
                  size="xs"
                  color="neutral"
                  variant="ghost"
                  icon="i-lucide-x"
                  @click="deleteReminder(reminder.id)"
                />
              </div>
            </div>
          </div>

          <div class="bg-elevated/30 rounded-sm p-4 space-y-2">
            <h3 class="text-sm font-medium">Created by</h3>
            <div v-if="task.createdBy" class="flex items-center gap-2">
              <UAvatar :alt="task.createdBy.username" size="sm" />
              <div class="flex-1 min-w-0">
                <p class="text-sm truncate">
                  {{ task.createdBy.firstName && task.createdBy.lastName ? `${task.createdBy.firstName} ${task.createdBy.lastName}` : task.createdBy.username }}
                </p>
              </div>
            </div>
          </div>

          <div class="bg-elevated/30 rounded-sm p-4 space-y-2">
            <h3 class="text-sm font-medium">Timestamps</h3>
            <div class="text-sm text-muted space-y-1">
              <p>Created: {{ format(parseISO(task.created), 'PPp') }}</p>
              <p>Updated: {{ format(parseISO(task.updated), 'PPp') }}</p>
              <p v-if="task.completedAt">
                Completed: {{ format(parseISO(task.completedAt), 'PPp') }}
              </p>
            </div>
          </div>
        </div>
      </div>
    </template>
  </UDashboardPanel>
</template>
