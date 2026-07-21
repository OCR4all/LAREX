import type { DockviewReadyEvent } from 'dockview-vue'
import { getCanvasId, getPagePanelId, getProjectPanelId, parseProjectPanelId } from '@/stores/editor/editor.keys'
import { useEditorStore } from '@/stores/editor/editor.store'
import { useEditorSessionStore } from '@/stores/editor/editor.session.store'
import type { useProjectDockviewRegistry } from '@/composables/editor/use-project-dockview-registry'
import type { useProjectTabCloseState } from '@/composables/editor/use-project-tab-close-state'

type RegisteredDockviewApi = Pick<DockviewReadyEvent['api'], 'addPanel' | 'getPanel' | 'onDidActivePanelChange' | 'onDidRemovePanel'>

type EditorDockviewTabsOptions = {
  getDockviewApi: () => RegisteredDockviewApi | null
  setDockviewApi: (api: DockviewReadyEvent['api']) => void
  projectDockviewRegistry: ReturnType<typeof useProjectDockviewRegistry>
  projectTabCloseState: ReturnType<typeof useProjectTabCloseState>
  clearProjectTabState: (projectId: string) => void
  tryCreateInitialPanels: () => void
}

export function useEditorDockviewTabs(options: EditorDockviewTabsOptions) {
  const editorStore = useEditorStore()
  const sessionStore = useEditorSessionStore()

  function getProjectTitle(projectId: string): string {
    const pages = editorStore.getProjectPages(projectId)
    return pages[0]?.projectName ?? projectId
  }

  function getPageTitle(projectId: string, pageId: string): string {
    return editorStore.getPage(pageId, projectId)?.label ?? pageId
  }

  function ensureProjectPanelExists(api: RegisteredDockviewApi, projectId: string) {
    const panelId = getProjectPanelId(projectId)
    const existing = api.getPanel(panelId)
    if (existing) return

    api.addPanel({
      id: panelId,
      component: 'EditorDockviewProjectPanel',
      tabComponent: 'EditorDockviewProjectTab',
      title: getProjectTitle(projectId),
      params: {
        projectId,
        projectName: getProjectTitle(projectId)
      }
    })
  }

  function ensurePagePanelExists(projectId: string, pageId: string) {
    const api = options.projectDockviewRegistry.get(projectId)
    if (!api) return

    const panelId = getPagePanelId(projectId, pageId)
    if (api.getPanel(panelId)) return

    const canvasId = getCanvasId(projectId, pageId)
    const canvas = editorStore.canvases[canvasId]
    api.addPanel({
      id: panelId,
      component: 'EditorDockviewDefaultPanel',
      tabComponent: 'EditorDockviewTab',
      title: getPageTitle(projectId, pageId),
      inactive: options.projectTabCloseState.isPageReplacementActive(projectId),
      params: {
        projectId,
        pageId,
        canvasId,
        variantId: canvas?.imageVariantId ?? undefined
      }
    })
  }

  function onReady(event: DockviewReadyEvent) {
    options.setDockviewApi(event.api)

    event.api.onDidActivePanelChange((panel) => {
      if (!panel) return
      const projectId = parseProjectPanelId(panel.id)
      if (projectId) {
        sessionStore.setActiveProject(projectId)
      }
    })

    event.api.onDidRemovePanel((panel) => {
      const panelId = panel.id
      setTimeout(() => {
        const stillExists = options.getDockviewApi()?.getPanel?.(panelId)
        if (stillExists) return

        const projectId = parseProjectPanelId(panelId)
        if (projectId) {
          options.projectTabCloseState.consumeAutoClosed(projectId)
          options.projectTabCloseState.consumeExplicitClose(projectId)
          options.clearProjectTabState(projectId)
        }
      }, 100)
    })

    for (const projectId of sessionStore.openedProjectIds) {
      ensureProjectPanelExists(event.api, projectId)
    }
    options.tryCreateInitialPanels()
  }

  return {
    getProjectTitle,
    getPageTitle,
    ensureProjectPanelExists,
    ensurePagePanelExists,
    onReady
  }
}
