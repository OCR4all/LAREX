import { computed, ref, watch, type ComputedRef } from 'vue'
import type { Subtask, LinkedTask } from '~/types/index'
import { extractApiErrorMessage } from '@/utils/api-error'

type EditorTaskStateOptions = {
  currentProjectId: ComputedRef<string | null>
  activePageId: ComputedRef<string | null>
  isActivePageLocked: ComputedRef<boolean>
  selectedWorkspace: ComputedRef<string | null | undefined>
  openedProjectIds: ComputedRef<string[]>
  refreshTaskCaches: (taskId: string, workspaceId?: string | null) => Promise<unknown>
  saveDocument: () => Promise<boolean>
}

export function useEditorTaskState(options: EditorTaskStateOptions) {
  const toast = useToast()

  const openSubtasksByProjectId = ref<Record<string, Record<string, Subtask[]>>>({})
  const isOpenSubtasksLoading = ref(false)
  const activePageTasks = ref<LinkedTask[]>([])
  const isActivePageTasksLoading = ref(false)
  const isCompletingOpenSubtasks = ref(false)

  const openSubtasksByPage = computed<Record<string, Subtask[]>>(() => {
    const projectId = options.currentProjectId.value
    if (!projectId) return {}
    return openSubtasksByProjectId.value[projectId] ?? {}
  })

  async function fetchOpenSubtasks(projectId?: string | null) {
    const targetProjectId = projectId ?? options.currentProjectId.value
    if (!targetProjectId) return

    isOpenSubtasksLoading.value = true
    try {
      const subtasksByPage = await $fetch<Record<string, Subtask[]>>(`/api/projects/${targetProjectId}/pages/subtasks/open`)
      openSubtasksByProjectId.value = {
        ...openSubtasksByProjectId.value,
        [targetProjectId]: subtasksByPage
      }
    } catch (error: unknown) {
      toast.add({
        title: 'Failed to load open subtasks',
        description: extractApiErrorMessage(error, 'Could not load open subtasks.'),
        color: 'error'
      })
    } finally {
      isOpenSubtasksLoading.value = false
    }
  }

  watch(options.currentProjectId, (projectId) => {
    void fetchOpenSubtasks(projectId)
  }, { immediate: true })

  watch(options.openedProjectIds, (projectIds) => {
    const openedIdSet = new Set(projectIds)
    openSubtasksByProjectId.value = Object.fromEntries(
      Object.entries(openSubtasksByProjectId.value).filter(([projectId]) => openedIdSet.has(projectId))
    )

    for (const projectId of projectIds) {
      if (!openSubtasksByProjectId.value[projectId]) {
        void fetchOpenSubtasks(projectId)
      }
    }
  }, { immediate: true })

  const openSubtaskPageIds = computed(() => {
    return new Set(
      Object.entries(openSubtasksByPage.value)
        .filter(([, subtasks]) => (subtasks?.length ?? 0) > 0)
        .map(([pageId]) => pageId)
    )
  })

  function getOpenSubtaskCountByPage(projectId: string): Record<string, number> {
    const subtasksByPage = openSubtasksByProjectId.value[projectId] ?? {}
    const result: Record<string, number> = {}
    for (const [pageId, subtasks] of Object.entries(subtasksByPage)) {
      result[pageId] = subtasks?.length ?? 0
    }
    return result
  }

  function getOpenSubtaskPageIds(projectId: string): Set<string> {
    const subtasksByPage = openSubtasksByProjectId.value[projectId] ?? {}
    return new Set(
      Object.entries(subtasksByPage)
        .filter(([, subtasks]) => (subtasks?.length ?? 0) > 0)
        .map(([pageId]) => pageId)
    )
  }

  const activeOpenSubtasks = computed(() => {
    const pageId = options.activePageId.value
    if (!pageId) return [] as Subtask[]
    return openSubtasksByPage.value?.[pageId] ?? []
  })

  async function fetchActivePageTasks(pageId: string | null) {
    if (!pageId) {
      activePageTasks.value = []
      return
    }

    isActivePageTasksLoading.value = true
    try {
      activePageTasks.value = await $fetch<LinkedTask[]>(`/api/pages/${pageId}/tasks?onlyAssigned=true`)
    } catch (error: unknown) {
      activePageTasks.value = []
      toast.add({
        title: 'Failed to load page tasks',
        description: extractApiErrorMessage(error, 'Could not load page tasks.'),
        color: 'error'
      })
    } finally {
      isActivePageTasksLoading.value = false
    }
  }

  watch(options.activePageId, (pageId) => {
    void fetchActivePageTasks(pageId)
  }, { immediate: true })

  const activeTaskByIdRecord = computed<Record<string, LinkedTask>>(() => {
    const record: Record<string, LinkedTask> = {}
    for (const task of activePageTasks.value) {
      record[task.id] = task
    }
    return record
  })

  const canCompleteActivePageSubtasks = computed(() => {
    return !options.isActivePageLocked.value && activeOpenSubtasks.value.length > 0
  })

  async function completeSubtask(subtask: Subtask) {
    if (options.isActivePageLocked.value) return
    try {
      await $fetch(`/api/tasks/${subtask.taskId}/subtasks/${subtask.id}/toggle`, { method: 'PUT' })
      await options.refreshTaskCaches(subtask.taskId, options.selectedWorkspace.value)
      await fetchOpenSubtasks()
      await fetchActivePageTasks(options.activePageId.value)
    } catch (error: unknown) {
      toast.add({
        title: 'Failed to complete subtask',
        description: extractApiErrorMessage(error, 'Could not complete subtask.'),
        color: 'error'
      })
    }
  }

  async function completeActivePageSubtasks() {
    if (!canCompleteActivePageSubtasks.value) return
    isCompletingOpenSubtasks.value = true
    try {
      await Promise.all(
        activeOpenSubtasks.value.map(subtask =>
          $fetch(`/api/tasks/${subtask.taskId}/subtasks/${subtask.id}/toggle`, { method: 'PUT' })
        )
      )
      const affectedTaskIds = [...new Set(activeOpenSubtasks.value.map(subtask => subtask.taskId))]
      await Promise.all(affectedTaskIds.map(taskId => options.refreshTaskCaches(taskId, options.selectedWorkspace.value)))
      await fetchOpenSubtasks()
      await fetchActivePageTasks(options.activePageId.value)
      toast.add({
        title: 'Completed open subtasks',
        color: 'success'
      })
    } catch (error: unknown) {
      toast.add({
        title: 'Failed to complete open subtasks',
        description: extractApiErrorMessage(error, 'Could not complete open subtasks.'),
        color: 'error'
      })
    } finally {
      isCompletingOpenSubtasks.value = false
    }
  }

  async function handleSaveAndCompleteOpenSubtasks() {
    const saved = await options.saveDocument()
    if (!saved) return
    await completeActivePageSubtasks()
  }

  return {
    openSubtasksByProjectId,
    openSubtasksByPage,
    isOpenSubtasksLoading,
    openSubtaskPageIds,
    getOpenSubtaskCountByPage,
    getOpenSubtaskPageIds,
    activeOpenSubtasks,
    activePageTasks,
    isActivePageTasksLoading,
    activeTaskByIdRecord,
    canCompleteActivePageSubtasks,
    isCompletingOpenSubtasks,
    fetchOpenSubtasks,
    fetchActivePageTasks,
    completeSubtask,
    completeActivePageSubtasks,
    handleSaveAndCompleteOpenSubtasks
  }
}
