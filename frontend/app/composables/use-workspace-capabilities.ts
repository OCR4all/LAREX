import type { WorkspaceCapabilities } from '@/types/capabilities'
import { DEFAULT_WORKSPACE_CAPABILITIES } from '@/types/capabilities'
import { globalKey, wsKey } from '@/utils/fetch-keys'

export function useWorkspaceCapabilities(workspaceId: MaybeRef<string | null | undefined>) {
  const resolvedWorkspaceId = computed(() => toValue(workspaceId))

  const key = computed(() => resolvedWorkspaceId.value
    ? wsKey(resolvedWorkspaceId.value, 'capabilities')
    : globalKey('pending', 'workspace', 'capabilities')
  )

  const { data, pending, error, refresh } = useFetch<WorkspaceCapabilities>(
    () => resolvedWorkspaceId.value ? `/api/workspaces/${resolvedWorkspaceId.value}/capabilities` : '',
    {
      key,
      watch: [resolvedWorkspaceId],
      immediate: !!resolvedWorkspaceId.value,
      default: () => ({ ...DEFAULT_WORKSPACE_CAPABILITIES })
    }
  )

  const capabilities = computed<WorkspaceCapabilities>(() => ({
    ...DEFAULT_WORKSPACE_CAPABILITIES,
    ...(data.value ?? {})
  }))

  return {
    capabilities,
    pending,
    error,
    refresh
  }
}
