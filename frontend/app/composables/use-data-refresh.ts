import { globalKey, wsKey } from '@/utils/fetch-keys'

function dedupeKeys(keys: Array<string | null | undefined>): string[] {
  return [...new Set(keys.filter((key): key is string => Boolean(key)))]
}

async function refreshKeys(keys: Array<string | null | undefined>): Promise<void> {
  const uniqueKeys = dedupeKeys(keys)
  if (uniqueKeys.length === 0) return

  await Promise.all(uniqueKeys.map(key => refreshNuxtData(key)))
}

export function useDataRefresh() {
  const workspaceStore = useWorkspaceStore()
  const { refreshTaskOverview } = useTaskOverviewRefresh()

  async function refreshWorkspaceList(): Promise<void> {
    await Promise.all([
      refreshKeys([globalKey('workspaces', 'list')]),
      workspaceStore.refreshWorkspaces()
    ])
  }

  async function refreshWorkspaceMembership(workspaceId: string | null | undefined): Promise<void> {
    if (!workspaceId) return
    await refreshKeys([wsKey(workspaceId, 'members', 'list')])
  }

  async function refreshWorkspaceDetails(workspaceId: string | null | undefined): Promise<void> {
    if (!workspaceId) return
    await refreshKeys([
      wsKey(workspaceId, 'details'),
      wsKey(workspaceId, 'storage', 'quota')
    ])
  }

  async function refreshWorkspaceTransfers(workspaceId: string | null | undefined): Promise<void> {
    if (!workspaceId) return

    await refreshKeys([
      wsKey(workspaceId, 'project-transfers', 'incoming'),
      wsKey(workspaceId, 'resource-transfers', 'incoming'),
      wsKey(workspaceId, 'project-transfers', 'outgoing'),
      wsKey(workspaceId, 'resource-transfers', 'outgoing')
    ])
  }

  async function refreshUserInvitations(): Promise<void> {
    await refreshKeys([
      globalKey('user', 'invitations', 'list')
    ])
  }

  async function refreshUserTransfers(): Promise<void> {
    await refreshKeys([
      globalKey('user', 'project-transfers', 'my-requests'),
      globalKey('user', 'resource-transfers', 'my-requests')
    ])
  }

  async function refreshAdminQuotas(): Promise<void> {
    await refreshKeys([
      globalKey('admin', 'storage-quotas', 'all'),
      globalKey('admin', 'storage-quotas', 'default'),
      globalKey('admin', 'storage-quotas', 'exceeded')
    ])
  }

  async function refreshProjectCaches(workspaceId: string | null | undefined, projectId: string | null | undefined): Promise<void> {
    if (!workspaceId) return

    await refreshKeys([
      wsKey(workspaceId, 'projects', 'list'),
      projectId ? wsKey(workspaceId, 'projects', projectId) : null,
      projectId ? wsKey(workspaceId, 'projects', projectId, 'status') : null,
      projectId ? wsKey(workspaceId, 'projects', projectId, 'pages') : null
    ])
  }

  async function refreshTaskCaches(taskId: string | null | undefined, workspaceId?: string | null): Promise<void> {
    if (!taskId) return

    await refreshKeys([
      globalKey('tasks', taskId, 'detail'),
      globalKey('tasks', taskId, 'comments'),
      globalKey('tasks', taskId, 'links'),
      globalKey('tasks', taskId, 'subtasks'),
      globalKey('tasks', taskId, 'subtasks-progress'),
      globalKey('tasks', taskId, 'reminders')
    ])

    if (workspaceId) {
      await refreshTaskOverview(workspaceId)
    }
  }

  return {
    refreshWorkspaceList,
    refreshWorkspaceMembership,
    refreshWorkspaceDetails,
    refreshWorkspaceTransfers,
    refreshUserInvitations,
    refreshUserTransfers,
    refreshAdminQuotas,
    refreshProjectCaches,
    refreshTaskCaches
  }
}
