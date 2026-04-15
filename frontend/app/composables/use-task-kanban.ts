import type { Task, TaskStatus } from '~/types/index'

export interface KanbanColumn {
  id: TaskStatus
  title: string
  color: 'neutral' | 'info' | 'success' | 'error'
  tasks: Task[]
}

type KanbanColumnMeta = Pick<KanbanColumn, 'id' | 'title' | 'color'>

export const KANBAN_COLUMNS: KanbanColumnMeta[] = [
  { id: 'OPEN', title: 'Open', color: 'neutral' },
  { id: 'IN_PROGRESS', title: 'In Progress', color: 'info' },
  { id: 'COMPLETED', title: 'Completed', color: 'success' },
  { id: 'CANCELLED', title: 'Cancelled', color: 'error' }
]

export function useTaskKanban(tasks: Ref<Task[] | null | undefined>) {
  const toast = useToast()
  const taskList = computed<Task[]>(() => Array.isArray(tasks.value) ? tasks.value : [])

  const columns = computed<KanbanColumn[]>(() => {
    return KANBAN_COLUMNS.map(col => ({
      ...col,
      tasks: taskList.value.filter(t => t.status === col.id)
    }))
  })

  const pendingUpdates = ref<Set<string>>(new Set())

  async function updateTaskStatus(taskId: string, newStatus: TaskStatus): Promise<boolean> {
    const task = taskList.value.find(t => t.id === taskId)
    if (!task || task.status === newStatus) return true

    const oldStatus = task.status
    pendingUpdates.value.add(taskId)

    task.status = newStatus

    try {
      await $fetch(`/api/tasks/${taskId}/status`, {
        method: 'PUT',
        body: { status: newStatus }
      })
      return true
    } catch (err: unknown) {
      task.status = oldStatus
      const errorMessage = (
        (err as { data?: { message?: string } } | undefined)?.data?.message
        || (err instanceof Error ? err.message : undefined)
        || 'An error occurred'
      )
      toast.add({
        title: 'Failed to update task status',
        description: errorMessage,
        color: 'error'
      })
      return false
    } finally {
      pendingUpdates.value.delete(taskId)
    }
  }

  function isUpdating(taskId: string): boolean {
    return pendingUpdates.value.has(taskId)
  }

  return {
    columns,
    updateTaskStatus,
    isUpdating,
    pendingUpdates
  }
}
