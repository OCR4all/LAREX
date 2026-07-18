<script setup lang="ts">
import type { Task, TaskStatus, TaskComment, TaskActivityLog, TaskLinks, Subtask, SubtaskProgress, TaskReminder, UserProfile, WorkspaceMember } from '~/types/index'
import { formatDistanceToNow, isPast, parseISO, format, addHours, addDays, set } from 'date-fns'
import type { BreadcrumbItem, DropdownMenuItem } from '@nuxt/ui'
import { LazyTaskSlideoverEdit, LazyUiDeleteSlideover, LazyTaskSlideoverLinkItems, LazyTaskModalConvertToSubtasks } from '#components'

const route = useRoute()
const router = useRouter()
const toast = useToast()
const overlay = useOverlay()
const { refreshTaskOverview } = useTaskOverviewRefresh()
const { refreshTaskCaches, refreshProjectCaches } = useDataRefresh()
const { allow } = useActionVisibility()

const taskId = computed(() => route.params.id as string)

const { data: task, pending: taskPending } = await useFetch<Task | null>(
  () => `/api/tasks/${taskId.value}`,
  {
    key: globalKey('tasks', taskId.value, 'detail'),
    default: () => null
  }
)
const isInitialTaskLoad = computed(() => taskPending.value && task.value === null)

const workspaceId = computed(() => task.value?.workspaceId)
const taskCapabilities = useResourceCapabilities(task, 'task')

const {
  data: acceptedWorkspaceMembers,
  status: acceptedWorkspaceMembersStatus,
  execute: loadAcceptedWorkspaceMembers
} = useFetch<WorkspaceMember[]>(
  () => `/api/workspaces/${workspaceId.value}/members/accepted`,
  {
    key: computed(() => workspaceId.value
      ? wsKey(workspaceId.value, 'members', 'accepted')
      : globalKey('pending', 'members', 'accepted')),
    default: () => [],
    immediate: false
  }
)

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
const canAssignOthers = computed(() => allow(taskCapabilities.value.canAssignOthers))

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

const dueDateEditorOpen = ref(false)
const dueDateDraft = ref('')
const isSavingDueDate = ref(false)

const assigneeEditorOpen = ref(false)
const assigneeQuery = ref('')
const assigneeDraftIds = ref<string[]>([])
const isSavingAssignees = ref(false)

const assignableUsers = computed<UserProfile[]>(() => (acceptedWorkspaceMembers.value ?? []).map(member => ({
  id: member.userId,
  username: member.username || member.userId,
  email: member.email,
  firstName: member.firstName,
  lastName: member.lastName,
  avatar: member.avatar
})))

const filteredAssignableUsers = computed(() => {
  const query = assigneeQuery.value.trim().toLowerCase()
  if (!query) return assignableUsers.value

  return assignableUsers.value.filter((member) => {
    const displayName = getUserDisplayName(member).toLowerCase()
    return displayName.includes(query)
      || member.username.toLowerCase().includes(query)
      || member.email?.toLowerCase().includes(query)
  })
})

watch(assigneeEditorOpen, async (open) => {
  if (!open) return
  assigneeDraftIds.value = [...(task.value?.assignedUserIds ?? [])]
  assigneeQuery.value = ''
  await loadAcceptedWorkspaceMembers()
})

function getUserDisplayName(user: UserProfile): string {
  const fullName = [user.firstName, user.lastName].filter(Boolean).join(' ')
  return fullName || user.username
}

function openDueDateEditor() {
  dueDateDraft.value = task.value?.dueDate?.slice(0, 16) ?? ''
  dueDateEditorOpen.value = true
}

function closeDueDateEditor() {
  dueDateEditorOpen.value = false
  dueDateDraft.value = task.value?.dueDate?.slice(0, 16) ?? ''
}

function toggleAssigneeDraft(userId: string) {
  assigneeDraftIds.value = assigneeDraftIds.value.includes(userId)
    ? assigneeDraftIds.value.filter(id => id !== userId)
    : [...assigneeDraftIds.value, userId]
}

function closeAssigneeEditor() {
  assigneeEditorOpen.value = false
}

async function refreshAfterInlineTaskUpdate() {
  await refreshTaskCaches(taskId.value, workspaceId.value)
  await refreshActivity()
}

async function persistDueDate(dueDate: string | null) {
  if (!canEdit.value) return

  isSavingDueDate.value = true
  try {
    const updated = await $fetch<Task>(`/api/tasks/${taskId.value}/due-date`, {
      method: 'PUT',
      body: { dueDate }
    })
    task.value = updated
    dueDateEditorOpen.value = false
    await refreshAfterInlineTaskUpdate()
    toast.add({ title: dueDate ? 'Due date updated' : 'Due date removed', color: 'success' })
  } catch (err: any) {
    toast.add({ title: 'Failed to update due date', description: err?.data?.message, color: 'error' })
  } finally {
    isSavingDueDate.value = false
  }
}

async function saveDueDate() {
  const value = dueDateDraft.value.trim()
  await persistDueDate(value ? `${value}:00` : null)
}

async function removeDueDate() {
  await persistDueDate(null)
}

async function saveAssignees() {
  if (!canAssignOthers.value || assigneeDraftIds.value.length === 0) return

  isSavingAssignees.value = true
  try {
    const updated = await $fetch<Task>(`/api/tasks/${taskId.value}/assignees`, {
      method: 'PUT',
      body: { assignedUserIds: assigneeDraftIds.value }
    })
    task.value = updated
    assigneeEditorOpen.value = false
    await refreshAfterInlineTaskUpdate()
    toast.add({ title: 'Assignees updated', color: 'success' })
  } catch (err: any) {
    toast.add({ title: 'Failed to update assignees', description: err?.data?.message, color: 'error' })
  } finally {
    isSavingAssignees.value = false
  }
}

const reminderTimeInput = ref('')
const isCreatingReminder = ref(false)

function toDatetimeLocal(date: Date) {
  return format(date, 'yyyy-MM-dd\'T\'HH:mm')
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

const updatingTaskStatus = ref<TaskStatus | null>(null)

async function updateStatus(newStatus: TaskStatus) {
  if (!allow(taskCapabilities.value.canUpdateStatus)) return
  if (!task.value || task.value.status === newStatus) return
  if (updatingTaskStatus.value) return

  updatingTaskStatus.value = newStatus
  try {
    const updated = await $fetch<Task>(`/api/tasks/${taskId.value}/status`, {
      method: 'PUT',
      body: { status: newStatus }
    })
    task.value = updated
    await refreshTaskCaches(taskId.value, workspaceId.value)
    await refreshLinkedProjectCaches()
    await refreshActivity()
    toast.add({ title: 'Status updated', color: 'success' })
  } catch (err: any) {
    toast.add({ title: 'Failed to update status', description: err?.data?.message, color: 'error' })
  } finally {
    updatingTaskStatus.value = null
  }
}

async function refreshLinkedProjectCaches() {
  if (!workspaceId.value) return
  const projectIds = [...new Set((links.value?.pageLinks ?? []).map(link => link.projectId).filter(Boolean))]
  await Promise.all(projectIds.map(projectId => refreshProjectCaches(workspaceId.value, projectId)))
}

function goBack() {
  navigateTo('/tasks')
}

const breadcrumbItems = computed<BreadcrumbItem[]>(() => [
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
      await refreshLinkedProjectCaches()
      await refreshActivity()
    }
  })
  await instance.result
}

const linkedPageIds = computed(() => (links.value?.pageLinks ?? []).map(l => l.pageId))

const pagesByProject = computed(() => {
  const groups = new Map<string, { projectId: string, projectName: string, pages: typeof links.value.pageLinks }>()

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
    onLinked: async (linkedPages: { pageId: string, pageName: string, projectId: string, projectName: string }[]) => {
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
        <template #title>
          <UBreadcrumb :items="breadcrumbItems" />
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
    </template>

    <template #body>
      <div v-if="isInitialTaskLoad" class="flex items-center justify-center py-16">
        <UIcon name="i-lucide-loader-2" class="size-8 animate-spin text-muted" />
      </div>

      <div v-else-if="!task" class="flex flex-col items-center justify-center py-16 text-muted">
        <UIcon name="i-lucide-alert-circle" class="size-12 mb-4" />
        <p>Task not found</p>
        <UButton class="mt-4" @click="goBack">
          Go back to tasks
        </UButton>
      </div>

      <div v-else class="grid grid-cols-1 gap-4 p-4 lg:grid-cols-3 lg:p-5">
        <div class="space-y-4 lg:col-span-2">
          <section class="px-1 py-2">
            <div class="flex items-start justify-between gap-4">
              <div class="flex min-w-0 items-start gap-3">
                <span class="flex size-8 shrink-0 items-center justify-center text-muted">
                  <UIcon name="i-lucide-clipboard-check" class="size-5" />
                </span>
                <h1 class="min-w-0 text-2xl font-semibold leading-8 tracking-tight text-highlighted lg:text-3xl lg:leading-9">
                  {{ task.title }}
                </h1>
              </div>
              <div class="flex flex-wrap items-center justify-end gap-2">
                <UBadge :color="statusColor(task.status)" variant="subtle" size="sm">
                  {{ task.status.replace('_', ' ') }}
                </UBadge>
                <UBadge :color="priorityColor(task.priority)" variant="outline" size="sm">
                  {{ task.priority }}
                </UBadge>
                <UBadge
                  v-if="task.syncLinkedPageStates"
                  color="info"
                  variant="subtle"
                  size="sm"
                  icon="i-lucide-refresh-cw"
                >
                  Page sync
                </UBadge>
              </div>
            </div>

            <div v-if="task.description" class="mt-3 max-w-none text-sm leading-6 text-muted">
              <p class="whitespace-pre-wrap">
                {{ task.description }}
              </p>
            </div>
            <p v-else class="mt-3 text-sm italic text-muted">
              No description provided
            </p>
            <div class="mt-4 flex flex-wrap gap-2">
              <UButton
                v-if="allow(taskCapabilities.canUpdateStatus) && task.status !== 'IN_PROGRESS'"
                color="info"
                variant="soft"
                size="sm"
                icon="i-lucide-play"
                :loading="updatingTaskStatus === 'IN_PROGRESS'"
                :disabled="updatingTaskStatus !== null"
                @click="updateStatus('IN_PROGRESS')"
              >
                Start Progress
              </UButton>
              <UButton
                v-if="allow(taskCapabilities.canUpdateStatus) && task.status !== 'COMPLETED'"
                color="success"
                variant="soft"
                size="sm"
                icon="i-lucide-check"
                :loading="updatingTaskStatus === 'COMPLETED'"
                :disabled="updatingTaskStatus !== null"
                @click="updateStatus('COMPLETED')"
              >
                Mark Complete
              </UButton>
              <UButton
                v-if="allow(taskCapabilities.canUpdateStatus) && (task.status === 'COMPLETED' || task.status === 'IN_PROGRESS')"
                color="neutral"
                variant="soft"
                size="sm"
                icon="i-lucide-rotate-ccw"
                :loading="updatingTaskStatus === 'OPEN'"
                :disabled="updatingTaskStatus !== null"
                @click="updateStatus('OPEN')"
              >
                Reopen
              </UButton>
            </div>
          </section>

          <section class="overflow-hidden rounded-xl border border-default bg-elevated/50 shadow-xs">
            <div class="px-2 pt-2">
              <UTabs
                v-model="activeTab"
                :items="tabItems"
                :content="false"
                variant="link"
                class="w-full"
              />
            </div>

            <div class="p-4">
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
                  <div v-if="activityHasMore" class="mt-4 flex justify-center">
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
          </section>
        </div>

        <aside class="space-y-4">
          <section class="space-y-3 rounded-xl border border-default bg-elevated/70 p-4 shadow-xs">
            <div class="flex items-center justify-between">
              <h3 class="flex items-center gap-2 text-sm font-semibold text-highlighted">
                <UIcon name="i-lucide-files" class="size-4 text-muted" />
                Linked Pages
                <UBadge
                  v-if="links?.pageLinks?.length"
                  size="xs"
                  color="neutral"
                  variant="subtle"
                >
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
                    <div class="flex space-x-2">
                      <UIcon name="i-lucide-folder" class="size-3.5 text-muted" />
                      <span class="truncate">{{ group.projectName }}</span>
                    </div>
                  </template>
                  <template #trailing>
                    <UBadge size="xs" color="neutral" variant="subtle">
                      {{ group.pages.length }}
                    </UBadge>
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
          </section>

          <section class="space-y-3 rounded-xl border border-default bg-elevated/70 p-4 shadow-xs">
            <div class="flex items-center justify-between gap-2">
              <h3 class="flex items-center gap-2 text-sm font-semibold text-highlighted">
                <UIcon name="i-lucide-users" class="size-4 text-muted" />
                Assignees
              </h3>

              <UPopover
                v-if="canAssignOthers"
                v-model:open="assigneeEditorOpen"
                :content="{ align: 'end', sideOffset: 8 }"
              >
                <UButton
                  icon="i-lucide-user-plus"
                  size="xs"
                  color="neutral"
                  variant="ghost"
                  title="Edit assignees"
                  aria-label="Edit assignees"
                />

                <template #content>
                  <div class="w-80 space-y-3 p-3">
                    <div>
                      <p class="text-sm font-semibold text-highlighted">
                        Assign users
                      </p>
                      <p class="text-xs text-muted">
                        Select everyone responsible for this task.
                      </p>
                    </div>

                    <UInput
                      v-model="assigneeQuery"
                      icon="i-lucide-search"
                      size="sm"
                      placeholder="Search workspace members..."
                      autofocus
                    />

                    <div class="max-h-64 space-y-1 overflow-y-auto pr-1">
                      <div
                        v-if="acceptedWorkspaceMembersStatus === 'pending'"
                        class="flex items-center justify-center py-6"
                      >
                        <UIcon name="i-lucide-loader-2" class="size-5 animate-spin text-muted" />
                      </div>
                      <button
                        v-for="member in filteredAssignableUsers"
                        v-else
                        :key="member.id"
                        type="button"
                        role="checkbox"
                        :aria-checked="assigneeDraftIds.includes(member.id)"
                        class="flex w-full items-center gap-2 rounded-lg px-2 py-2 text-left transition-colors hover:bg-default"
                        @click="toggleAssigneeDraft(member.id)"
                      >
                        <AppAvatar
                          :seed="member.id"
                          :src="resolveManagedProfileAvatarSrc(member.avatar)"
                          :alt="getUserDisplayName(member)"
                          size="sm"
                        />
                        <span class="min-w-0 flex-1">
                          <span class="block truncate text-sm font-medium text-highlighted">
                            {{ getUserDisplayName(member) }}
                          </span>
                          <span v-if="member.email" class="block truncate text-xs text-muted">
                            {{ member.email }}
                          </span>
                        </span>
                        <span
                          class="flex size-5 shrink-0 items-center justify-center rounded border"
                          :class="assigneeDraftIds.includes(member.id)
                            ? 'border-primary bg-primary text-inverted'
                            : 'border-accented bg-default'"
                        >
                          <UIcon
                            v-if="assigneeDraftIds.includes(member.id)"
                            name="i-lucide-check"
                            class="size-3.5"
                          />
                        </span>
                      </button>
                      <p
                        v-if="acceptedWorkspaceMembersStatus !== 'pending' && filteredAssignableUsers.length === 0"
                        class="px-2 py-5 text-center text-sm text-muted"
                      >
                        No workspace members found.
                      </p>
                    </div>

                    <div class="flex items-center justify-between gap-3 border-t border-default pt-3">
                      <p class="text-xs text-muted">
                        At least one assignee is required.
                      </p>
                      <div class="flex shrink-0 gap-2">
                        <UButton
                          size="xs"
                          color="neutral"
                          variant="ghost"
                          :disabled="isSavingAssignees"
                          @click="closeAssigneeEditor"
                        >
                          Cancel
                        </UButton>
                        <UButton
                          size="xs"
                          color="primary"
                          :loading="isSavingAssignees"
                          :disabled="assigneeDraftIds.length === 0"
                          @click="saveAssignees"
                        >
                          Save
                        </UButton>
                      </div>
                    </div>
                  </div>
                </template>
              </UPopover>
            </div>
            <div v-if="task.assignedUsers?.length" class="space-y-2">
              <div
                v-for="assignee in task.assignedUsers"
                :key="assignee.id"
                class="flex items-center gap-2"
              >
                <AppAvatar
                  :seed="assignee.id"
                  :src="resolveManagedProfileAvatarSrc(assignee.avatar)"
                  :alt="getUserDisplayName(assignee)"
                  size="sm"
                />
                <div class="flex-1 min-w-0">
                  <p class="text-sm font-medium truncate">
                    {{ getUserDisplayName(assignee) }}
                  </p>
                </div>
              </div>
            </div>
            <p v-else class="text-sm text-muted">
              No assignees
            </p>
          </section>

          <section class="space-y-3 rounded-xl border border-default bg-elevated/70 p-4 shadow-xs">
            <div class="flex items-center justify-between gap-2">
              <h3 class="flex items-center gap-2 text-sm font-semibold text-highlighted">
                <UIcon name="i-lucide-calendar-days" class="size-4 text-muted" />
                Due Date
              </h3>
              <UButton
                v-if="canEdit"
                :icon="dueDateEditorOpen ? 'i-lucide-x' : (task.dueDate ? 'i-lucide-pencil' : 'i-lucide-calendar-plus')"
                size="xs"
                color="neutral"
                variant="ghost"
                :title="dueDateEditorOpen ? 'Close due date editor' : (task.dueDate ? 'Change due date' : 'Set due date')"
                :aria-label="dueDateEditorOpen ? 'Close due date editor' : (task.dueDate ? 'Change due date' : 'Set due date')"
                @click="dueDateEditorOpen ? closeDueDateEditor() : openDueDateEditor()"
              />
            </div>

            <div v-if="dueDateEditorOpen" class="space-y-3 border-t border-default pt-3">
              <UiDateTimePicker
                v-model="dueDateDraft"
                size="sm"
                :disabled="isSavingDueDate"
                placeholder="Choose due date and time"
              />
              <div class="flex items-center justify-between gap-2">
                <UButton
                  v-if="task.dueDate"
                  size="xs"
                  color="error"
                  variant="ghost"
                  icon="i-lucide-trash-2"
                  :disabled="isSavingDueDate"
                  @click="removeDueDate"
                >
                  Remove
                </UButton>
                <span v-else />
                <div class="flex gap-2">
                  <UButton
                    size="xs"
                    color="neutral"
                    variant="ghost"
                    :disabled="isSavingDueDate"
                    @click="closeDueDateEditor"
                  >
                    Cancel
                  </UButton>
                  <UButton
                    size="xs"
                    color="primary"
                    :loading="isSavingDueDate"
                    :disabled="!dueDateDraft && !task.dueDate"
                    @click="saveDueDate"
                  >
                    Save
                  </UButton>
                </div>
              </div>
            </div>
            <div v-else-if="task.dueDate" class="space-y-1">
              <p class="text-sm" :class="isOverdue ? 'text-error font-medium' : ''">
                {{ formattedDueDate }}
              </p>
              <p class="text-xs text-muted" :class="isOverdue ? 'text-error' : ''">
                {{ dueDateRelative }}
                <span v-if="isOverdue" class="ml-1">(overdue)</span>
              </p>
            </div>
            <p v-else class="text-sm text-muted">
              No due date set
            </p>
          </section>

          <section class="space-y-3 rounded-xl border border-default bg-elevated/70 p-4 shadow-xs">
            <h3 class="flex items-center gap-2 text-sm font-semibold text-highlighted">
              <UIcon name="i-lucide-bell" class="size-4 text-muted" />
              Reminders
            </h3>

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
          </section>

          <section class="space-y-2 rounded-xl border border-default bg-elevated/70 p-4 shadow-xs">
            <h3 class="flex items-center gap-2 text-sm font-semibold text-highlighted">
              <UIcon name="i-lucide-user-round-check" class="size-4 text-muted" />
              Created by
            </h3>
            <div v-if="task.createdBy" class="flex items-center gap-2">
              <AppAvatar
                :seed="task.createdBy.id"
                :src="task.createdBy.avatar"
                :alt="task.createdBy.firstName && task.createdBy.lastName ? `${task.createdBy.firstName} ${task.createdBy.lastName}` : task.createdBy.username"
                size="sm"
              />
              <div class="flex-1 min-w-0">
                <p class="text-sm truncate">
                  {{ task.createdBy.firstName && task.createdBy.lastName ? `${task.createdBy.firstName} ${task.createdBy.lastName}` : task.createdBy.username }}
                </p>
              </div>
            </div>
          </section>

          <section class="space-y-2 rounded-xl border border-default bg-elevated/70 p-4 shadow-xs">
            <h3 class="flex items-center gap-2 text-sm font-semibold text-highlighted">
              <UIcon name="i-lucide-clock-3" class="size-4 text-muted" />
              Timestamps
            </h3>
            <div class="text-sm text-muted space-y-1">
              <p>Created: {{ format(parseISO(task.created), 'PPp') }}</p>
              <p>Updated: {{ format(parseISO(task.updated), 'PPp') }}</p>
              <p v-if="task.completedAt">
                Completed: {{ format(parseISO(task.completedAt), 'PPp') }}
              </p>
            </div>
          </section>
        </aside>
      </div>
    </template>
  </UDashboardPanel>
</template>
