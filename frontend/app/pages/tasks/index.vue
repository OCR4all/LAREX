<script setup lang="ts">
import type { DropdownMenuItem, TableColumn, TableRow } from '@nuxt/ui'
import type { Task, TaskStatus, TaskPriority, WorkspaceMember } from '~/types/index'
import { DEFAULT_TASK_CAPABILITIES } from '@/types/capabilities'
import { LazyUiDeleteSlideover, LazyTaskSlideoverEdit } from '#components'

const UButton = resolveComponent('UButton')
const UBadge = resolveComponent('UBadge')
const UCheckbox = resolveComponent('UCheckbox')
const UDropdownMenu = resolveComponent('UDropdownMenu')
const TaskSubtaskProgress = resolveComponent('TaskSubtaskProgress')

const route = useRoute()
const toast = useToast()
const overlay = useOverlay()
const { refreshTaskOverview } = useTaskOverviewRefresh()

await useWorkspaceBootstrap()

const workspace = useWorkspaceStore()
const selectedWorkspace = computed(() => workspace.selectedWorkspaceId)
const { capabilities: workspaceCapabilities } = useWorkspaceCapabilities(selectedWorkspace)
const { allow } = useActionVisibility()

const { user } = useUserSession()
const currentUserId = computed(() => user.value?.id || '')

type ViewMode = 'table' | 'kanban'
const viewMode = ref<ViewMode>('table')

const STORAGE_KEY = 'larex-tasks-view-mode'
onMounted(() => {
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored === 'table' || stored === 'kanban') {
    viewMode.value = stored
  }
})
watch(viewMode, (mode) => {
  localStorage.setItem(STORAGE_KEY, mode)
})

const statusFilter = ref<TaskStatus | 'ALL'>('ALL')
const assignedToMe = ref(true)
const q = ref('')

const tasksKey = computed(() => {
  if (!selectedWorkspace.value) return globalKey('pending', 'tasks', 'list')
  return wsKey(selectedWorkspace.value, 'tasks', 'list', statusFilter.value, String(assignedToMe.value))
})

const tasksQuery = computed(() => {
  const query: { assignedToMe: boolean, status?: TaskStatus } = { assignedToMe: assignedToMe.value }
  if (statusFilter.value !== 'ALL') query.status = statusFilter.value
  return query
})

const { data: tasks, status: tasksStatus } = await useFetch<Task[]>(
  () => `/api/workspaces/${selectedWorkspace.value}/tasks`,
  {
    key: tasksKey,
    watch: [selectedWorkspace, statusFilter, assignedToMe],
    query: tasksQuery,
    default: () => [],
    immediate: !!selectedWorkspace.value
  }
)

const membersKey = computed(() => {
  if (!selectedWorkspace.value) return globalKey('pending', 'members', 'list')
  return wsKey(selectedWorkspace.value, 'members', 'list')
})

const { data: members } = await useFetch<WorkspaceMember[]>(
  () => `/api/workspaces/${selectedWorkspace.value}/members`,
  {
    key: membersKey,
    watch: [selectedWorkspace],
    default: () => [],
    immediate: !!selectedWorkspace.value
  }
)

const canManageTasks = computed(() => allow(workspaceCapabilities.value.canManageTasks))
const acceptedMembers = computed(() => (members.value ?? []).filter(m => m.invitationStatus === 'ACCEPTED'))
const canCreateTasks = computed(() => canManageTasks.value)

const tasksSafe = computed(() => Array.isArray(tasks.value) ? tasks.value : [])
const tasksSafeCount = computed(() => tasksSafe.value.length)

const filteredTasks = computed(() => {
  const source = Array.isArray(tasksSafe.value) ? tasksSafe.value : []
  if (!q.value) return source
  const search = q.value.toLowerCase()
  return source.filter(t =>
    t.title.toLowerCase().includes(search)
    || (t.description || '').toLowerCase().includes(search)
  )
})

const selectedTaskIds = ref<Set<string>>(new Set())
const selectedTasks = computed(() =>
  (filteredTasks.value ?? []).filter(t => selectedTaskIds.value.has(t.id))
)
const selectedTasksCount = computed(() => selectedTasks.value?.length ?? 0)

function getTaskCapabilities(task: Task) {
  return {
    ...DEFAULT_TASK_CAPABILITIES,
    ...(task.capabilities ?? {})
  }
}

const canBulkDeleteSelected = computed(() =>
  selectedTasksCount.value > 0 && selectedTasks.value.every(task => allow(getTaskCapabilities(task).canDelete))
)

const canBulkStatusSelected = computed(() =>
  selectedTasksCount.value > 0 && selectedTasks.value.every(task => allow(getTaskCapabilities(task).canUpdateStatus))
)

const canBulkPrioritySelected = computed(() =>
  selectedTasksCount.value > 0 && selectedTasks.value.every(task => allow(getTaskCapabilities(task).canEdit))
)

const canBulkAssignSelected = computed(() =>
  selectedTasksCount.value > 0 && selectedTasks.value.every(task => allow(getTaskCapabilities(task).canAssignOthers))
)

const bulkAssignPopoverOpen = ref(false)
const bulkAssignQuery = ref('')

const bulkAssigneeItems = computed(() =>
  (acceptedMembers.value ?? [])
    .map(member => ({
      label: member.displayName || member.username || member.userId,
      value: member.userId
    }))
    .sort((a, b) => a.label.localeCompare(b.label, undefined, { sensitivity: 'base' }))
)
const hasBulkAssigneeItems = computed(() => (bulkAssigneeItems.value?.length ?? 0) > 0)

const filteredBulkAssigneeItems = computed(() => {
  const query = bulkAssignQuery.value.trim().toLowerCase()
  const items = bulkAssigneeItems.value ?? []
  if (!query) return items
  return items.filter(item => item.label.toLowerCase().includes(query))
})
const hasFilteredBulkAssigneeItems = computed(() => (filteredBulkAssigneeItems.value?.length ?? 0) > 0)

const allSelected = computed(() =>
  (filteredTasks.value?.length ?? 0) > 0 && (filteredTasks.value ?? []).every(t => selectedTaskIds.value.has(t.id))
)

const someSelected = computed(() =>
  selectedTaskIds.value.size > 0 && !allSelected.value
)

function toggleSelectAll() {
  if (allSelected.value) {
    selectedTaskIds.value.clear()
  } else {
    ;(filteredTasks.value ?? []).forEach(t => selectedTaskIds.value.add(t.id))
  }
}

function toggleTaskSelection(taskId: string) {
  if (selectedTaskIds.value.has(taskId)) {
    selectedTaskIds.value.delete(taskId)
  } else {
    selectedTaskIds.value.add(taskId)
  }
}

function clearSelection() {
  selectedTaskIds.value.clear()
}

function isTaskSelected(taskId: string) {
  return selectedTaskIds.value.has(taskId)
}

const bulkWorkspaceId = computed(() => selectedWorkspace.value ?? undefined)
const { isLoading: bulkLoading, bulkUpdateStatus, bulkUpdatePriority, bulkUpdateAssignees, bulkDelete } = useTaskBulkOperations(bulkWorkspaceId)

const deleteSlideover = overlay.create(LazyUiDeleteSlideover)
const createTaskSlideover = overlay.create(LazyTaskSlideoverEdit)

async function refreshOverview() {
  await refreshTaskOverview(selectedWorkspace.value)
}

async function refreshCurrentTasksView() {
  await refreshNuxtData(tasksKey.value)
}

const statusOptions: Array<{ label: string, value: TaskStatus, icon: string }> = [
  { label: 'Open', value: 'OPEN', icon: 'i-lucide-circle' },
  { label: 'In Progress', value: 'IN_PROGRESS', icon: 'i-lucide-play' },
  { label: 'Completed', value: 'COMPLETED', icon: 'i-lucide-check-circle' },
  { label: 'Cancelled', value: 'CANCELLED', icon: 'i-lucide-x-circle' }
]

const statusFilterOptions = [
  { label: 'All', value: 'ALL' },
  ...statusOptions.map(opt => ({ label: opt.label, value: opt.value }))
]

const priorityOptions: Array<{ label: string, value: TaskPriority }> = [
  { label: 'Low', value: 'LOW' },
  { label: 'Medium', value: 'MEDIUM' },
  { label: 'High', value: 'HIGH' },
  { label: 'Urgent', value: 'URGENT' }
]

async function handleBulkStatusChange(status: TaskStatus) {
  if (!canBulkStatusSelected.value) return
  const ids = selectedTasks.value.map(t => t.id)
  const result = await bulkUpdateStatus(ids, status)
  if (result) {
    await refreshOverview()
    clearSelection()
  }
}

async function handleBulkPriorityChange(priority: TaskPriority) {
  if (!canBulkPrioritySelected.value) return
  const ids = selectedTasks.value.map(t => t.id)
  const result = await bulkUpdatePriority(ids, priority)
  if (result) {
    await refreshOverview()
    clearSelection()
  }
}

async function handleBulkAddAssignee(userId: string) {
  if (!canBulkAssignSelected.value) return
  const ids = selectedTasks.value.map(t => t.id)
  const result = await bulkUpdateAssignees(ids, [userId], [])
  if (result) {
    await refreshOverview()
    clearSelection()
  }
}

function closeBulkAssignPopover() {
  bulkAssignPopoverOpen.value = false
  bulkAssignQuery.value = ''
}

async function handleBulkAssigneePick(userId: string) {
  await handleBulkAddAssignee(userId)
  closeBulkAssignPopover()
}

watch(canBulkAssignSelected, (enabled) => {
  if (!enabled) closeBulkAssignPopover()
})

async function handleBulkDelete() {
  if (!canBulkDeleteSelected.value) return
  const count = selectedTasksCount.value
  const instance = deleteSlideover.open({
    name: `${count} task${count > 1 ? 's' : ''}`,
    entityType: 'Task',
    warningMessage: `This will permanently delete ${count} task${count > 1 ? 's' : ''} and all associated data.`
  })
  const confirmed = await instance.result
  if (!confirmed) return

  const ids = selectedTasks.value.map(t => t.id)
  const result = await bulkDelete(ids)
  if (result) {
    await refreshOverview()
    clearSelection()
  }
}

async function handleDeleteTask(task: Task) {
  if (!allow(getTaskCapabilities(task).canDelete)) return

  const instance = deleteSlideover.open({
    name: task.title,
    entityType: 'Task',
    warningMessage: 'This will permanently delete this task and all associated data.'
  })
  const confirmed = await instance.result
  if (!confirmed) return

  try {
    await $fetch(`/api/tasks/${task.id}`, { method: 'DELETE' })
    toast.add({ title: 'Task deleted', color: 'success' })
    await refreshOverview()
  } catch (error: unknown) {
    toast.add({
      title: 'Failed to delete task',
      description: extractApiErrorMessage(error, 'Could not delete the task.'),
      color: 'error'
    })
  }
}

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

function getRowActions(task: Task) {
  const capabilities = getTaskCapabilities(task)
  const actions: DropdownMenuItem[][] = [
    [
      {
        label: 'Open',
        icon: 'i-lucide-external-link',
        onSelect: () => navigateTo(`/tasks/${task.id}`)
      }
    ]
  ]
  if (allow(capabilities.canDelete)) {
    actions.push([
      {
        label: 'Delete',
        icon: 'i-lucide-trash',
        color: 'error',
        onSelect: () => handleDeleteTask(task)
      }
    ])
  }
  return actions
}

const contextMenuTask = ref<Task | null>(null)
const contextMenuItems = computed(() => {
  if (!contextMenuTask.value) return []
  return getRowActions(contextMenuTask.value)
})

function handleRowContextMenu(_event: Event, row?: TableRow<Task>) {
  if (!row?.original) return
  contextMenuTask.value = row.original
}

const columns: TableColumn<Task>[] = [
  {
    id: 'select',
    header: () => {
      if (!canManageTasks.value) return null
      return h(UCheckbox, {
        'modelValue': allSelected.value,
        'indeterminate': someSelected.value,
        'onUpdate:modelValue': () => toggleSelectAll()
      })
    },
    cell: ({ row }) => {
      if (!canManageTasks.value) return null
      return h(UCheckbox, {
        'modelValue': isTaskSelected(row.original.id),
        'onUpdate:modelValue': () => toggleTaskSelection(row.original.id),
        'onClick': (e: Event) => e.stopPropagation()
      })
    }
  },
  {
    accessorKey: 'title',
    header: 'Title',
    cell: ({ row }) => h('a', {
      href: `/tasks/${row.original.id}`,
      class: 'font-medium hover:underline text-primary text-left',
      onClick: (e: Event) => {
        e.preventDefault()
        navigateTo(`/tasks/${row.original.id}`)
      }
    }, row.original.title)
  },
  {
    id: 'progress',
    header: 'Progress',
    cell: ({ row }) => h('div', { class: 'min-w-[120px]' }, [
      h(TaskSubtaskProgress, { taskId: row.original.id })
    ])
  },
  {
    accessorKey: 'status',
    header: 'Status',
    cell: ({ row }) => h(UBadge, { variant: 'subtle', size: 'sm', color: statusColor(row.original.status) }, () => row.original.status.replace('_', ' '))
  },
  {
    accessorKey: 'priority',
    header: 'Priority',
    cell: ({ row }) => h(UBadge, { variant: 'subtle', size: 'sm', color: priorityColor(row.original.priority) }, () => row.original.priority)
  },
  {
    accessorKey: 'assignees',
    header: 'Assignees',
    cell: ({ row }) => {
      const names = row.original.assignedUsers?.map(u => u.username).filter(Boolean) || []
      return h('span', { class: 'text-sm text-muted' }, names.length ? names.join(', ') : '—')
    }
  },
  {
    id: 'actions',
    cell: ({ row }) => h('div', { class: 'text-right' },
      h(UDropdownMenu, {
        content: { align: 'end' },
        items: getRowActions(row.original)
      }, () => h(UButton, {
        icon: 'i-lucide-ellipsis-vertical',
        color: 'neutral',
        variant: 'ghost',
        onClick: (e: Event) => e.stopPropagation()
      }))
    )
  }
]

function openCreate() {
  if (!canCreateTasks.value) return
  if (!selectedWorkspace.value) return
  createTaskSlideover.open({
    workspaceId: selectedWorkspace.value,
    isAdmin: canManageTasks.value,
    currentUserId: currentUserId.value
  })
}

function handleTaskClick(task: Task) {
  navigateTo(`/tasks/${task.id}`)
}

watch(() => route.query.taskId, (taskId) => {
  if (!taskId || typeof taskId !== 'string') return
  navigateTo(`/tasks/${taskId}`)
}, { immediate: true })

const viewModeItems = [
  { value: 'table', icon: 'i-lucide-table-2' },
  { value: 'kanban', icon: 'i-lucide-kanban' }
]
</script>

<template>
  <UDashboardPanel id="tasks" data-tour="tasks-panel">
    <template #header>
      <UDashboardNavbar title="Tasks">
        <template #leading>
          <LazyUDashboardSidebarCollapse />
        </template>
        <template #right>
          <UButton
            v-if="selectedWorkspace && canCreateTasks"
            data-tour="tasks-new"
            label="New Task"
            color="neutral"
            variant="outline"
            icon="i-lucide-clipboard-plus"
            @click="openCreate"
          />
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar>
        <template #left>
          <UInput
            v-model="q"
            data-tour="tasks-search"
            placeholder="Search tasks..."
            icon="i-lucide-search"
            class="w-64"
          />
          <USelect
            v-if="viewMode === 'table'"
            v-model="statusFilter"
            :items="statusFilterOptions"
            value-key="value"
            class="w-40"
          />
          <UCheckbox
            v-model="assignedToMe"
            label="Assigned to me"
          />
        </template>
        <template #right>
          <UTabs
            v-model="viewMode"
            data-tour="tasks-view-mode"
            size="sm"
            color="primary"
            :content="false"
            :items="viewModeItems"
          />
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div v-if="tasksStatus === 'pending' && tasksSafeCount === 0" class="py-8 text-center">
        <div class="flex items-center justify-center">
          <UIcon name="i-lucide-loader" class="animate-spin text-neutral-500" />
          <span class="ml-2 text-sm text-neutral-600 dark:text-neutral-400">Loading tasks...</span>
        </div>
      </div>

      <UEmpty
        v-else-if="tasksSafeCount === 0"
        variant="naked"
        icon="i-lucide-clipboard-list"
        title="No tasks found"
        description="Create your first task to get started."
        :actions="[
          {
            icon: 'i-lucide-clipboard-plus',
            label: 'Create Task',
            variant: 'solid',
            disabled: !selectedWorkspace || !canCreateTasks,
            onClick: openCreate
          }
        ]"
      />

      <div v-else-if="viewMode === 'table'">
        <UContextMenu :items="contextMenuItems">
          <AppTable
            table-id="tasks-index"
            :data="filteredTasks"
            :columns="columns"
            @contextmenu="handleRowContextMenu"
          />
        </UContextMenu>

        <UiFloatingSelectionMenu
          :selected-count="selectedTasksCount"
          @clear="clearSelection"
        >
          <UDropdownMenu
            v-if="canBulkStatusSelected"
            :items="statusOptions.map(opt => ({
              label: opt.label,
              icon: opt.icon,
              onSelect: () => handleBulkStatusChange(opt.value)
            }))"
          >
            <UButton
              icon="i-lucide-circle-dot"
              color="neutral"
              variant="ghost"
              size="sm"
              class="text-neutral-50 hover:bg-white/10"
              :loading="bulkLoading"
            >
              Status
            </UButton>
          </UDropdownMenu>

          <UDropdownMenu
            v-if="canBulkPrioritySelected"
            :items="priorityOptions.map(opt => ({
              label: opt.label,
              onSelect: () => handleBulkPriorityChange(opt.value)
            }))"
          >
            <UButton
              icon="i-lucide-flag"
              color="neutral"
              variant="ghost"
              size="sm"
              class="text-neutral-50 hover:bg-white/10"
              :loading="bulkLoading"
            >
              Priority
            </UButton>
          </UDropdownMenu>

          <UPopover
            v-if="hasBulkAssigneeItems && canBulkAssignSelected"
            v-model:open="bulkAssignPopoverOpen"
            :content="{ align: 'start', sideOffset: 6 }"
          >
            <UButton
              :icon="bulkLoading ? 'i-lucide-loader-2' : 'i-lucide-user-plus'"
              color="neutral"
              variant="ghost"
              size="sm"
              class="text-neutral-50 hover:bg-white/10"
              :ui="{ leadingIcon: bulkLoading ? 'animate-spin' : '' }"
              :disabled="bulkLoading"
            >
              Assign
            </UButton>

            <template #content>
              <div class="w-64 space-y-2 p-2">
                <UInput
                  v-model="bulkAssignQuery"
                  icon="i-lucide-search"
                  size="sm"
                  placeholder="Search assignees..."
                  autofocus
                />
                <div class="max-h-56 space-y-1 overflow-auto">
                  <UButton
                    v-for="item in filteredBulkAssigneeItems"
                    :key="item.value"
                    color="neutral"
                    variant="ghost"
                    size="sm"
                    block
                    class="justify-start"
                    :disabled="bulkLoading"
                    @click="handleBulkAssigneePick(item.value)"
                  >
                    {{ item.label }}
                  </UButton>
                  <p v-if="!hasFilteredBulkAssigneeItems" class="px-2 py-1 text-xs text-muted">
                    No assignees found.
                  </p>
                </div>
              </div>
            </template>
          </UPopover>

          <UButton
            v-if="canBulkDeleteSelected"
            icon="i-lucide-trash-2"
            color="error"
            variant="ghost"
            size="sm"
            class="hover:bg-white/10"
            :loading="bulkLoading"
            @click="handleBulkDelete"
          >
            Delete
          </UButton>
        </UiFloatingSelectionMenu>
      </div>

      <div v-else>
        <TaskBoardKanban
          :tasks="filteredTasks"
          @refresh="refreshCurrentTasksView"
          @task-click="handleTaskClick"
        />
      </div>
    </template>
  </UDashboardPanel>
</template>
