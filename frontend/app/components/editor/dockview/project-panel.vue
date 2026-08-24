<script setup lang="ts">
import { DockviewVue, type DockviewReadyEvent, type DockviewTheme } from 'dockview-vue'
import { useEditorStore } from '@/stores/editor/editor.store'
import { useEditorSessionStore } from '@/stores/editor/editor.session.store'
import { getCanvasId, getPagePanelId, parsePagePanelId, parseProjectPanelId } from '@/stores/editor/editor.keys'
import { useProjectDockviewRegistry, type ProjectDockviewApi } from '@/composables/editor/use-project-dockview-registry'
import { useProjectTabCloseState } from '@/composables/editor/use-project-tab-close-state'

type ProjectPanelParams = {
  projectId?: string
  projectName?: string
}

type DockviewPanelProps<TParams> = {
  params: TParams
  api: { id: string, close?: () => void }
  containerApi: unknown
  tabLocation?: unknown
}

const props = defineProps<{ params: DockviewPanelProps<ProjectPanelParams> }>()

const editorStore = useEditorStore()
const sessionStore = useEditorSessionStore()
const registry = useProjectDockviewRegistry()
const projectTabCloseState = useProjectTabCloseState()
const colorMode = useColorMode()

const themeLarexLight: DockviewTheme = {
  name: 'larex-light',
  className: 'dockview-theme-larex-light',
  gap: 0
}

const themeLarexDark: DockviewTheme = {
  name: 'larex-dark',
  className: 'dockview-theme-larex-dark',
  gap: 0
}

const dockviewTheme = computed(() => colorMode.value === 'dark' ? themeLarexDark : themeLarexLight)

const projectId = computed(() => {
  return props.params.params?.projectId ?? parseProjectPanelId(props.params.api.id) ?? null
})

const innerDockviewApi = ref<ProjectDockviewApi | null>(null)
const requestedPagePanelIds = new Set<string>()

function getTitleForPage(pageId: string): string {
  const id = projectId.value
  if (!id) return pageId
  return editorStore.getPage(pageId, id)?.label ?? pageId
}

function ensurePagePanelExists(api: ProjectDockviewApi, pageId: string) {
  const id = projectId.value
  if (!id) return

  const panelId = getPagePanelId(id, pageId)
  if (api.getPanel(panelId) || requestedPagePanelIds.has(panelId)) return

  const canvasId = getCanvasId(id, pageId)
  const canvas = editorStore.canvases[canvasId]
  requestedPagePanelIds.add(panelId)
  api.addPanel({
    id: panelId,
    component: 'EditorDockviewDefaultPanel',
    tabComponent: 'EditorDockviewTab',
    title: getTitleForPage(pageId),
    inactive: projectTabCloseState.isPageReplacementActive(id),
    params: {
      projectId: id,
      pageId,
      canvasId,
      variantId: canvas?.imageVariantId ?? undefined
    }
  })
}

function restoreProjectPanels() {
  const id = projectId.value
  const api = innerDockviewApi.value
  if (!id || !api) return

  const openedPageIds = sessionStore.getOpenedPageIds(id)
  for (const pageId of openedPageIds) {
    ensurePagePanelExists(api, pageId)
  }

  const activePageId = sessionStore.getActivePageId(id)
  if (activePageId) {
    api.getPanel(getPagePanelId(id, activePageId))?.api.setActive()
  }
}

const onReady = (event: DockviewReadyEvent) => {
  const id = projectId.value
  if (!id) return

  innerDockviewApi.value = event.api
  restoreProjectPanels()
  registry.register(id, event.api)

  event.api.onWillShowOverlay((overlayEvent) => {
    const transfer = overlayEvent.getData()
    const sourcePanelId = transfer?.panelId
    if (!sourcePanelId) return
    const source = parsePagePanelId(sourcePanelId)
    if (!source) return
    if (source.projectId !== id) {
      overlayEvent.preventDefault()
    }
  })

  event.api.onWillDrop((dropEvent) => {
    const transfer = dropEvent.getData()
    const sourcePanelId = transfer?.panelId
    if (!sourcePanelId) return
    const source = parsePagePanelId(sourcePanelId)
    if (!source) return
    if (source.projectId !== id) {
      dropEvent.preventDefault()
    }
  })

  event.api.onDidActivePanelChange(({ panel }) => {
    if (!panel) return
    const parsed = parsePagePanelId(panel.id)
    if (!parsed) return

    editorStore.setActiveCanvas(getCanvasId(parsed.projectId, parsed.pageId))
    sessionStore.setActiveProject(parsed.projectId)
    sessionStore.setActivePage(parsed.projectId, parsed.pageId)
  })

  event.api.onDidRemovePanel((panel) => {
    const parsed = parsePagePanelId(panel.id)
    if (!parsed) return

    setTimeout(() => {
      const stillExists = innerDockviewApi.value?.getPanel(panel.id)
      if (stillExists) return

      const canvasId = getCanvasId(parsed.projectId, parsed.pageId)
      editorStore.unregisterCanvas(canvasId)
      sessionStore.removeOpenedPage(parsed.projectId, parsed.pageId)
      requestedPagePanelIds.delete(panel.id)

      if (
        sessionStore.getOpenedPageIds(parsed.projectId).length === 0
        && !projectTabCloseState.isExplicitClose(parsed.projectId)
        && !projectTabCloseState.isPageReplacementActive(parsed.projectId)
      ) {
        projectTabCloseState.markAutoClosed(parsed.projectId)
        props.params.api.close?.()
      }
    }, 100)
  })
}

watch(() => editorStore.canvases, () => {
  const id = projectId.value
  const api = innerDockviewApi.value
  if (!id || !api) return

  const canvases = Object.values(editorStore.canvases)
    .filter(canvas => canvas.projectId === id && canvas.pageId && !canvas.comparison)

  for (const canvas of canvases) {
    ensurePagePanelExists(api, canvas.pageId as string)
  }
}, { deep: true })

onBeforeUnmount(() => {
  const id = projectId.value
  if (!id) return
  registry.unregister(id)
  innerDockviewApi.value = null
})
</script>

<template>
  <div class="h-full w-full">
    <DockviewVue
      class="h-full w-full"
      :theme="dockviewTheme"
      right-header-actions-component="EditorDockviewTabGroupMaximizeButton"
      default-tab-component="EditorDockviewTab"
      @ready="onReady"
    />
  </div>
</template>
