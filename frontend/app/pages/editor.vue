<script setup lang="ts">
import 'dockview-vue/dist/styles/dockview.css'
import {
  LazyEditorModalOpenProjectPages,
  LazyEditorSlideoverMergeSettings,
  LazyEditorSlideoverUnsavedProgress,
  LazyEditorVersionHistorySlideover,
  LazyEditorModalPageVersionCompare,
  LazyProjectSlideoverXmlEditor,
  LazyCodecSlideoverAction,
  LazyActionSlideoverRun,
  LazyEditorSlideoverPageOrder,
  LazyUiConfirmSlideover
} from '#components'

import type { DockviewReadyEvent, DockviewTheme } from 'dockview-vue'
import * as dockviewVuePkg from 'dockview-vue'
import type { DockviewPanelApi } from 'dockview-core'

import type { DropdownMenuItem } from '@nuxt/ui'

import { useEditorStore } from '@/stores/editor/editor.store'
import { useEditorUiStore } from '@/stores/editor/editor.ui.store'
import {
  EDITOR_WORKSPACE_FLOATING_ANCHOR_ID,
  getEditorSession,
  registerEditorFloatingAnchor,
  unregisterEditorFloatingAnchor
} from '@/session/editor/editor-session'
import { PolygonType, createPageXmlLabelSet } from '@/models/editor'
import type { Region, RegionKind } from '@/models/editor/region'
import type { TextLine } from '@/models/editor/text'
import { redoSessionCommand, undoSessionCommand } from '@/session/editor/canvas-commander'
import { MergeElementsCommand } from '@/commands/editor/merge-elements-command'
import type { Commander } from '@/commands/editor/commander'
import type { MergeSettings } from '@/components/editor/slideover/merge-settings.vue'
import { createSkeletonPageData, type PageResponse } from '@/services/editor/project-loader'
import { useEditorSessionStore } from '@/stores/editor/editor.session.store'
import type { LabelSet as ApiLabelSet, LabelDefinition as ApiLabelDefinition } from '@/types/label-set'
import type { ValidateCodecAgainstSourcesResponse } from '@/types/codec'
import type { ActionPageResultEvent } from '@/stores/action-runs.store'
import type { ActionTargetSelection } from '@/types/action'
import type { Dictionary } from '@/types/dictionary'
import type { RenderablePolyline } from '@/types/editor/rendering'
import type { PageIndexingStatus } from '@/stores/editor/types'
import type { PageDto } from '@/types/page-dto'
import type { PageXmlVersion } from '@/types/version'
import { getCanvasId, getCompareCanvasId, getPagePanelId, getProjectPanelId } from '@/stores/editor/editor.keys'
import { DEFAULT_PAGE_SORT_MODE, sortPagesForEditor, type PageSortMode } from '@/utils/editor/page-sort'
import { useProjectDockviewRegistry } from '@/composables/editor/use-project-dockview-registry'
import { useProjectTabCloseState } from '@/composables/editor/use-project-tab-close-state'
import { useEditorCommandCenter } from '@/composables/editor/use-editor-command-center'
import type { OpenProjectPagesSelection } from '@/components/editor/modal/open-project-pages.vue'
import type { PageWorkflowState } from '@/types/project-page'

import EditorEmpty from '@/components/editor/empty.vue'
import { useEditorIndexStatusPolling } from '@/composables/editor/use-editor-index-status-polling'
import { useEditorMetadataApply } from '@/composables/editor/use-editor-metadata-apply'
import { useEditorSidebarState } from '@/composables/editor/use-editor-sidebar-state'
import { useEditorSessionRestore } from '@/composables/editor/use-editor-session-restore'
import { useEditorTaskState } from '@/composables/editor/use-editor-task-state'
import { useEditorCollaboration } from '@/composables/editor/use-editor-collaboration'
import { useEditorActiveCanvasStatus } from '@/composables/editor/use-editor-active-canvas-status'
import { useEditorDeepLinks } from '@/composables/editor/use-editor-deep-links'
import { useEditorDockviewTabs } from '@/composables/editor/use-editor-dockview-tabs'
import { useEditorLayoutState } from '@/composables/editor/use-editor-layout-state'
import { UpdateReadingOrderCommand } from '@/commands'
import type { ReadingOrder } from '@/models/editor'
import { resolveAdjacentPageId } from '@/utils/editor/page-navigation'
import { convertPageDtoToPcGts, convertPcGtsToPageDto } from '@/services/editor/page-conversion.service'

definePageMeta({ layout: 'editor' })

const DockviewVue = (dockviewVuePkg as { DockviewVue?: unknown }).DockviewVue
  ?? ((dockviewVuePkg as Record<string, unknown>)['default'] as { DockviewVue?: unknown } | undefined)?.DockviewVue

const route = useRoute()
const router = useRouter()
const colorMode = useColorMode()

const { maybeAutoStartContextTour } = useOnboarding()

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

const dockviewApi = ref<DockviewReadyEvent['api'] | null>(null)

function getErrorMessage(error: unknown, fallback: string): string {
  if (typeof error !== 'object' || error === null) return fallback
  const data = 'data' in error ? (error.data as { message?: unknown } | undefined) : undefined
  if (typeof data?.message === 'string' && data.message.trim().length > 0) return data.message
  if (error instanceof Error && error.message.trim().length > 0) return error.message
  return fallback
}

const MIN_LEFT_WIDTH_PX = 250
const MAX_LEFT_WIDTH_PX = 500
const MIN_RIGHT_WIDTH_PX = 250
const MAX_RIGHT_WIDTH_PX = 800

type ResizeSide = 'left' | 'right'
const resizingSide = ref<ResizeSide | null>(null)
let resizeStartX = 0
let resizeStartWidth = 0
let rafLayoutId: number | null = null
const editorWorkspaceAnchorRef = ref<HTMLElement | null>(null)
let registeredEditorWorkspaceAnchorElement: HTMLElement | null = null

function syncEditorWorkspaceAnchorRegistration(): void {
  if (!import.meta.client) return

  const nextElement = editorWorkspaceAnchorRef.value
  if (registeredEditorWorkspaceAnchorElement === nextElement) return

  if (registeredEditorWorkspaceAnchorElement) {
    unregisterEditorFloatingAnchor(EDITOR_WORKSPACE_FLOATING_ANCHOR_ID, registeredEditorWorkspaceAnchorElement)
  }

  registeredEditorWorkspaceAnchorElement = nextElement

  if (registeredEditorWorkspaceAnchorElement) {
    registerEditorFloatingAnchor(EDITOR_WORKSPACE_FLOATING_ANCHOR_ID, registeredEditorWorkspaceAnchorElement)
  }
}

const logoMenuItems: DropdownMenuItem[][] = [[
  {
    label: 'Back to Dashboard',
    icon: 'i-lucide-arrow-left',
    onSelect: () => {
      void navigateTo('/')
    }
  }
]]

const pageNameFilter = ref('')
const pageSortMode = ref<PageSortMode>(DEFAULT_PAGE_SORT_MODE)
const editorWorkflowStateOptions: Array<{ label: string, value: PageWorkflowState, icon: string }> = [
  { label: 'Open', value: 'OPEN', icon: 'i-lucide-circle' },
  { label: 'In progress', value: 'IN_PROGRESS', icon: 'i-lucide-loader-circle' },
  { label: 'Done', value: 'DONE', icon: 'i-lucide-circle-check' }
]

const editorStore = useEditorStore()
const actionRunsStore = useActionRunsStore()
const editorUiStore = useEditorUiStore()
const imageLoader = useEditorImageLoader()
const sessionStore = useEditorSessionStore()

const {
  rootLayoutClass,
  toolbarClass,
  contentClass,
  statusBarClass
} = useEditorLayoutState(computed(() => editorStore.toolbarLayout))

const collaboration = useEditorCollaboration()
const projectDockviewRegistry = useProjectDockviewRegistry()
const projectTabCloseState = useProjectTabCloseState()

const {
  getProjectTitle,
  ensureProjectPanelExists,
  onReady
} = useEditorDockviewTabs({
  getDockviewApi: () => dockviewApi.value,
  setDockviewApi: (api) => {
    dockviewApi.value = api
  },
  projectDockviewRegistry,
  projectTabCloseState,
  clearProjectTabState,
  tryCreateInitialPanels: () => tryCreateInitialPanels()
})

const toast = useToast()
const { refreshTaskCaches } = useDataRefresh()
const { selectedWorkspace } = await useWorkspaceBootstrap()
const loadedPageIdsForCodecValidation = computed(() => {
  const projectId = currentProjectId.value
  if (!projectId) return []
  return [...new Set(Object.values(editorStore.canvases)
    .filter(canvas => canvas.projectId === projectId)
    .map(canvas => canvas.pageId)
    .filter((pageId): pageId is string => typeof pageId === 'string' && pageId.trim().length > 0))]
})
const canCheckCodecForLoadedPages = computed(() => {
  return Boolean(
    selectedWorkspace.value
    && currentProjectId.value
    && loadedPageIdsForCodecValidation.value.length > 0
  )
})

const currentProjectId = computed(() => editorStore.currentProjectId ?? sessionStore.activeProjectId)
const currentProjectIdForFilter = computed<string | undefined>(() => currentProjectId.value ?? undefined)
useEditorIndexStatusPolling()

const {
  hasActiveFilters: hasAdvancedFilters,
  hasBackendFilters,
  isFiltering,
  labelIds,
  textContent: backendTextContentFilter,
  tags: backendTagsFilter,
  filterOperator: backendFilterOperator,
  confidenceRange: backendConfidenceRange,
  confidenceElementTypes: backendConfidenceElementTypes,
  hasComments: backendHasComments,
  onlyWithOpenSubtasks,
  workflowStates: pageWorkflowStateFilters
} = usePageFilter(currentProjectIdForFilter)

const availableLabelsForFilter = computed<ApiLabelDefinition[]>(() => {
  const labelSet = editorStore.labelSet
  if (!labelSet || !Array.isArray(labelSet.labels)) return []
  return labelSet.labels.map(label => ({
    id: label.id,
    name: label.name,
    scope: label.scope as ApiLabelDefinition['scope'],
    color: label.color ?? '#6b7280',
    description: label.description,
    hasText: label.hasText,
    isContainer: label.isContainer,
    group: label.group ?? null,
    mapping: label.mapping as ApiLabelDefinition['mapping']
  }))
})

const availableTags = computed(() => {
  const tagMap = new Map<string, { count: number }>()
  for (const page of editorStore.pages) {
    for (const tag of page.tags ?? []) {
      const existing = tagMap.get(tag)
      if (existing) {
        existing.count++
      } else {
        tagMap.set(tag, { count: 1 })
      }
    }
  }
  return Array.from(tagMap.entries()).map(([tag, { count }]) => ({
    label: tag,
    value: tag,
    count
  }))
})

watch(() => editorStore.currentPageId, (newPageId) => {
  if (!newPageId) return

  const pages = editorStore.pages
  if (pages.length === 0) return

  imageLoader.prefetchImagesBidirectional(newPageId, pages, 5, 5)
})

const overlay = useOverlay()
const mergeSettingsSlideover = overlay.create(LazyEditorSlideoverMergeSettings)
const unsavedProgressSlideover = overlay.create(LazyEditorSlideoverUnsavedProgress)
const versionHistorySlideover = overlay.create(LazyEditorVersionHistorySlideover)
const pageVersionCompareModal = overlay.create(LazyEditorModalPageVersionCompare)
const xmlEditorSlideover = overlay.create(LazyProjectSlideoverXmlEditor)
const codecActionSlideover = overlay.create(LazyCodecSlideoverAction)
const actionRunSlideover = overlay.create(LazyActionSlideoverRun)
const pageOrderSlideover = overlay.create(LazyEditorSlideoverPageOrder)
const openProjectPagesModal = overlay.create(LazyEditorModalOpenProjectPages)
const confirmSlideover = overlay.create(LazyUiConfirmSlideover)
const handledActionPageResultEvents = ref<Set<number>>(new Set())
let editorActionIndexStatusTimers: Array<ReturnType<typeof setTimeout>> = []

type SelectionOpenSource = 'modal' | 'project-search' | 'page-search'

async function openSelectionsInEditor(
  selections: OpenProjectPagesSelection,
  source: SelectionOpenSource
) {
  if (!selectedWorkspace.value || selections.length === 0) return

  sessionStore.initWorkspaceSession(selectedWorkspace.value)

  for (const selection of selections) {
    const projectId = selection.projectId
    const projectName = selection.projectName
    sessionStore.addOpenedProject(projectId)

    let allPages: PageResponse[] = []

    try {
      allPages = await $fetch<PageResponse[]>(`/api/projects/${projectId}/pages`)
    } catch (err: unknown) {
      toast.add({
        title: 'Failed to load project pages',
        description: getErrorMessage(err, 'Could not load pages for this project.'),
        color: 'error'
      })
      continue
    }

    const selectedPages = selection.pageIds
      ? allPages.filter(page => selection.pageIds?.includes(page.id))
      : allPages

    const skeletonPages = createSkeletonPageData(selectedPages, {
      projectId,
      projectName
    })
    editorStore.appendProjectPages(projectId, skeletonPages)
    await loadProjectLabelSet(projectId)

    const pageIdsToOpen = new Set<string>()
    if (selection.pageIds && selection.pageIds.length > 0) {
      for (const pageId of selection.pageIds) {
        pageIdsToOpen.add(pageId)
      }
    }
    if (source === 'project-search' && selection.pageIds === null) {
      const firstPageId = selectedPages[0]?.id
      if (firstPageId) {
        pageIdsToOpen.add(firstPageId)
      }
    }

    for (const pageId of pageIdsToOpen) {
      const page = editorStore.getPage(pageId, projectId)
      if (!page) continue
      const variant = editorStore.getDisplayedVariantForPage(page)
      await openEditorForPage(projectId, pageId, variant?.id ?? undefined)
    }
  }

  if (source === 'modal' && !editorStore.activeCanvasId) {
    const firstSelection = selections[0]
    if (!firstSelection) return
    const projectId = firstSelection.projectId
    const firstPage = editorStore.getProjectPages(projectId)[0]
    if (!firstPage) return
    const variant = editorStore.getDisplayedVariantForPage(firstPage)
    await openEditorForPage(projectId, firstPage.id, variant?.id ?? undefined)
  }
}

async function handleOpenProjectsModal() {
  if (!selectedWorkspace.value) {
    toast.add({
      title: 'No workspace selected',
      description: 'Select a workspace first.',
      color: 'warning'
    })
    return
  }

  const instance = openProjectPagesModal.open({
    workspaceId: selectedWorkspace.value
  })

  const result = await instance.result as OpenProjectPagesSelection | null
  if (!result || result.length === 0) return
  await openSelectionsInEditor(result, 'modal')
}

const {
  open: commandCenterOpen,
  searchTerm: commandCenterSearchTerm,
  isLoading: isCommandCenterLoading,
  groups: commandCenterGroups,
  openCommandCenter
} = useEditorCommandCenter({
  openProjectModal: async () => {
    await handleOpenProjectsModal()
  },
  openProjectSelection: async (selection, source) => {
    await openSelectionsInEditor([selection], source)
  }
})

const closeRequests = useEditorCloseRequests()

const unsavedCanvasEntries = computed(() => {
  return Object.entries(editorStore.canvases)
    .filter(([, canvas]) => !!canvas.pageId && canvas.hasUnsavedChanges)
    .map(([id, canvas]) => ({ id, canvas }))
})

const unsavedPageLabels = computed(() => {
  return unsavedCanvasEntries.value.map(({ canvas }) => {
    const pageId = canvas.pageId as string
    return editorStore.getPage(pageId, canvas.projectId ?? undefined)?.label ?? pageId
  })
})

async function handleCloseRequest(params: { panelApi: DockviewPanelApi, projectId?: string | null, pageId?: string | null }) {
  const projectId = params.projectId ?? null
  const pageId = params.pageId ?? null

  if (projectId && !pageId) {
    const projectEntries = unsavedCanvasEntries.value.filter(({ canvas }) => canvas.projectId === projectId)
    if (projectEntries.length === 0) {
      projectTabCloseState.markExplicitClose(projectId)
      params.panelApi.close()
      return
    }

    const pages = projectEntries.map(({ canvas }) => {
      const page = editorStore.getPage(canvas.pageId as string, canvas.projectId ?? undefined)
      return page?.label ?? canvas.pageId ?? 'Unknown page'
    })

    const instance = unsavedProgressSlideover.open({
      title: 'Unsaved changes',
      message: 'This project contains pages with unsaved changes. What would you like to do?',
      confirmLabel: 'Save all and close',
      discardLabel: 'Close anyway',
      cancelLabel: 'Cancel',
      confirmColor: 'primary',
      discardColor: 'warning',
      pages
    })
    const action = await instance.result
    if (action === 'save') {
      const results = await Promise.all(projectEntries.map(entry => editorStore.saveAnnotations(entry.id)))
      if (!results.every(Boolean)) {
        toast.add({
          title: 'Save failed',
          description: 'Some pages could not be saved. Please try again.',
          color: 'error',
          icon: 'i-lucide-alert-circle'
        })
        return
      }
      projectTabCloseState.markExplicitClose(projectId)
      params.panelApi.close()
      return
    }
    if (action === 'discard') {
      projectTabCloseState.markExplicitClose(projectId)
      params.panelApi.close()
    }
    return
  }

  if (!pageId || !projectId) {
    params.panelApi.close()
    return
  }

  const canvasId = getCanvasId(projectId, pageId)
  const canvas = editorStore.canvases[canvasId]
  const hasUnsaved = canvas?.hasUnsavedChanges === true
  if (!hasUnsaved) {
    params.panelApi.close()
    return
  }

  const pageLabel = editorStore.getPage(pageId, projectId)?.label ?? pageId
  const instance = unsavedProgressSlideover.open({
    title: 'Unsaved changes',
    message: `"${pageLabel}" has unsaved changes. What would you like to do?`,
    confirmLabel: 'Save and close',
    discardLabel: 'Close anyway',
    cancelLabel: 'Cancel',
    confirmColor: 'primary',
    discardColor: 'warning'
  })
  const action = await instance.result
  if (action === 'save') {
    const saved = await editorStore.saveAnnotations(canvasId)
    if (!saved) {
      toast.add({
        title: 'Save failed',
        description: 'Could not save annotations. Please try again.',
        color: 'error',
        icon: 'i-lucide-alert-circle'
      })
      return
    }
    params.panelApi.close()
    return
  }
  if (action === 'discard') {
    params.panelApi.close()
  }
}

closeRequests.on((params) => {
  void handleCloseRequest(params)
})

if (import.meta.client) {
  const onBeforeUnload = (e: BeforeUnloadEvent) => {
    if (unsavedCanvasEntries.value.length > 0) {
      e.preventDefault()
    }
  }
  onMounted(() => window.addEventListener('beforeunload', onBeforeUnload))
  onBeforeUnmount(() => window.removeEventListener('beforeunload', onBeforeUnload))
}

let shouldCleanupOnUnmount = false

onBeforeRouteLeave(async (to) => {
  if (to.path.startsWith('/editor')) {
    shouldCleanupOnUnmount = false
    return
  }

  shouldCleanupOnUnmount = true

  if (unsavedCanvasEntries.value.length === 0) {
    return
  }

  const instance = unsavedProgressSlideover.open({
    title: 'Unsaved changes',
    message: 'You have unsaved changes on multiple pages. What would you like to do?',
    confirmLabel: 'Save all',
    discardLabel: 'Discard all',
    cancelLabel: 'Cancel',
    confirmColor: 'primary',
    discardColor: 'warning',
    pages: unsavedPageLabels.value
  })

  const action = await instance.result
  if (action === 'save') {
    const results = await Promise.all(
      unsavedCanvasEntries.value.map(entry => editorStore.saveAnnotations(entry.id))
    )
    if (results.every(Boolean)) {
      return
    }

    toast.add({
      title: 'Save failed',
      description: 'Some pages could not be saved. Please try again.',
      color: 'error',
      icon: 'i-lucide-alert-circle'
    })
    shouldCleanupOnUnmount = false
    return false
  }

  if (action === 'discard') {
    return
  }

  shouldCleanupOnUnmount = false
  return false
})

async function openMergeSettingsSlideover(kinds: RegionKind[]): Promise<MergeSettings | null> {
  const instance = mergeSettingsSlideover.open({ availableKinds: kinds, defaultKind: kinds[0] })
  return await instance.result
}

async function handleMergeSelected() {
  const controls = activeControls.value
  if (!controls) return
  if (!controls.isCanvasEditable.value) return

  const selectedPolygonIds = controls.selectedPolygonIds?.value ?? []
  if (selectedPolygonIds.length < 2) return

  const polygons = controls.polygons ?? []
  const selectedPolygons = polygons.filter(p => selectedPolygonIds.includes(p.id))
  if (selectedPolygons.length < 2) return

  const types = new Set(selectedPolygons.map(p => p.type))
  if (types.size !== 1) return

  const type = selectedPolygons[0]?.type
  const elementType = type === PolygonType.REGION ? 'region' : type === PolygonType.TEXTLINE ? 'textline' : null
  if (!elementType) return

  let targetKind: RegionKind | undefined
  let mergeChildren = true

  if (elementType === 'region') {
    const kinds = [...new Set(
      selectedPolygons
        .map(p => p.regionKind)
        .filter((kind): kind is RegionKind => Boolean(kind))
    )]
    if (kinds.length > 1) {
      const settings = await openMergeSettingsSlideover(kinds)
      if (!settings) return
      targetKind = settings.targetKind
      mergeChildren = settings.mergeChildren
    } else {
      targetKind = kinds[0]
    }
  }

  const canvasId = activeCanvasId.value
  if (!canvasId) return

  const session = getEditorSession(canvasId)
  if (!session) return

  const command = new MergeElementsCommand({
    elementIds: selectedPolygonIds,
    elementType,
    targetKind,
    mergeChildren
  })

  const commander = controls.commander
  commander?.execute(command, { canvasId, session })

  if (controls.selectedPolygonIds) {
    controls.selectedPolygonIds.value = []
  }
}

async function handleSaveDocument() {
  if (isSavingActiveCanvas.value) {
    return false
  }

  const canvasId = editorStore.activeCanvasId
  if (!canvasId) {
    toast.add({
      title: 'No active canvas',
      description: 'Please open a page before saving.',
      color: 'warning'
    })
    return false
  }

  if (!collaboration.canEditCanvas(canvasId)) {
    toast.add({
      title: 'Page is locked',
      description: 'You are currently in read-only mode for this page.',
      color: 'warning'
    })
    return false
  }

  try {
    const success = await editorStore.saveAnnotations(canvasId)

    if (success) {
      toast.add({
        title: 'Saved',
        description: 'Annotations saved successfully.',
        color: 'success'
      })
    } else {
      toast.add({
        title: 'Save failed',
        description: 'Could not save annotations. Check the console for details.',
        color: 'error'
      })
    }
    return success
  } catch (err: unknown) {
    toast.add({
      title: 'Save failed',
      description: getErrorMessage(err, 'An unexpected error occurred.'),
      color: 'error'
    })
    return false
  }
}

function resolveCanvasAnnotationContext(canvasId: string) {
  const canvas = editorStore.canvases[canvasId]
  if (!canvas?.projectId || !canvas.pageId) return null
  if (canvas.annotationContext?.basePath) return canvas.annotationContext
  const page = editorStore.getPage(canvas.pageId, canvas.projectId)
  if (page?.annotationContext?.basePath) return page.annotationContext
  return {
    mode: 'PROJECT' as const,
    basePath: `/api/projects/${canvas.projectId}/pages/${canvas.pageId}/annotations`,
    createAllowed: true
  }
}

async function openVersionHistory() {
  const canvasId = editorStore.activeCanvasId
  if (!canvasId) return
  const canvas = editorStore.canvases[canvasId]
  if (!canvas?.xmlFileId || !canvas?.pageId || !canvas?.projectId) return
  const annotationContext = resolveCanvasAnnotationContext(canvasId)
  if (!annotationContext) return

  const instance = versionHistorySlideover.open({
    projectId: canvas.projectId,
    pageId: canvas.pageId,
    xmlId: canvas.xmlFileId,
    annotationBasePath: annotationContext.basePath,
    canRestore: collaboration.canEditCanvas(canvasId),
    canCompare: true,
    hasUnsavedChanges: canvas.hasUnsavedChanges === true
  })
  const result = await instance.result
  if (result === 'restored') {
    editorStore.invalidateAnnotationCache(canvas.pageId, canvas.projectId)
    await editorStore.loadPageIntoCanvas(canvasId, canvas.projectId, canvas.pageId)
  } else if (result && typeof result === 'object' && result.action === 'compare') {
    await openVersionComparison(canvasId, result.version, annotationContext.basePath)
  }
}

async function openVersionComparison(canvasId: string, version: PageXmlVersion, annotationBasePath: string) {
  const canvas = editorStore.canvases[canvasId]
  const document = activeDocument.value
  if (!canvas?.xmlFileId || !canvas.pageId || !canvas.projectId || !document) {
    toast.add({
      title: 'Compare unavailable',
      description: 'Open a PAGE XML document before comparing versions.',
      color: 'warning'
    })
    return
  }

  try {
    const comparisonId = `version-${version.id}`
    const currentCanvasId = getCompareCanvasId(canvas.projectId, canvas.pageId, comparisonId, 'current')
    const versionCanvasId = getCompareCanvasId(canvas.projectId, canvas.pageId, comparisonId, 'version')
    const page = editorStore.getPage(canvas.pageId, canvas.projectId)
    const pageLabel = page?.label ?? canvas.pageId
    const imageSrc = canvas.imageSrc ?? ''
    const imageVariantId = canvas.imageVariantId ?? null
    const currentSnapshot = convertPcGtsToPageDto(document)
    const compared = await $fetch<PageDto>(`${annotationBasePath}/${canvas.xmlFileId}/versions/${version.id}/annotation`)

    if (!editorStore.canvases[currentCanvasId]) {
      editorStore.registerCanvas(currentCanvasId, {
        projectId: canvas.projectId,
        pageId: canvas.pageId,
        imageVariantId,
        imageSrc,
        xmlFileId: canvas.xmlFileId,
        annotationContext: canvas.annotationContext ?? { mode: 'PROJECT', basePath: annotationBasePath, createAllowed: false },
        isLoadingAnnotations: false,
        comparison: {
          id: comparisonId,
          source: 'version',
          side: 'current',
          readOnly: true,
          baseCanvasId: canvasId,
          pairedCanvasId: versionCanvasId,
          version
        }
      })
      editorStore.setCanvasDocument(currentCanvasId, convertPageDtoToPcGts(currentSnapshot))
    }

    if (!editorStore.canvases[versionCanvasId]) {
      editorStore.registerCanvas(versionCanvasId, {
        projectId: canvas.projectId,
        pageId: canvas.pageId,
        imageVariantId,
        imageSrc,
        xmlFileId: canvas.xmlFileId,
        annotationContext: canvas.annotationContext ?? { mode: 'PROJECT', basePath: annotationBasePath, createAllowed: false },
        isLoadingAnnotations: false,
        comparison: {
          id: comparisonId,
          source: 'version',
          side: 'version',
          readOnly: true,
          baseCanvasId: canvasId,
          pairedCanvasId: currentCanvasId,
          version
        }
      })
      editorStore.setCanvasDocument(versionCanvasId, convertPageDtoToPcGts(compared))
    }

    const instance = pageVersionCompareModal.open({
      pageLabel,
      annotationBasePath,
      xmlId: canvas.xmlFileId,
      currentCanvasId,
      versionCanvasId,
      currentPage: currentSnapshot,
      initialVersionPage: compared,
      initialVersion: version,
      canRestore: collaboration.canEditCanvas(canvasId),
      hasUnsavedChanges: canvas.hasUnsavedChanges === true,
      gtIndex: editorStore.projectTextDefaultGtIndex
    })
    const result = await instance.result
    editorStore.unregisterCanvas(currentCanvasId)
    editorStore.unregisterCanvas(versionCanvasId)
    editorStore.setActiveCanvas(canvasId)
    if (result === 'restored') {
      editorStore.invalidateAnnotationCache(canvas.pageId, canvas.projectId)
      await editorStore.loadPageIntoCanvas(canvasId, canvas.projectId, canvas.pageId)
    }
  } catch (error: unknown) {
    toast.add({
      title: 'Compare failed',
      description: getErrorMessage(error, 'Failed to load the selected version.'),
      color: 'error'
    })
  }
}

async function openXmlEditor() {
  const canvasId = editorStore.activeCanvasId
  if (!canvasId) return

  const canvas = editorStore.canvases[canvasId]
  if (!canvas?.xmlFileId || !canvas?.pageId || !canvas?.projectId) {
    toast.add({
      title: 'XML unavailable',
      description: 'This page does not have a PAGE XML file yet.',
      color: 'warning'
    })
    return
  }
  const annotationContext = resolveCanvasAnnotationContext(canvasId)
  if (!annotationContext) return

  const room = collaboration.getRoomForCanvas(canvasId)
  const canEditXml = room?.identity.canEdit ?? activeCanvasCanEdit.value
  const pageName = editorStore.getPage(canvas.pageId, canvas.projectId)?.label ?? canvas.pageId

  const instance = xmlEditorSlideover.open({
    projectId: canvas.projectId,
    pageId: canvas.pageId,
    xmlId: canvas.xmlFileId,
    xmlBasePath: annotationContext.basePath.endsWith('/annotations')
      ? `${annotationContext.basePath.slice(0, -'/annotations'.length)}/xml`
      : annotationContext.basePath.replace(/\/annotations$/, '/xml'),
    pageName,
    readOnly: !canEditXml,
    readOnlyMessage: canEditXml
      ? undefined
      : 'You can view the XML, but only users with edit rights for this page can save changes.'
  })

  const result = await instance.result
  if (result === 'saved') {
    editorStore.invalidateAnnotationCache(canvas.pageId, canvas.projectId)
    await editorStore.loadPageIntoCanvas(canvasId, canvas.projectId, canvas.pageId)
  }
}

async function openCodecValidationForLoadedPages() {
  if (!selectedWorkspace.value || !currentProjectId.value) {
    toast.add({
      title: 'Codec check unavailable',
      description: 'Open a project in the editor first.',
      color: 'warning'
    })
    return
  }

  if (loadedPageIdsForCodecValidation.value.length === 0) {
    toast.add({
      title: 'No loaded pages',
      description: 'Load at least one page in the editor to run codec validation.',
      color: 'warning'
    })
    return
  }

  const instance = codecActionSlideover.open({
    mode: 'validate',
    workspaceId: selectedWorkspace.value,
    sources: [
      {
        projectId: currentProjectId.value,
        pageIds: loadedPageIdsForCodecValidation.value
      }
    ],
    defaultCodecId: editorStore.projectCodecId
  })

  await instance.result as ValidateCodecAgainstSourcesResponse | null
}

async function openActionRunForEditorTarget(payload: { targetSelection: ActionTargetSelection, targetSummary: string }) {
  if (!selectedWorkspace.value || !currentProjectId.value) {
    toast.add({
      title: 'Action unavailable',
      description: 'Open a project in the editor first.',
      color: 'warning'
    })
    return
  }

  const pageIds = payload.targetSelection.pages.map(page => page.pageId)
  const pages = pageIds.map((pageId) => {
    const page = editorStore.getPage(pageId, currentProjectId.value ?? undefined)
    return {
      id: pageId,
      name: page?.label ?? pageId,
      imageCount: page?.imageCount ?? page?.imageVariants?.length ?? 0,
      xmlFileCount: page?.xmlFileCount ?? page?.xmlFiles?.length ?? 0,
      imageVariants: (page?.imageVariants ?? []).map(variant => ({
        id: variant.id,
        fileName: variant.fileName ?? variant.label,
        variant: variant.type ?? variant.label
      }))
    }
  })

  const unsavedEntries = pageIds
    .map(pageId => ({ pageId, canvasId: getCanvasId(currentProjectId.value as string, pageId) }))
    .filter(entry => editorStore.canvases[entry.canvasId]?.hasUnsavedChanges === true)
  if (unsavedEntries.length > 0) {
    const pageNames = unsavedEntries
      .map(entry => editorStore.getPage(entry.pageId, currentProjectId.value ?? undefined)?.label ?? entry.pageId)
      .join(', ')
    const confirmation = confirmSlideover.open({
      title: 'Save before running Action?',
      message: `Actions process the saved PAGE XML. Save changes for ${pageNames} before continuing?`,
      confirmLabel: 'Save and Continue',
      cancelLabel: 'Cancel',
      confirmColor: 'primary',
      confirmIcon: 'i-lucide-save',
      showCancel: true
    })
    const confirmed = await confirmation.result
    if (!confirmed) return

    const saved = await Promise.all(unsavedEntries.map(entry => editorStore.saveAnnotations(entry.canvasId)))
    if (!saved.every(Boolean)) {
      toast.add({
        title: 'Save failed',
        description: 'Could not save all annotations. Please try again before running an Action.',
        color: 'error',
        icon: 'i-lucide-alert-circle'
      })
      return
    }
  }

  const instance = actionRunSlideover.open({
    workspaceId: selectedWorkspace.value,
    projectId: currentProjectId.value,
    projectName: getProjectTitle(currentProjectId.value),
    pageIds,
    pages,
    targetSelection: payload.targetSelection,
    targetSummary: payload.targetSummary
  })
  const changed = await instance.result
  if (changed) {
    for (const pageId of pageIds) {
      editorStore.invalidateAnnotationCache(pageId, currentProjectId.value)
      const canvasId = getCanvasId(currentProjectId.value, pageId)
      if (editorStore.canvases[canvasId]) {
        await editorStore.loadPageIntoCanvas(canvasId, currentProjectId.value, pageId)
      }
    }
  }
}

async function refreshActionRunPageSummaries(projectId: string, pageIds: string[]) {
  if (pageIds.length === 0) return

  try {
    const pages = await Promise.all(pageIds.map(pageId =>
      $fetch<PageResponse>(`/api/projects/${projectId}/pages/${pageId}`)
    ))
    editorStore.patchProjectPageSummaries(projectId, pages)
  } catch (error) {
    console.error(`Failed to refresh page summaries after Action run for project ${projectId}:`, error)
  }
}

async function refreshActionRunIndexStatuses(projectId: string, pageIds: string[]) {
  if (pageIds.length === 0) return

  try {
    const affectedPageIds = new Set(pageIds)
    const statuses = await $fetch<Record<string, PageIndexingStatus>>(`/api/projects/${projectId}/pages/index-statuses`)
    editorStore.patchPageIndexingStatuses(
      projectId,
      Object.fromEntries(Object.entries(statuses).filter(([pageId]) => affectedPageIds.has(pageId)))
    )
  } catch (error) {
    console.error(`Failed to refresh page index statuses after Action run for project ${projectId}:`, error)
  }
}

function scheduleActionRunIndexStatusRefresh(projectId: string, pageIds: string[]) {
  if (pageIds.length === 0) return

  for (const delayMs of [0, 1500, 5000]) {
    const timer = setTimeout(() => {
      editorActionIndexStatusTimers = editorActionIndexStatusTimers.filter(candidate => candidate !== timer)
      void refreshActionRunIndexStatuses(projectId, pageIds)
    }, delayMs)
    editorActionIndexStatusTimers.push(timer)
  }
}

async function reloadPageTouchedByActionResult(event: ActionPageResultEvent) {
  const canvasId = getCanvasId(event.projectId, event.pageId)
  const canvas = editorStore.canvases[canvasId]
  if (!canvas) return
  if (canvas.hasUnsavedChanges) {
    toast.add({
      title: 'Action result ready',
      description: 'This page changed in the background. Save or discard your editor changes before reloading it.',
      color: 'warning'
    })
    return
  }

  try {
    const previousPage = editorStore.getPage(event.pageId, event.projectId)
    const previousVariant = previousPage?.imageVariants?.find(variant => variant.id === canvas.imageVariantId)
    const logicalVariantKey = previousVariant?.type ?? previousVariant?.label ?? null

    await refreshActionRunPageSummaries(event.projectId, [event.pageId])
    await editorStore.refreshPageData(event.projectId, event.pageId)
    const refreshedPage = editorStore.getPage(event.pageId, event.projectId)
    const refreshedVariantId = logicalVariantKey
      ? refreshedPage?.imageVariants?.find(variant => (variant.type ?? variant.label) === logicalVariantKey)?.id
      : undefined

    editorStore.invalidateAnnotationCache(event.pageId, event.projectId)
    await editorStore.loadPageIntoCanvas(canvasId, event.projectId, event.pageId, refreshedVariantId)
    if (event.resultTypes.length === 0 || event.resultTypes.includes('xml')) {
      scheduleActionRunIndexStatusRefresh(event.projectId, [event.pageId])
    }
  } catch (error) {
    console.error(`Failed to reload Action result page ${event.pageId}:`, error)
    toast.add({
      title: 'Could not reload Action result',
      description: 'Reload the page to fetch the latest images and annotations.',
      color: 'error'
    })
  }
}

function handleEditorActionTargetEvent(event: Event) {
  const customEvent = event as CustomEvent<{ targetSelection: ActionTargetSelection, targetSummary: string }>
  if (!customEvent.detail?.targetSelection) return
  void openActionRunForEditorTarget(customEvent.detail)
}

if (import.meta.client) {
  onMounted(() => {
    window.addEventListener('larex:editor-action-target', handleEditorActionTargetEvent)
  })
  onBeforeUnmount(() => {
    window.removeEventListener('larex:editor-action-target', handleEditorActionTargetEvent)
    for (const timer of editorActionIndexStatusTimers) {
      clearTimeout(timer)
    }
    editorActionIndexStatusTimers = []
  })
}

watch(() => actionRunsStore.pageResultEvents, (events) => {
  for (const event of events) {
    if (handledActionPageResultEvents.value.has(event.sequence)) continue
    handledActionPageResultEvents.value = new Set([...handledActionPageResultEvents.value, event.sequence].slice(-100))
    void reloadPageTouchedByActionResult(event)
  }
}, { deep: false })

const rightSidebarActionItems = computed<DropdownMenuItem[][]>(() => {
  const pageActions: DropdownMenuItem[] = [
    {
      label: 'Version History',
      icon: 'i-lucide-history',
      onSelect: () => {
        void openVersionHistory()
      }
    },
    {
      label: 'View/Edit XML',
      icon: 'i-lucide-file-pen-line',
      disabled: !canOpenActiveCanvasXmlEditor.value,
      onSelect: () => {
        void openXmlEditor()
      }
    },
    {
      label: `Page state: ${editorWorkflowStateOptions.find(option => option.value === activeWorkflowState.value)?.label ?? 'Open'}`,
      icon: 'i-lucide-list-checks',
      children: editorWorkflowStateOptions.map(option => ({
        label: option.label,
        icon: option.icon,
        type: 'checkbox' as const,
        checked: activeWorkflowState.value === option.value,
        disabled: !canChangeActiveWorkflowState.value || activeWorkflowState.value === option.value,
        onSelect: () => {
          void updateActiveWorkflowState(option.value)
        }
      }))
    }
  ]

  if (activeUiMode.value === 'layout') {
    if (canCompleteActivePageSubtasks.value) {
      pageActions.push({
        label: 'Save + Complete',
        icon: 'i-lucide-check-square',
        disabled: isSavingActiveCanvas.value || isCompletingOpenSubtasks.value || isActivePageLocked.value || !activeCanvasCanEdit.value,
        onSelect: () => {
          void handleSaveAndCompleteOpenSubtasks()
        }
      })
    }
  }

  const toolkitActions: DropdownMenuItem[] = [
    {
      label: 'Check Codec',
      icon: 'i-lucide-badge-check',
      disabled: !canCheckCodecForLoadedPages.value,
      onSelect: () => {
        void openCodecValidationForLoadedPages()
      }
    }
  ]

  return [pageActions, toolkitActions]
})

const backendFilteredPageIdsByProjectId = ref<Record<string, string[]>>({})

watch(() => [...sessionStore.openedProjectIds], (projectIds) => {
  const openedIdSet = new Set(projectIds)
  backendFilteredPageIdsByProjectId.value = Object.fromEntries(
    Object.entries(backendFilteredPageIdsByProjectId.value).filter(([projectId]) => openedIdSet.has(projectId))
  )
}, { immediate: true })

const backendFilterSignature = computed(() => JSON.stringify({
  enabled: hasBackendFilters.value,
  labelIds: [...labelIds.value].sort(),
  textContent: backendTextContentFilter.value.trim(),
  tags: [...backendTagsFilter.value].sort(),
  confidenceRange: [...backendConfidenceRange.value],
  confidenceElementTypes: [...backendConfidenceElementTypes.value].sort(),
  hasComments: backendHasComments.value,
  filterOperator: backendFilterOperator.value,
  projectIds: [...sessionStore.openedProjectIds]
}))

async function refreshBackendFiltersForOpenedProjects() {
  if (!hasBackendFilters.value || sessionStore.openedProjectIds.length === 0) {
    backendFilteredPageIdsByProjectId.value = {}
    return
  }

  const requestBody = buildPageFilterRequestBody({
    labelIds: labelIds.value,
    textContent: backendTextContentFilter.value,
    tags: backendTagsFilter.value,
    filterOperator: backendFilterOperator.value,
    confidenceRange: backendConfidenceRange.value,
    confidenceElementTypes: backendConfidenceElementTypes.value,
    hasComments: backendHasComments.value,
    onlyWithOpenSubtasks: false
  })

  const projectIds = [...sessionStore.openedProjectIds]
  const results = await Promise.allSettled(
    projectIds.map(projectId =>
      $fetch<{ pageIds: string[], count: number }>(`/api/projects/${projectId}/pages/filter`, {
        method: 'POST',
        body: requestBody
      })
    )
  )

  const next: Record<string, string[]> = {}
  let hasError = false
  for (const [index, result] of results.entries()) {
    const projectId = projectIds[index]
    if (!projectId) continue
    if (result.status === 'fulfilled') {
      next[projectId] = result.value.pageIds ?? []
    } else {
      next[projectId] = []
      hasError = true
    }
  }

  backendFilteredPageIdsByProjectId.value = next
  if (hasError) {
    toast.add({
      title: 'Some filters failed',
      description: 'Could not apply backend filters for all opened projects.',
      color: 'warning'
    })
  }
}

const debouncedRefreshBackendFiltersForOpenedProjects = useDebounceFn(() => {
  void refreshBackendFiltersForOpenedProjects()
}, 300)

watch(backendFilterSignature, () => {
  debouncedRefreshBackendFiltersForOpenedProjects()
}, { immediate: true })

function getProjectDeepLink(projectId: string): string {
  const href = router.resolve({
    path: '/editor',
    query: {
      projectId,
      scope: 'project'
    }
  }).href

  if (typeof window === 'undefined') return href
  return new URL(href, window.location.origin).toString()
}

async function copyToClipboard(text: string, successTitle: string) {
  await copyTextToClipboard(text, {
    successTitle,
    failureTitle: 'Copy failed',
    failureDescription: 'Your browser blocked clipboard access.'
  })
}

async function handleCopyProjectLink(projectId: string) {
  await copyToClipboard(getProjectDeepLink(projectId), 'Project link copied')
}

async function handleCopyProjectId(projectId: string) {
  await copyToClipboard(projectId, 'Project ID copied')
}

function mergeOrderedPageSummaries(projectId: string, responses: PageResponse[]) {
  const existingById = new Map(editorStore.getProjectPages(projectId).map(page => [page.id, page]))
  const projectName = getProjectTitle(projectId)
  return createSkeletonPageData(responses, { projectId, projectName }).map((summary) => {
    const existing = existingById.get(summary.id)
    if (!existing) return summary

    return {
      ...existing,
      ...summary,
      imageVariants: existing.imageVariants.length > 0 ? existing.imageVariants : summary.imageVariants,
      xmlFiles: existing.xmlFiles.length > 0 ? existing.xmlFiles : summary.xmlFiles,
      annotationContext: existing.annotationContext ?? summary.annotationContext
    }
  })
}

async function openPageOrderSlideover(projectId: string) {
  const pages = editorStore.getProjectPages(projectId)
  if (pages.length === 0) return

  const instance = pageOrderSlideover.open({
    projectId,
    projectName: getProjectTitle(projectId),
    pages
  })
  const updatedPages = await instance.result as PageResponse[] | null
  if (!updatedPages) return

  editorStore.setProjectPages(projectId, mergeOrderedPageSummaries(projectId, updatedPages), {
    replaceProject: true,
    preserveLoaded: true
  })
  pageSortMode.value = DEFAULT_PAGE_SORT_MODE
}

function getProjectContextMenuItems(projectId: string): DropdownMenuItem[][] {
  const hasProject = Boolean(openedProjectById.value[projectId])
  return [[
    {
      label: 'Edit Page Order',
      icon: 'i-lucide-list-ordered',
      disabled: !hasProject || editorStore.getProjectPages(projectId).length === 0,
      onSelect: () => { void openPageOrderSlideover(projectId) }
    },
    {
      label: 'Copy Project Link',
      icon: 'i-lucide-link',
      disabled: !hasProject,
      onSelect: () => { void handleCopyProjectLink(projectId) }
    },
    {
      label: 'Copy Project ID',
      icon: 'i-lucide-copy',
      onSelect: () => { void handleCopyProjectId(projectId) }
    }
  ], [
    {
      label: 'Unload Project',
      icon: 'i-lucide-folder-x',
      color: 'error',
      disabled: !hasProject,
      onSelect: () => { void confirmAndUnloadProject(projectId) }
    }
  ]]
}

const globalVariantItems = computed(() => {
  const map = new Map<string, string>()
  for (const page of editorStore.pages) {
    for (const v of page.imageVariants ?? []) {
      const key = v.type ?? v.label
      if (!key) continue
      if (!map.has(key)) {
        map.set(key, v.type ? v.type : v.label)
      }
    }
  }

  return Array.from(map.entries())
    .map(([value, label]) => ({ label, value }))
    .sort((a, b) => a.label.localeCompare(b.label))
})

const {
  editorFilterPopoverOpen,
  projectAccordionPanels,
  accordionPanels
} = useEditorSidebarState({
  openedProjectIds: computed(() => [...sessionStore.openedProjectIds]),
  isLeftCollapsed: computed(() => editorUiStore.leftCollapsed),
  isRightCollapsed: computed(() => editorUiStore.rightCollapsed),
  expandLeftSidebar: () => editorUiStore.toggleLeftCollapsed(),
  expandRightSidebar: () => editorUiStore.toggleRightCollapsed()
})

onMounted(() => {
  syncEditorWorkspaceAnchorRegistration()
})

onBeforeUnmount(() => {
  for (const projectId of previousPrefetchScopeProjectIds) {
    editorStore.setAdjacentPrefetchPageScope(projectId, null)
  }
  previousPrefetchScopeProjectIds = []

  if (registeredEditorWorkspaceAnchorElement) {
    unregisterEditorFloatingAnchor(EDITOR_WORKSPACE_FLOATING_ANCHOR_ID, registeredEditorWorkspaceAnchorElement)
    registeredEditorWorkspaceAnchorElement = null
  }
})

watch(() => editorWorkspaceAnchorRef.value, () => {
  if (!import.meta.client) return
  syncEditorWorkspaceAnchorRegistration()
})

function navigateImage(direction: 'next' | 'prev') {
  const projectId = currentProjectId.value
  if (!projectId) return
  const allPages = editorStore.getProjectPages(projectId)
  if (allPages.length === 0) return

  const availablePages = getFilteredPagesForProject(projectId)
  const nextPageId = resolveAdjacentPageId({
    allPages,
    availablePages,
    currentPageId: editorStore.currentPageId,
    direction
  })

  if (nextPageId) {
    void openEditorForPage(projectId, nextPageId)
  }
}

function resolveAdjacentPageForCurrentTab(direction: 'next' | 'prev'): { projectId: string, pageId: string } | null {
  const projectId = currentProjectId.value
  const currentPageId = editorStore.currentPageId
  if (!projectId || !currentPageId) return null

  const allPages = editorStore.getProjectPages(projectId)
  if (allPages.length === 0) return null

  const availablePages = getFilteredPagesForProject(projectId)
  const pageId = resolveAdjacentPageId({
    allPages,
    availablePages,
    currentPageId,
    direction
  })

  return pageId ? { projectId, pageId } : null
}

async function closeCurrentTab(): Promise<boolean> {
  const canvasId = activeCanvasId.value
  if (!canvasId) return false

  const canvas = editorStore.canvases[canvasId]
  const projectId = canvas?.projectId ?? null
  const pageId = canvas?.pageId ?? null
  if (!projectId || !pageId) return false

  const innerApi = projectDockviewRegistry.get(projectId)
  if (!innerApi) return false

  const panelId = getPagePanelId(projectId, pageId)
  const panel = innerApi.getPanel(panelId)
  if (!panel) return false

  await handleCloseRequest({ panelApi: panel.api, projectId, pageId })
  return waitForCondition(() =>
    !projectDockviewRegistry.get(projectId)?.getPanel(panelId)
    && !editorStore.canvases[canvasId]
    && !sessionStore.getOpenedPageIds(projectId).includes(pageId)
  )
}

async function closeCurrentTabAndOpenAdjacentPage(direction: 'next' | 'prev') {
  const adjacent = resolveAdjacentPageForCurrentTab(direction)
  const closed = await closeCurrentTab()
  if (!closed || !adjacent) return
  await openEditorForPage(adjacent.projectId, adjacent.pageId)
}

const {
  activeCanvasId,
  isSavingActiveCanvas,
  activeUiMode,
  useFloatingCollapsedSidebars,
  activeControls,
  activeSelectedPolygonId,
  activeSelectedPolylineId,
  activeHoveredPolygonId,
  activePolygons,
  activePolylines,
  activePolygonsForSidebar,
  activePolylinesForSidebar,
  activeHoveredEntity,
  activeSelectedEntity,
  activePageSummary,
  activeCollaborators,
  activeCanvasEditor,
  activePendingTakeover,
  activeCanvasCanEdit,
  activePageLockReason,
  activeAnnotationMode,
  canOpenActiveCanvasXmlEditor,
  activeSelectedPolygonIds,
  activeSelectedPolylineIds,
  activeHiddenPolygonIds,
  activeHiddenPolylineIds,
  activePageId,
  activeDocument,
  activePage,
  isActivePageLocked
} = useEditorActiveCanvasStatus({
  resolveCanvasAnnotationContext
})
const activeCanvasIsComparison = computed(() => {
  const canvasId = activeCanvasId.value
  return canvasId ? Boolean(editorStore.canvases[canvasId]?.comparison) : false
})
const activeWorkflowState = computed<PageWorkflowState>(() => {
  if (!activePageId.value || !currentProjectId.value) return 'OPEN'
  return editorStore.getPage(activePageId.value, currentProjectId.value)?.workflowState ?? 'OPEN'
})
const canChangeActiveWorkflowState = computed(() => {
  if (!activePageId.value || !currentProjectId.value || activeCanvasIsComparison.value) return false
  const page = editorStore.getPage(activePageId.value, currentProjectId.value)
  const workflowOnlyLock = page?.workflowState === 'DONE' && page.lockedReason === 'Page workflow state is Done'
  return Boolean(selectedWorkspace.value && activeAnnotationMode.value === 'PROJECT' && (!page?.locked || workflowOnlyLock))
})

async function updateActiveWorkflowState(workflowState: PageWorkflowState) {
  const projectId = currentProjectId.value
  const pageId = activePageId.value
  if (!projectId || !pageId || workflowState === activeWorkflowState.value || !canChangeActiveWorkflowState.value) return

  if (workflowState === 'DONE') {
    const saved = await handleSaveDocument()
    if (!saved) return
  }

  try {
    const updated = await $fetch<PageResponse>(`/api/projects/${projectId}/pages/${pageId}/workflow-state`, {
      method: 'PUT',
      body: { workflowState }
    })
    editorStore.patchProjectPageSummaries(projectId, [updated])

    const projectPagesCacheKey = wsKey(selectedWorkspace.value as string, 'projects', projectId, 'pages')
    clearNuxtData(projectPagesCacheKey)

    const canvasId = activeCanvasId.value
    if (canvasId) {
      if (workflowState === 'DONE') {
        await collaboration.releaseCanvasLease(canvasId)
      } else {
        await collaboration.ensureCanvasRoom(canvasId)
        const session = getEditorSession(canvasId)
        if (session) collaboration.attachCanvasSession(canvasId, session)
      }
    }

    await Promise.all([
      refreshNuxtData(wsKey(selectedWorkspace.value as string, 'projects', projectId)),
      refreshNuxtData(wsKey(selectedWorkspace.value as string, 'projects', 'list'))
    ])
    const label = workflowState === 'IN_PROGRESS' ? 'In progress' : workflowState === 'DONE' ? 'Done' : 'Open'
    toast.add({ title: `Page marked ${label}`, color: 'success' })
  } catch (error) {
    toast.add({
      title: 'Failed to update page state',
      description: extractApiErrorMessage(error, 'Could not update the page workflow state.'),
      color: 'error'
    })
  }
}
const collapsedImagePopoverDismissKey = ref(0)

watch(activeUiMode, (mode) => {
  if (mode !== 'text') return
  void maybeAutoStartContextTour('/editor', { editorMode: 'text' })
})

const {
  findRegionById,
  findTextLineById,
  handleApplyMetadata
} = useEditorMetadataApply({
  activeCanvasId,
  activeDocument,
  activePage,
  onReadingOrderUpdated: () => editorUiStore.bumpReadingOrderVersion()
})

function handleApplyMetadataIfWritable(payload: Parameters<typeof handleApplyMetadata>[0]): void {
  if (isActivePageLocked.value) return
  handleApplyMetadata(payload)
}

const textSidebarSelectedElement = computed<Region | TextLine | RenderablePolyline | null>(() => {
  const page = activePage.value
  if (!page?.regions) return null

  const selectedPolylineId = activeSelectedPolylineId.value ?? editorStore.activeSelectedBaselineId
  if (selectedPolylineId) {
    const selectedPolyline = activePolylines.value.find(polyline => polyline.id === selectedPolylineId)
    if (selectedPolyline) return selectedPolyline
  }

  const selectedPolygonId = activeSelectedPolygonId.value ?? editorStore.activeSelectedRegionId
  if (!selectedPolygonId) return null

  const selectedPolygon = activePolygons.value.find(polygon => polygon.id === selectedPolygonId)
  if (selectedPolygon?.type === PolygonType.TEXTLINE) {
    return findTextLineById(page.regions, selectedPolygonId)
  }
  if (selectedPolygon?.type === PolygonType.REGION) {
    return findRegionById(page.regions, selectedPolygonId)
  }

  return findTextLineById(page.regions, selectedPolygonId) ?? findRegionById(page.regions, selectedPolygonId)
})

const {
  isOpenSubtasksLoading,
  openSubtaskPageIds,
  getOpenSubtaskCountByPage,
  getOpenSubtaskPageIds,
  activeOpenSubtasks,
  isActivePageTasksLoading,
  activeTaskByIdRecord,
  canCompleteActivePageSubtasks,
  isCompletingOpenSubtasks,
  handleSaveAndCompleteOpenSubtasks,
  completeSubtask
} = useEditorTaskState({
  currentProjectId,
  activePageId,
  isActivePageLocked,
  selectedWorkspace,
  openedProjectIds: computed(() => [...sessionStore.openedProjectIds]),
  refreshTaskCaches,
  saveDocument: handleSaveDocument
})

function getFilteredPagesForProject(projectId: string) {
  const q = pageNameFilter.value.trim().toLowerCase()
  let result = editorStore.getProjectPages(projectId)

  if (q) {
    result = result.filter(p => (p.label ?? '').toLowerCase().includes(q))
  }

  if (onlyWithOpenSubtasks.value) {
    const pageIdsWithSubtasks = getOpenSubtaskPageIds(projectId)
    result = result.filter(p => pageIdsWithSubtasks.has(p.id))
  }

  if (pageWorkflowStateFilters.value.length > 0) {
    const selectedStates = new Set(pageWorkflowStateFilters.value)
    result = result.filter(page => selectedStates.has(page.workflowState ?? 'OPEN'))
  }

  if (hasBackendFilters.value) {
    const filteredIds = backendFilteredPageIdsByProjectId.value[projectId]
    if (filteredIds) {
      const filteredIdSet = new Set(filteredIds)
      result = result.filter(p => filteredIdSet.has(p.id))
    }
  }

  return sortPagesForEditor(result, pageSortMode.value)
}

const openedProjectsForSidebar = computed(() => {
  return sessionStore.openedProjectIds.map(projectId => ({
    id: projectId,
    name: getProjectTitle(projectId),
    pages: getFilteredPagesForProject(projectId),
    openSubtaskCountByPage: getOpenSubtaskCountByPage(projectId)
  }))
})

const totalFilteredPagesAcrossProjects = computed(() => {
  return openedProjectsForSidebar.value.reduce((count, project) => count + project.pages.length, 0)
})

const openedProjectById = computed(() => {
  return Object.fromEntries(openedProjectsForSidebar.value.map(project => [project.id, project]))
})

const isPageListFilteringActive = computed(() => {
  return pageNameFilter.value.trim().length > 0
    || onlyWithOpenSubtasks.value
    || pageWorkflowStateFilters.value.length > 0
    || hasBackendFilters.value
})

const prefetchScopeSignature = computed(() => {
  const mode = isPageListFilteringActive.value ? 'filtered' : 'all'
  const scopes = sessionStore.openedProjectIds.map((projectId) => {
    const visiblePageIds = openedProjectById.value[projectId]?.pages.map(page => page.id) ?? []
    return `${projectId}:${visiblePageIds.join(',')}`
  })
  return `${mode}|${scopes.join('|')}`
})

let previousPrefetchScopeProjectIds: string[] = []

function syncAdjacentPrefetchScopes() {
  const openedProjectIds = [...sessionStore.openedProjectIds]
  const openedProjectIdSet = new Set(openedProjectIds)

  for (const previousProjectId of previousPrefetchScopeProjectIds) {
    if (!openedProjectIdSet.has(previousProjectId)) {
      editorStore.setAdjacentPrefetchPageScope(previousProjectId, null)
    }
  }

  if (!isPageListFilteringActive.value) {
    for (const projectId of openedProjectIds) {
      editorStore.setAdjacentPrefetchPageScope(projectId, null)
    }
    previousPrefetchScopeProjectIds = openedProjectIds
    return
  }

  for (const projectId of openedProjectIds) {
    const visiblePageIds = openedProjectById.value[projectId]?.pages.map(page => page.id) ?? []
    editorStore.setAdjacentPrefetchPageScope(projectId, visiblePageIds)
  }

  previousPrefetchScopeProjectIds = openedProjectIds
}

watch(prefetchScopeSignature, () => {
  syncAdjacentPrefetchScopes()
}, { immediate: true })

const projectAccordionItems = computed(() => {
  return openedProjectsForSidebar.value.map((project) => {
    const unsavedCount = Object.values(editorStore.canvases)
      .filter(canvas => canvas.projectId === project.id && canvas.hasUnsavedChanges)
      .length

    return {
      label: unsavedCount > 0
        ? `${project.name} (${project.pages.length}) *`
        : `${project.name} (${project.pages.length})`,
      value: project.id,
      slot: `project-${project.id}`
    }
  })
})

const commanderForSidebar = computed<Commander | null>(() => {
  return activeControls.value?.commander ?? null
})

function handleApplyReadingOrder(readingOrder: ReadingOrder): void {
  const canvasId = activeCanvasId.value
  const commander = commanderForSidebar.value
  if (!canvasId || !commander || isActivePageLocked.value) return

  const session = getEditorSession(canvasId)
  if (!session) return

  commander.execute(
    new UpdateReadingOrderCommand({ readingOrder }),
    { canvasId, session }
  )
}

const activeDrawingMode = computed<DrawingMode>(() => {
  const controls = activeControls.value
  return controls?.drawingMode?.value ?? DRAWING_MODES.SELECT
})

const activeViewMode = computed<ViewMode>(() => {
  const controls = activeControls.value
  return controls?.viewMode?.value ?? VIEW_MODES.DEFAULT
})

const activeSelectedPolygonIndex = computed(() => activeControls.value?.selectedPolygonIndex?.value ?? -1)
const activeSelectedPolylineIndex = computed(() => activeControls.value?.selectedPolylineIndex?.value ?? -1)

if (import.meta.client) {
  useKeyboardShortcuts({
    canvasId: activeCanvasId,
    isDrawingMode: computed(() => activeDrawingMode.value !== DRAWING_MODES.SELECT),
    drawingMode: activeDrawingMode,
    viewMode: activeViewMode,
    selectedPolygonIds: computed(() => activeSelectedPolygonIds.value),
    selectedPolylineIds: computed(() => activeSelectedPolylineIds.value),
    polygons: activePolygons,
    polylines: activePolylines,
    selectedPolygonIndex: computed(() => activeSelectedPolygonIndex.value),
    selectedPolylineIndex: computed(() => activeSelectedPolylineIndex.value),
    callbacks: {
      handleUndo: () => {
        const controls = activeControls.value
        if (controls?.handleUndo) {
          controls.handleUndo()
          return
        }

        const canvasId = activeCanvasId.value
        if (!canvasId) return
        undoSessionCommand(canvasId)
      },
      handleRedo: () => {
        const controls = activeControls.value
        if (controls?.handleRedo) {
          controls.handleRedo()
          return
        }

        const canvasId = activeCanvasId.value
        if (!canvasId) return
        redoSessionCommand(canvasId)
      },
      setDrawingMode: (mode: DrawingMode) => {
        const controls = activeControls.value
        editorUiStore.setActionWandActive(false)
        if (mode !== DRAWING_MODES.SELECT && controls?.isCanvasEditable.value === false) return
        if (controls?.drawingMode) {
          controls.drawingMode.value = mode
        }
      },
      setViewMode: (mode: ViewMode) => {
        const controls = activeControls.value
        if (controls?.setViewMode) {
          controls.setViewMode(mode)
        } else if (controls?.viewMode) {
          controls.viewMode.value = mode
        }
      },
      selectPolygonByIndex: (index: number) => {
        const controls = activeControls.value
        if (controls?.selectedPolygonIndex) {
          controls.selectedPolygonIndex.value = index
          const polygon = controls.polygons?.[index]
          if (polygon && controls.selectedPolygonIds) {
            controls.selectedPolygonIds.value = [polygon.id]
          }
        }
      },
      selectPolylineByIndex: (index: number) => {
        const controls = activeControls.value
        if (controls?.selectedPolylineIndex) {
          controls.selectedPolylineIndex.value = index
          const polyline = controls.polylines?.[index]
          if (polyline && controls.selectedPolylineIds) {
            controls.selectedPolylineIds.value = [polyline.id]
          }
        }
      },
      clearSelection: () => {
        activeControls.value?.selectPolygonById?.(null)
        activeControls.value?.selectPolylineById?.(null)
        if (activeControls.value?.selectedPolygonIds) {
          activeControls.value.selectedPolygonIds.value = []
        }
        if (activeControls.value?.selectedPolylineIds) {
          activeControls.value.selectedPolylineIds.value = []
        }
      },
      selectAll: () => {
        const controls = activeControls.value
        if (!controls) return
        const allPolygonIds = (controls.polygons ?? []).map(p => p.id)
        if (controls.selectedPolygonIds) {
          controls.selectedPolygonIds.value = allPolygonIds
        }
      },
      zoomIn: () => {
      },
      zoomOut: () => {
      },
      fitToContent: () => {
      },
      resetView: () => {
        activeControls.value?.resetView?.()
      },
      centerOnSelection: () => {
      },
      addHoveredToReadingOrder: () => activeControls.value?.addHoveredElementToReadingOrder?.() ?? false,
      toggleLeftSidebar: () => {
        editorUiStore.toggleLeftCollapsed()
      },
      toggleRightSidebar: () => {
        editorUiStore.toggleRightCollapsed()
      },
      mergeSelected: handleMergeSelected,
      setRegionType: (type: 'region' | 'textline' | 'baseline') => {
        const controls = activeControls.value
        if (!controls?.regionType) return
        if (type === 'region') controls.regionType.value = PolygonType.REGION
        else if (type === 'textline') controls.regionType.value = PolygonType.TEXTLINE
        else controls.regionType.value = PolygonType.BASELINE
      },
      setCutMode: (mode: 'line' | 'polygon' | 'rectangle') => {
        const controls = activeControls.value
        if (!controls || !controls.isCanvasEditable.value) return
        editorUiStore.setActionWandActive(false)
        if (mode === 'line') controls.toggleCutLineMode?.()
        else if (mode === 'polygon') controls.toggleCutPolygonMode?.()
        else controls.toggleCutRectangleMode?.()
      },
      setUiMode: (mode: 'layout' | 'text') => {
        editorStore.setUiMode(mode)
      },
      toggleVirtualKeyboard: () => {
        const current = editorUiStore.virtualKeyboardMode
        editorUiStore.setVirtualKeyboardMode(current === 'off' ? 'floating' : 'off')
      },
      saveDocument: handleSaveDocument,
      nextImage: () => navigateImage('next'),
      prevImage: () => navigateImage('prev'),
      toggleShortcutsHelp: () => editorUiStore.toggleShortcutsHelp(),
      closeActiveTab: () => { void closeCurrentTab() },
      closeActiveTabAndNextPage: () => { void closeCurrentTabAndOpenAdjacentPage('next') },
      closeActiveTabAndPrevPage: () => { void closeCurrentTabAndOpenAdjacentPage('prev') }
    }
  })
}

const loadedProjectMetadata = ref<Set<string>>(new Set())

async function ensureProjectPagesLoaded(projectId: string, pageId: string): Promise<boolean> {
  if (editorStore.getPage(pageId, projectId)) return true

  try {
    const projectPromise = selectedWorkspace.value
      ? $fetch<{ id: string, name: string }>(`/api/workspaces/${selectedWorkspace.value}/projects/${projectId}`).catch(() => null)
      : Promise.resolve(null)
    const [project, allPagesResponse] = await Promise.all([
      projectPromise,
      $fetch<PageResponse[]>(`/api/projects/${projectId}/pages`)
    ])

    const targetPage = allPagesResponse.find(page => page.id === pageId)
    if (!targetPage) return false

    const skeletonPages = createSkeletonPageData([targetPage], {
      projectId,
      projectName: project?.name
    })
    editorStore.setProjectPages(projectId, skeletonPages, { replaceProject: true })

    const variants = sessionStore.getSelectedVariantIdByPageId(projectId)
    for (const [pageId, variantId] of Object.entries(variants)) {
      editorStore.setSelectedVariantOverride(pageId, variantId ?? null, projectId)
    }

    return true
  } catch (error) {
    console.error(`Failed to load pages for project ${projectId}:`, error)
    return false
  }
}

async function ensureFullProjectPagesLoaded(projectId: string): Promise<boolean> {
  if (editorStore.getProjectPages(projectId).length > 0) return true

  try {
    const projectPromise = selectedWorkspace.value
      ? $fetch<{ id: string, name: string }>(`/api/workspaces/${selectedWorkspace.value}/projects/${projectId}`).catch(() => null)
      : Promise.resolve(null)
    const [project, allPagesResponse] = await Promise.all([
      projectPromise,
      $fetch<PageResponse[]>(`/api/projects/${projectId}/pages`)
    ])

    const skeletonPages = createSkeletonPageData(allPagesResponse, {
      projectId,
      projectName: project?.name
    })
    editorStore.setProjectPages(projectId, skeletonPages, { replaceProject: true })

    const variants = sessionStore.getSelectedVariantIdByPageId(projectId)
    for (const [pageId, variantId] of Object.entries(variants)) {
      editorStore.setSelectedVariantOverride(pageId, variantId ?? null, projectId)
    }

    return true
  } catch (error) {
    console.error(`Failed to load full project pages for project ${projectId}:`, error)
    return false
  }
}

const loadProjectLabelSet = async (projectId?: string | null, force = false) => {
  if (!projectId || !selectedWorkspace.value) {
    editorStore.setLabelSet(createPageXmlLabelSet())
    editorStore.clearProjectCodec()
    editorStore.clearProjectDictionary()
    editorStore.clearProjectVirtualKeyboard()
    editorStore.setProjectToolkitSettings({})
    return
  }
  if (!force && loadedProjectMetadata.value.has(projectId)) {
    return
  }

  try {
    const project = await $fetch<{
      labelSetId?: string | null
      codecId?: string | null
      dictionaryId?: string | null
      tagSetId?: string | null
      normalizationProfileId?: string | null
      validationRulesetId?: string | null
      virtualKeyboardId?: string | null
      allowCodecOverride?: boolean
      allowDictionaryOverride?: boolean
      allowVirtualKeyboardOverride?: boolean
      allowLabelSetOverride?: boolean
      allowTagSetOverride?: boolean
      allowNormalizationProfileOverride?: boolean
      allowValidationRulesetOverride?: boolean
      defaultGtIndex?: number | null
      defaultRecognitionIndices?: number[] | null
    }>(`/api/workspaces/${selectedWorkspace.value}/projects/${projectId}`)
    editorStore.setProjectToolkitSettings({
      codecId: project.codecId ?? null,
      labelSetId: project.labelSetId ?? null,
      dictionaryId: project.dictionaryId ?? null,
      tagSetId: project.tagSetId ?? null,
      normalizationProfileId: project.normalizationProfileId ?? null,
      validationRulesetId: project.validationRulesetId ?? null,
      virtualKeyboardId: project.virtualKeyboardId ?? null,
      allowCodecOverride: project.allowCodecOverride !== false,
      allowDictionaryOverride: project.allowDictionaryOverride !== false,
      allowVirtualKeyboardOverride: project.allowVirtualKeyboardOverride !== false,
      allowLabelSetOverride: project.allowLabelSetOverride !== false,
      allowTagSetOverride: project.allowTagSetOverride !== false,
      allowNormalizationProfileOverride: project.allowNormalizationProfileOverride !== false,
      allowValidationRulesetOverride: project.allowValidationRulesetOverride !== false
    }, projectId)
    if (project.labelSetId) {
      const labelSet = await $fetch<ApiLabelSet>(`/api/workspaces/${selectedWorkspace.value}/label-sets/${project.labelSetId}`)
      editorStore.setLabelSetFromApi(labelSet, projectId)
    } else {
      editorStore.setLabelSet(createPageXmlLabelSet(), projectId)
    }

    if (project.codecId) {
      try {
        const codec = await $fetch<{ id: string, codec: string[] }>(`/api/workspaces/${selectedWorkspace.value}/codecs/${project.codecId}`)
        editorStore.setProjectCodec(codec.id, codec.codec ?? [], projectId)
      } catch {
        editorStore.clearProjectCodec(projectId)
      }
    } else {
      editorStore.clearProjectCodec(projectId)
    }

    if (project.dictionaryId) {
      try {
        const dictionary = await $fetch<Dictionary>(`/api/workspaces/${selectedWorkspace.value}/dictionaries/${project.dictionaryId}`)
        editorStore.setProjectDictionary({
          id: dictionary.id,
          forms: [],
          caseSensitive: dictionary.caseSensitive,
          unicodeNormalization: dictionary.unicodeNormalization,
          canEdit: Boolean(dictionary.capabilities?.canEdit),
          locked: Boolean(dictionary.locked)
        }, projectId)
      } catch {
        editorStore.clearProjectDictionary(projectId)
      }
    } else {
      editorStore.clearProjectDictionary(projectId)
    }

    editorStore.setProjectVirtualKeyboard(project.virtualKeyboardId ?? null, projectId)

    editorStore.setProjectTextIndexDefaults({
      gtIndex: Number.isFinite(Number(project.defaultGtIndex)) ? Number(project.defaultGtIndex) : 0,
      recognitionIndices: Array.isArray(project.defaultRecognitionIndices) ? project.defaultRecognitionIndices : [1]
    }, projectId)

    loadedProjectMetadata.value = new Set([...loadedProjectMetadata.value, projectId])
  } catch {
    editorStore.setLabelSet(createPageXmlLabelSet(), projectId)
    editorStore.clearProjectCodec(projectId)
    editorStore.clearProjectDictionary(projectId)
    editorStore.clearProjectTextIndexDefaults(projectId)
  }
}

async function restorePersistedProject(projectId: string) {
  const project = selectedWorkspace.value
    ? await $fetch<{ id: string, name: string }>(`/api/workspaces/${selectedWorkspace.value}/projects/${projectId}`).catch(() => null)
    : null

  const allPagesResponse = await $fetch<PageResponse[]>(`/api/projects/${projectId}/pages`)

  const skeletonPages = createSkeletonPageData(allPagesResponse, {
    projectId,
    projectName: project?.name
  })
  editorStore.setProjectPages(projectId, skeletonPages, { replaceProject: true })

  const variants = sessionStore.getSelectedVariantIdByPageId(projectId)
  for (const [pageId, variantId] of Object.entries(variants)) {
    editorStore.setSelectedVariantOverride(pageId, variantId ?? null, projectId)
  }
}

async function openEditorForPage(projectId: string, pageId: string, variantId?: string) {
  let page = editorStore.getPage(pageId, projectId)
  if (!page) {
    const loaded = await ensureProjectPagesLoaded(projectId, pageId)
    if (!loaded) return
    page = editorStore.getPage(pageId, projectId)
  }
  if (!page) return

  const canvasId = getCanvasId(projectId, pageId)
  const existingCanvas = editorStore.canvases[canvasId]
  const isAlreadyLoaded = existingCanvas?.pageId === pageId && existingCanvas?.projectId === projectId
  const requestedVariant = variantId
    ? page.imageVariants.find(variant => variant.id === variantId) ?? null
    : editorStore.getDisplayedVariantForPage(page)
  const previewSrc = editorStore.getPreviewUrlForPage(page)

  if (!existingCanvas) {
    editorStore.registerCanvas(canvasId, {
      projectId,
      pageId,
      imageVariantId: requestedVariant?.id ?? variantId ?? null,
      imageSrc: previewSrc,
      isLoadingAnnotations: true
    })
  } else if (variantId && existingCanvas?.imageVariantId !== variantId) {
    editorStore.switchImageVariantForCanvas(canvasId, variantId)
  }

  editorStore.setActiveCanvas(canvasId)
  sessionStore.addOpenedProject(projectId)
  sessionStore.addOpenedPage(projectId, pageId)
  sessionStore.setActiveProject(projectId)
  sessionStore.setActivePage(projectId, pageId)

  const api = dockviewApi.value
  if (!api) return

  ensureProjectPanelExists(api, projectId)
  api.getPanel(getProjectPanelId(projectId))?.api.setActive()
  await projectDockviewRegistry.waitFor(projectId)
  await nextTick()
  projectDockviewRegistry.get(projectId)?.getPanel(getPagePanelId(projectId, pageId))?.api.setActive()

  void loadProjectLabelSet(projectId)

  if (!isAlreadyLoaded) {
    void editorStore.loadPageIntoCanvas(canvasId, projectId, pageId, variantId)
  }
}

const { applyEditorDeepLinkFromQuery } = useEditorDeepLinks({
  route,
  router,
  ensureFullProjectPagesLoaded,
  openEditorForPage,
  getErrorMessage
})

function handleSelectPage(pageId: string, variantId?: string, projectId?: string) {
  collapsedImagePopoverDismissKey.value++
  const targetProjectId = projectId ?? currentProjectId.value
  if (!targetProjectId) return
  void openEditorForPage(targetProjectId, pageId, variantId)
}

function removeProjectFromLoadedState(projectId: string) {
  const canvasesToRemove = Object.entries(editorStore.canvases)
    .filter(([, canvas]) => canvas.projectId === projectId)
    .map(([canvasId]) => canvasId)

  for (const canvasId of canvasesToRemove) {
    editorStore.unregisterCanvas(canvasId)
  }

  sessionStore.removeOpenedProject(projectId)
  editorStore.removeProject(projectId)

  loadedProjectMetadata.value = new Set(
    [...loadedProjectMetadata.value].filter(id => id !== projectId)
  )

  const { [projectId]: _filtersRemoved, ...remainingFilters } = backendFilteredPageIdsByProjectId.value
  backendFilteredPageIdsByProjectId.value = remainingFilters
}

function clearProjectTabState(projectId: string) {
  const openedPageIds = [...sessionStore.getOpenedPageIds(projectId)]
  for (const pageId of openedPageIds) {
    sessionStore.removeOpenedPage(projectId, pageId)
  }

  const canvasesToRemove = Object.entries(editorStore.canvases)
    .filter(([, canvas]) => canvas.projectId === projectId)
    .map(([canvasId]) => canvasId)

  for (const canvasId of canvasesToRemove) {
    editorStore.unregisterCanvas(canvasId)
  }
}

function removePageFromLoadedState(projectId: string, pageId: string) {
  const nextPages = editorStore.getProjectPages(projectId).filter(page => page.id !== pageId)
  sessionStore.removeOpenedPage(projectId, pageId)

  if (nextPages.length === 0) {
    removeProjectFromLoadedState(projectId)
    return
  }

  editorStore.setProjectPages(projectId, nextPages, { replaceProject: true })
}

async function waitForCondition(condition: () => boolean, timeoutMs = 900, intervalMs = 30): Promise<boolean> {
  const start = Date.now()
  while (Date.now() - start < timeoutMs) {
    if (condition()) return true
    await new Promise(resolve => setTimeout(resolve, intervalMs))
  }
  return condition()
}

async function confirmAndUnloadProject(projectId: string) {
  const projectLabel = getProjectTitle(projectId)
  const instance = confirmSlideover.open({
    title: 'Unload Project?',
    message: `Unload "${projectLabel}" from the editor?`,
    confirmLabel: 'Unload Project',
    confirmColor: 'warning',
    confirmIcon: 'i-lucide-folder-x'
  })

  const confirmed = await instance.result
  if (!confirmed) return

  await handleUnloadProject(projectId)
}

async function handleUnloadProject(projectId: string) {
  const panelId = getProjectPanelId(projectId)
  const panel = dockviewApi.value?.getPanel(panelId)
  if (panel) {
    await handleCloseRequest({ panelApi: panel.api, projectId })
    const closed = await waitForCondition(() => !dockviewApi.value?.getPanel(panelId))
    if (!closed) return
  }

  if (
    sessionStore.openedProjectIds.includes(projectId)
    || editorStore.getProjectPages(projectId).length > 0
  ) {
    removeProjectFromLoadedState(projectId)
  }
}

async function handleUnloadPage(pageId: string, projectId?: string) {
  const targetProjectId = projectId ?? currentProjectId.value
  if (!targetProjectId) return

  const panelId = getPagePanelId(targetProjectId, pageId)
  const innerApi = projectDockviewRegistry.get(targetProjectId)
  const pagePanel = innerApi?.getPanel(panelId)
  if (pagePanel) {
    await handleCloseRequest({ panelApi: pagePanel.api, projectId: targetProjectId, pageId })
    const closed = await waitForCondition(() => !projectDockviewRegistry.get(targetProjectId)?.getPanel(panelId))
    if (!closed) return
  }

  if (editorStore.getPage(pageId, targetProjectId)) {
    removePageFromLoadedState(targetProjectId, pageId)
  }
}

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value))
}

function scheduleDockviewLayout() {
  if (rafLayoutId != null) return
  rafLayoutId = window.requestAnimationFrame(() => {
    rafLayoutId = null
    const api = dockviewApi.value
    if (!api) return
    api.layout(api.width, api.height)
  })
}

function onResizePointerMove(event: PointerEvent) {
  if (!resizingSide.value) return

  const delta = event.clientX - resizeStartX
  if (resizingSide.value === 'left') {
    editorUiStore.setLeftWidth(clamp(resizeStartWidth + delta, MIN_LEFT_WIDTH_PX, MAX_LEFT_WIDTH_PX))
  } else {
    editorUiStore.setRightWidth(clamp(resizeStartWidth - delta, MIN_RIGHT_WIDTH_PX, MAX_RIGHT_WIDTH_PX))
  }

  scheduleDockviewLayout()
}

function stopResize() {
  resizingSide.value = null
  window.removeEventListener('pointermove', onResizePointerMove)
  window.removeEventListener('pointerup', stopResize)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
  scheduleDockviewLayout()
}

function startResize(side: ResizeSide, event: PointerEvent) {
  resizingSide.value = side
  resizeStartX = event.clientX
  resizeStartWidth = side === 'left' ? editorUiStore.leftWidthPx : editorUiStore.rightWidthPx
  window.addEventListener('pointermove', onResizePointerMove)
  window.addEventListener('pointerup', stopResize)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
}

onBeforeUnmount(() => {
  stopResize()
  if (rafLayoutId != null) {
    window.cancelAnimationFrame(rafLayoutId)
    rafLayoutId = null
  }

  if (shouldCleanupOnUnmount) {
    useEditorSessionStore().clearSession({ preserveTextViewSettings: true })
    editorStore.resetEditorState()
  }
})

const {
  isLoading,
  tryCreateInitialPanels
} = useEditorSessionRestore({
  route,
  selectedWorkspace,
  dockviewApi: dockviewApi as unknown as Ref<DockviewReadyEvent['api'] | null>,
  loadPreferences: () => editorUiStore.loadPreferences(),
  clearSession: () => sessionStore.clearSession({ preserveTextViewSettings: true }),
  resetEditorState: () => editorStore.resetEditorState(),
  shouldRestorePersistedSession: () => editorStore.allPages.length === 0,
  loadPersistedSession: () => sessionStore.loadPersistedSession(),
  hasSession: () => sessionStore.hasSession(),
  workspaceId: computed(() => sessionStore.workspaceId),
  initWorkspaceSession: workspaceId => sessionStore.initWorkspaceSession(workspaceId),
  openedProjectIds: computed(() => [...sessionStore.openedProjectIds]),
  getOpenedPageIds: projectId => sessionStore.getOpenedPageIds(projectId),
  getSelectedVariantIdByPageId: projectId => sessionStore.getSelectedVariantIdByPageId(projectId),
  ensureProjectPanelExists,
  openEditorForPage,
  restorePersistedProject,
  loadProjectMetadata: projectId => loadProjectLabelSet(projectId),
  applyEditorDeepLink: applyEditorDeepLinkFromQuery,
  maybeAutoStartContextTour: async (path, context) => {
    await maybeAutoStartContextTour(path, {
      editorMode: context.editorMode === 'text' ? 'text' : 'layout'
    })
  },
  getEditorMode: () => editorStore.effectiveUiMode(editorStore.activeCanvasId)
})
</script>

<template>
  <AppSplashScreen v-if="isLoading" />
  <UDashboardSearch
    v-model:open="commandCenterOpen"
    v-model:search-term="commandCenterSearchTerm"
    :groups="commandCenterGroups"
    :loading="isCommandCenterLoading"
    shortcut=""
  />
  <div v-show="!isLoading" class="w-screen h-screen flex overflow-hidden bg-default text-default">
    <EditorLeftSidebar
      v-model:page-name-filter="pageNameFilter"
      v-model:filter-popover-open="editorFilterPopoverOpen"
      v-model:page-sort-mode="pageSortMode"
      :left-rail-width-px="64"
      :use-floating-collapsed="useFloatingCollapsedSidebars"
      :image-popover-dismiss-key="collapsedImagePopoverDismissKey"
      :logo-menu-items="logoMenuItems"
      :current-project-id="currentProjectId"
      :available-labels="availableLabelsForFilter"
      :available-tags="availableTags"
      :open-subtask-page-ids="openSubtaskPageIds"
      :has-advanced-filters="hasAdvancedFilters"
      :is-filtering="isFiltering"
      :total-filtered-pages-across-projects="totalFilteredPagesAcrossProjects"
      :global-variant-items="globalVariantItems"
      @open-command-center="openCommandCenter"
    >
      <template #default>
        <EditorProjectListShell
          v-model:project-accordion-panels="projectAccordionPanels"
          :projects="openedProjectsForSidebar"
          :project-accordion-items="projectAccordionItems"
          :page-name-filter="pageNameFilter"
          :only-with-open-subtasks="onlyWithOpenSubtasks"
          :has-backend-filters="hasBackendFilters"
          :backend-filtered-page-ids-by-project-id="backendFilteredPageIdsByProjectId"
          :get-project-context-menu-items="getProjectContextMenuItems"
          @select-page="handleSelectPage"
          @unload-page="handleUnloadPage"
        />
      </template>

      <template #image-popover>
        <EditorProjectListShell
          v-model:project-accordion-panels="projectAccordionPanels"
          :projects="openedProjectsForSidebar"
          :project-accordion-items="projectAccordionItems"
          :page-name-filter="pageNameFilter"
          :only-with-open-subtasks="onlyWithOpenSubtasks"
          :has-backend-filters="hasBackendFilters"
          :backend-filtered-page-ids-by-project-id="backendFilteredPageIdsByProjectId"
          :get-project-context-menu-items="getProjectContextMenuItems"
          @select-page="handleSelectPage"
          @unload-page="handleUnloadPage"
        />
      </template>
    </EditorLeftSidebar>

    <div
      v-show="!editorUiStore.leftCollapsed"
      class="group h-full w-0 shrink-0 cursor-col-resize touch-none relative overflow-visible"
      @pointerdown="(e: PointerEvent) => startResize('left', e)"
    >
      <span class="absolute inset-y-0 left-1/2 w-2 -translate-x-1/2" />
      <span
        :class="[
          'pointer-events-none absolute inset-y-0 left-1/2 w-px -translate-x-1/2 transition-colors',
          resizingSide === 'left' ? 'bg-accented' : 'bg-transparent group-hover:bg-accented/70'
        ]"
      />
    </div>

    <main
      ref="editorWorkspaceAnchorRef"
      class="flex-1 min-w-0 min-h-0 overflow-hidden relative"
      :class="rootLayoutClass"
    >
      <EditorToolbar
        v-if="activeCanvasId && !activeCanvasIsComparison"
        :class="toolbarClass"
        @merge="handleMergeSelected"
      />
      <DockviewVue
        :class="['min-h-0 min-w-0 h-full w-full', contentClass]"
        :theme="dockviewTheme"
        right-header-actions-component="EditorDockviewTabGroupMaximizeButton"
        default-tab-component="EditorDockviewProjectTab"
        @ready="onReady"
      />
      <EditorEntityStatusBar
        v-if="activeCanvasId"
        :class="statusBarClass"
        :hovered-entity="activeHoveredEntity"
        :selected-entity="activeSelectedEntity"
        :page-summary="activePageSummary"
        :collaborators="activeCollaborators"
        :editor="activeCanvasEditor"
        :pending-takeover="activePendingTakeover"
        :can-edit="activeCanvasCanEdit"
        :page-lock-reason="activePageLockReason"
        :annotation-mode="activeAnnotationMode"
      />

      <EditorEmpty v-if="!activeCanvasId" class="absolute inset-0 z-10" />

      <EditorKeyboardShortcutsHelp
        v-model:open="editorUiStore.shortcutsHelpOpen"
        @customize="editorUiStore.openShortcutSettings()"
      />
      <EditorShortcutSettingsModal v-model:open="editorUiStore.shortcutSettingsOpen" />
    </main>

    <div
      v-show="!editorUiStore.rightCollapsed && !activeCanvasIsComparison"
      class="group h-full w-0 shrink-0 cursor-col-resize touch-none relative overflow-visible"
      @pointerdown="(e: PointerEvent) => startResize('right', e)"
    >
      <span class="absolute inset-y-0 left-1/2 w-2 -translate-x-1/2" />
      <span
        :class="[
          'pointer-events-none absolute inset-y-0 left-1/2 w-px -translate-x-1/2 transition-colors',
          resizingSide === 'right' ? 'bg-accented' : 'bg-transparent group-hover:bg-accented/70'
        ]"
      />
    </div>

    <EditorRightSidebar
      v-if="!activeCanvasIsComparison"
      :right-rail-width-px="48"
      :use-floating-collapsed="useFloatingCollapsedSidebars"
      :is-saving-active-canvas="isSavingActiveCanvas"
      :can-edit-active-canvas="activeCanvasCanEdit"
      :can-open-active-canvas-xml-editor="canOpenActiveCanvasXmlEditor"
      :can-complete-active-page-subtasks="canCompleteActivePageSubtasks"
      :is-completing-open-subtasks="isCompletingOpenSubtasks"
      :is-active-page-locked="isActivePageLocked"
      :action-items="rightSidebarActionItems"
      @save="handleSaveDocument"
      @open-history="openVersionHistory"
      @open-xml-editor="openXmlEditor"
      @save-and-complete="handleSaveAndCompleteOpenSubtasks"
    >
      <EditorSidebarPolygon
        v-if="activeUiMode === 'layout'"
        v-model:accordion-panels="accordionPanels"
        :collapsed="editorUiStore.rightCollapsed"
        :page-id="activePageId ?? undefined"
        :polygons="activePolygonsForSidebar"
        :polylines="activePolylinesForSidebar"
        :selected-polygon-ids="activeSelectedPolygonIds"
        :selected-polyline-ids="activeSelectedPolylineIds"
        :selected-polygon-id="activeSelectedPolygonId ?? undefined"
        :selected-polyline-id="activeSelectedPolylineId ?? undefined"
        :hidden-polygon-ids="activeHiddenPolygonIds"
        :hidden-polyline-ids="activeHiddenPolylineIds"
        :hovered-polygon-id="activeHoveredPolygonId ?? undefined"
        :commander="commanderForSidebar"
        :document="activeDocument"
        :page="activePage"
        :open-tasks="activeOpenSubtasks"
        :task-by-id="activeTaskByIdRecord"
        :is-page-locked="isActivePageLocked"
        :is-tasks-loading="isOpenSubtasksLoading || isActivePageTasksLoading"
        :on-complete-task="completeSubtask"
        @apply-reading-order="handleApplyReadingOrder"
        @apply-metadata="handleApplyMetadataIfWritable"
        @select-polygon="(id, options) => activeControls?.selectPolygonById?.(id, options)"
        @select-polyline="(id, options) => activeControls?.selectPolylineById?.(id, options)"
        @hover-polygon="(id) => activeControls?.hoverPolygonById?.(id)"
        @hover-polyline="(id) => activeControls?.hoverPolylineById?.(id)"
        @unhover-polygon="() => { activeControls?.unhoverPolygon?.(); activeControls?.unhoverPolyline?.() }"
        @clear-selection="() => {
          activeControls?.selectPolygonById?.(null)
          activeControls?.selectPolylineById?.(null)
          if (activeControls?.selectedPolygonIds) activeControls.selectedPolygonIds.value = []
          if (activeControls?.selectedPolylineIds) activeControls.selectedPolylineIds.value = []
        }"
      />
      <EditorSidebarText
        v-else-if="activeUiMode === 'text'"
        :collapsed="editorUiStore.rightCollapsed"
        :canvas-id="activeCanvasId"
        :document="activeDocument"
        :page="activePage"
        :selected-element="textSidebarSelectedElement"
        :open-tasks="activeOpenSubtasks"
        :task-by-id="activeTaskByIdRecord"
        :is-page-locked="isActivePageLocked"
        :is-tasks-loading="isOpenSubtasksLoading || isActivePageTasksLoading"
        :on-complete-task="completeSubtask"
        @apply-metadata="handleApplyMetadataIfWritable"
      />
    </EditorRightSidebar>
  </div>
</template>
