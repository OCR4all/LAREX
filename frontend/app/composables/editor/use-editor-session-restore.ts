import { nextTick, onMounted, ref, watch, type ComputedRef, type Ref } from 'vue'
import type { DockviewReadyEvent } from 'dockview-vue'

type DockviewApi = DockviewReadyEvent['api']

type EditorSessionRestoreOptions = {
  route: {
    query: Record<string, unknown>
  }
  selectedWorkspace: ComputedRef<string | null | undefined>
  dockviewApi: Ref<DockviewApi | null>
  loadPreferences: () => Promise<void>
  clearSession: () => void
  resetEditorState: () => void
  shouldRestorePersistedSession: () => boolean
  loadPersistedSession: () => boolean
  hasSession: () => boolean
  workspaceId: ComputedRef<string | null | undefined>
  initWorkspaceSession: (workspaceId: string | null) => void
  openedProjectIds: ComputedRef<string[]>
  getOpenedPageIds: (projectId: string) => string[]
  getSelectedVariantIdByPageId: (projectId: string) => Record<string, string | null | undefined>
  ensureProjectPanelExists: (api: DockviewApi, projectId: string) => void
  openEditorForPage: (projectId: string, pageId: string, variantId?: string) => Promise<void>
  restorePersistedProject: (projectId: string) => Promise<void>
  loadProjectMetadata: (projectId: string) => Promise<void>
  applyEditorDeepLink: () => Promise<void>
  maybeAutoStartContextTour: (path: string, context: { editorMode: string }) => void | Promise<void>
  getEditorMode: () => string
}

function getSingleQueryValue(value: unknown): string | null {
  if (Array.isArray(value)) {
    const first = value[0]
    value = typeof first === 'string' ? first : null
  }

  if (typeof value !== 'string') return null

  const trimmed = value.trim()
  return trimmed.length > 0 ? trimmed : null
}

export function useEditorSessionRestore(options: EditorSessionRestoreOptions) {
  const initialPanelsCreated = ref(false)
  const isLoading = ref(true)
  const hasMounted = ref(false)

  function tryCreateInitialPanels() {
    if (initialPanelsCreated.value) return
    if (!options.dockviewApi.value) return
    if (options.openedProjectIds.value.length === 0) return

    initialPanelsCreated.value = true
    for (const projectId of options.openedProjectIds.value) {
      options.ensureProjectPanelExists(options.dockviewApi.value, projectId)
    }

    for (const projectId of options.openedProjectIds.value) {
      const openedPageIds = options.getOpenedPageIds(projectId)
      const variants = options.getSelectedVariantIdByPageId(projectId)
      for (const pageId of openedPageIds) {
        void options.openEditorForPage(projectId, pageId, variants[pageId] ?? undefined)
      }
    }

    nextTick(() => {
      isLoading.value = false
    })
  }

  onMounted(async () => {
    const deepLinkProjectId = getSingleQueryValue(options.route.query.projectId)
    const deepLinkPageId = getSingleQueryValue(options.route.query.pageId)
    const deepLinkScope = getSingleQueryValue(options.route.query.scope)
    const hasPageDeepLink = Boolean(deepLinkProjectId && deepLinkPageId)
    const hasProjectDeepLink = Boolean(deepLinkScope === 'project' && deepLinkProjectId && !deepLinkPageId)

    await options.loadPreferences()

    if (hasPageDeepLink || hasProjectDeepLink) {
      options.clearSession()
      options.resetEditorState()
      isLoading.value = false
      hasMounted.value = true
      void options.applyEditorDeepLink()
      return
    }

    const hasPersistedSession = options.loadPersistedSession()

    if (options.workspaceId.value && options.workspaceId.value !== options.selectedWorkspace.value) {
      options.initWorkspaceSession(options.selectedWorkspace.value ?? null)
    }

    if (options.shouldRestorePersistedSession() && hasPersistedSession && options.hasSession()) {
      try {
        for (const projectId of options.openedProjectIds.value) {
          await options.restorePersistedProject(projectId)
        }
      } catch (error) {
        console.error('Failed to restore editor session:', error)
        options.clearSession()
        isLoading.value = false
        hasMounted.value = true
        void options.applyEditorDeepLink()
        return
      }
    }

    if (options.openedProjectIds.value.length === 0) {
      isLoading.value = false
      hasMounted.value = true
      void options.applyEditorDeepLink()
      return
    }

    for (const projectId of options.openedProjectIds.value) {
      await options.loadProjectMetadata(projectId)
    }

    initialPanelsCreated.value = false
    tryCreateInitialPanels()
    hasMounted.value = true
    void options.applyEditorDeepLink()

    void options.maybeAutoStartContextTour('/editor', {
      editorMode: options.getEditorMode()
    })
  })

  watch(
    () => [
      options.route.query.projectId,
      options.route.query.pageId,
      options.route.query.variantId,
      options.route.query.scope
    ],
    () => {
      if (!hasMounted.value) return
      void options.applyEditorDeepLink()
    }
  )

  return {
    initialPanelsCreated,
    isLoading,
    hasMounted,
    tryCreateInitialPanels
  }
}
