import type { TaskStatus } from '~/types/index'

const taskOverviewStatuses: Array<TaskStatus | 'ALL'> = ['ALL', ...TASK_STATUSES]

const booleanValues = ['true', 'false'] as const

export function useTaskOverviewRefresh() {
  async function refreshTaskOverview(workspaceId?: string | null) {
    if (!workspaceId) return

    const overviewKeys = taskOverviewStatuses.flatMap(status =>
      booleanValues.flatMap(assignedToMe =>
        booleanValues.map(includeArchived =>
          wsKey(workspaceId, 'tasks', 'list', status, assignedToMe, includeArchived)
        )
      )
    )

    await Promise.all(overviewKeys.map(key => refreshNuxtData(key)))
  }

  return {
    refreshTaskOverview
  }
}
