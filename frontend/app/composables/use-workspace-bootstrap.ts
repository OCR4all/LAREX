import { computed } from 'vue'

export async function useWorkspaceBootstrap(options?: { validateSelection?: boolean }) {
  const workspace = useWorkspaceStore()
  const validateSelection = options?.validateSelection ?? true

  if (!workspace.hasFetched) {
    await workspace.fetchWorkspaces()
  }

  if (validateSelection) {
    await workspace.validateAndSelectWorkspace()
  }

  return {
    workspace,
    selectedWorkspace: computed(() => workspace.selectedWorkspaceId)
  }
}
