import type { Task, TaskStatus } from '~/types/index'
import { normalizeLocalDateTimeInput } from '~/composables/use-local-date-time'

export type TaskStatusMeta = {
  label: string
  color: 'neutral' | 'info' | 'success' | 'warning'
  icon: string
}

export const TASK_STATUSES: TaskStatus[] = ['OPEN', 'IN_PROGRESS', 'COMPLETED', 'PAUSED']

export const TASK_STATUS_META: Record<TaskStatus, TaskStatusMeta> = {
  OPEN: { label: 'Open', color: 'neutral', icon: 'i-lucide-circle-dot' },
  IN_PROGRESS: { label: 'In progress', color: 'info', icon: 'i-lucide-loader-circle' },
  COMPLETED: { label: 'Completed', color: 'success', icon: 'i-lucide-circle-check' },
  PAUSED: { label: 'Paused', color: 'warning', icon: 'i-lucide-circle-pause' }
}

export function getTaskStatusMeta(status: string | null | undefined): TaskStatusMeta {
  return TASK_STATUS_META[status as TaskStatus] ?? { label: status ?? 'Unknown', color: 'neutral', icon: 'i-lucide-circle' }
}

export function isTaskArchived(task: { archivedAt?: string | null } | null | undefined) {
  return Boolean(task?.archivedAt)
}

type DueTask = Pick<Task, 'dueDate' | 'status'> & { archivedAt?: string | null }

export type TaskDueFilter = 'ALL' | 'OVERDUE' | 'DUE_SOON'

const DUE_SOON_MS = 7 * 24 * 60 * 60 * 1000

function isTaskStopped(task: DueTask) {
  return task.status === 'COMPLETED' || task.status === 'PAUSED' || isTaskArchived(task)
}

export function isTaskOverdue(task: DueTask, now = new Date()) {
  const due = normalizeLocalDateTimeInput(task.dueDate)
  return Boolean(due) && !isTaskStopped(task) && due!.getTime() < now.getTime()
}

export function isTaskDueSoon(task: DueTask, now = new Date()) {
  const due = normalizeLocalDateTimeInput(task.dueDate)
  if (!due || isTaskStopped(task)) return false
  const remaining = due.getTime() - now.getTime()
  return remaining >= 0 && remaining <= DUE_SOON_MS
}

export function matchesTaskDueFilter(task: DueTask, filter: TaskDueFilter, now = new Date()) {
  if (filter === 'OVERDUE') return isTaskOverdue(task, now)
  if (filter === 'DUE_SOON') return isTaskDueSoon(task, now)
  return true
}
