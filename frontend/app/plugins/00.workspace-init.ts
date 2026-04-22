import error from "~/error.vue";

/**
 * SSR Workspace Initialization Plugin
 *
 * Sets default workspace selection during SSR if cookie is empty.
 */
export default defineNuxtPlugin(async () => {
  const { loggedIn } = useUserSession()

  if (!import.meta.server || !loggedIn.value) return

  const workspaceStore = useWorkspaceStore()

  try {
    await workspaceStore.fetchWorkspaces()
    await workspaceStore.validateAndSelectWorkspace()
  } catch {
    console.error('Failed to initialize workspace store:', error)
  }
})
