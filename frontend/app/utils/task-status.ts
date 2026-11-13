import type { TaskStatus } from '~/types/index'

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
