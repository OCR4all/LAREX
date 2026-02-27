import type { TaskStatus } from '~/types/index'
import { wsKey } from '@/utils/fetch-keys'

const taskOverviewStatuses: Array<TaskStatus | 'ALL'> = [
  'ALL',
  'OPEN',
  'IN_PROGRESS',
  'COMPLETED',
  'CANCELLED'
]

const assignedToMeValues = ['true', 'false'] as const

export function useTaskOverviewRefresh() {
  async function refreshTaskOverview(workspaceId?: string | null) {
    if (!workspaceId) return

    const overviewKeys = taskOverviewStatuses.flatMap(status =>
      assignedToMeValues.map(assignedToMe =>
        wsKey(workspaceId, 'tasks', 'list', status, assignedToMe)
      )
    )

    await Promise.all(overviewKeys.map(key => refreshNuxtData(key)))
  }

  return {
    refreshTaskOverview
  }
}
