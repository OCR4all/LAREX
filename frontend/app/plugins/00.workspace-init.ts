/**
 * SSR Workspace Initialization Plugin
 *
 * Sets default workspace selection during SSR if cookie is empty.
 */
export default defineNuxtPlugin(async () => {
  const { loggedIn } = useUserSession()

  if (!import.meta.server || !loggedIn.value) return

  const selectedWorkspaceId = useCookie<string | null>('selectedWorkspaceId')

  if (selectedWorkspaceId.value) return

  try {
    const workspaces = await $fetch<{ id: string }[]>('/api/workspaces')
    if (workspaces?.length) {
      selectedWorkspaceId.value = workspaces[0].id
    }
  } catch {
  }
})
