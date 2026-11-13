import type { Task, TaskStatus } from '~/types/index'

export interface KanbanColumn {
  id: TaskStatus
  title: string
  tasks: Task[]
}

export function useTaskKanban(tasks: Ref<Task[] | null | undefined>) {
  const columns = computed<KanbanColumn[]>(() => {
    const taskList = Array.isArray(tasks.value) ? tasks.value : []
    return TASK_STATUSES.map(status => ({
      id: status,
      title: TASK_STATUS_META[status].label,
      tasks: taskList.filter(task => task.status === status)
    }))
  })

  return { columns }
}
