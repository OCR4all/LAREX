import type { Task, TaskStatus } from '~/types/index'

export interface KanbanColumn {
  id: TaskStatus
  title: string
  color: 'neutral' | 'info' | 'success' | 'error'
  tasks: Task[]
}

export const KANBAN_COLUMNS: { id: TaskStatus; title: string; color: 'neutral' | 'info' | 'success' | 'error' }[] = [
  { id: 'OPEN', title: 'Open', color: 'neutral' },
  { id: 'IN_PROGRESS', title: 'In Progress', color: 'info' },
  { id: 'COMPLETED', title: 'Completed', color: 'success' },
  { id: 'CANCELLED', title: 'Cancelled', color: 'error' }
]

export function useTaskKanban(tasks: Ref<Task[]>) {
  const toast = useToast()

  const columns = computed<KanbanColumn[]>(() => {
    return KANBAN_COLUMNS.map(col => ({
      ...col,
      tasks: tasks.value.filter(t => t.status === col.id)
    }))
  })

  const pendingUpdates = ref<Set<string>>(new Set())

  async function updateTaskStatus(taskId: string, newStatus: TaskStatus): Promise<boolean> {
    const task = tasks.value.find(t => t.id === taskId)
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
    } catch (err: any) {
      task.status = oldStatus
      toast.add({
        title: 'Failed to update task status',
        description: err?.data?.message || 'An error occurred',
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
