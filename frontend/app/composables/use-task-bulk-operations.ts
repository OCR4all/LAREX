import type { TaskStatus, TaskPriority, BulkOperationResponse } from '~/types/index'

export function useTaskBulkOperations(workspaceId: MaybeRef<string | undefined>) {
  const toast = useToast()
  const isLoading = ref(false)

  const resolvedWorkspaceId = computed(() => toValue(workspaceId))

  async function bulkUpdateStatus(taskIds: string[], status: TaskStatus): Promise<BulkOperationResponse | null> {
    if (!resolvedWorkspaceId.value || taskIds.length === 0) return null

    isLoading.value = true
    try {
      const response = await $fetch<BulkOperationResponse>(
        `/api/workspaces/${resolvedWorkspaceId.value}/tasks/bulk/status`,
        {
          method: 'PUT',
          body: { taskIds, status }
        }
      )

      showResultToast(response, 'status update')
      return response
    } catch (err: any) {
      toast.add({ title: 'Bulk status update failed', description: err?.data?.message, color: 'error' })
      return null
    } finally {
      isLoading.value = false
    }
  }

  async function bulkUpdatePriority(taskIds: string[], priority: TaskPriority): Promise<BulkOperationResponse | null> {
    if (!resolvedWorkspaceId.value || taskIds.length === 0) return null

    isLoading.value = true
    try {
      const response = await $fetch<BulkOperationResponse>(
        `/api/workspaces/${resolvedWorkspaceId.value}/tasks/bulk/priority`,
        {
          method: 'PUT',
          body: { taskIds, priority }
        }
      )

      showResultToast(response, 'priority update')
      return response
    } catch (err: any) {
      toast.add({ title: 'Bulk priority update failed', description: err?.data?.message, color: 'error' })
      return null
    } finally {
      isLoading.value = false
    }
  }

  async function bulkUpdateAssignees(
    taskIds: string[],
    addUserIds?: string[],
    removeUserIds?: string[]
  ): Promise<BulkOperationResponse | null> {
    if (!resolvedWorkspaceId.value || taskIds.length === 0) return null

    isLoading.value = true
    try {
      const response = await $fetch<BulkOperationResponse>(
        `/api/workspaces/${resolvedWorkspaceId.value}/tasks/bulk/assignees`,
        {
          method: 'PUT',
          body: { taskIds, addUserIds, removeUserIds }
        }
      )

      showResultToast(response, 'assignees update')
      return response
    } catch (err: any) {
      toast.add({ title: 'Bulk assignees update failed', description: err?.data?.message, color: 'error' })
      return null
    } finally {
      isLoading.value = false
    }
  }

  async function bulkDelete(taskIds: string[]): Promise<BulkOperationResponse | null> {
    if (!resolvedWorkspaceId.value || taskIds.length === 0) return null

    isLoading.value = true
    try {
      const response = await $fetch<BulkOperationResponse>(
        `/api/workspaces/${resolvedWorkspaceId.value}/tasks/bulk`,
        {
          method: 'DELETE',
          body: { taskIds }
        }
      )

      showResultToast(response, 'delete')
      return response
    } catch (err: any) {
      toast.add({ title: 'Bulk delete failed', description: err?.data?.message, color: 'error' })
      return null
    } finally {
      isLoading.value = false
    }
  }

  function showResultToast(response: BulkOperationResponse, operation: string) {
    if (response.failedCount === 0) {
      toast.add({
        title: `Bulk ${operation} completed`,
        description: `Successfully updated ${response.successCount} task(s)`,
        color: 'success'
      })
    } else if (response.successCount === 0) {
      toast.add({
        title: `Bulk ${operation} failed`,
        description: `All ${response.failedCount} task(s) failed`,
        color: 'error'
      })
    } else {
      toast.add({
        title: `Bulk ${operation} partially completed`,
        description: `${response.successCount} succeeded, ${response.failedCount} failed`,
        color: 'warning'
      })
    }
  }

  return {
    isLoading: readonly(isLoading),
    bulkUpdateStatus,
    bulkUpdatePriority,
    bulkUpdateAssignees,
    bulkDelete
  }
}
