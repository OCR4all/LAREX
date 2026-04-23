<script setup lang="ts">
import 'dockview-vue/dist/styles/dockview.css'
import {
  LazyEditorModalOpenProjectPages,
  LazyEditorSlideoverMergeSettings,
  LazyEditorSlideoverUnsavedProgress,
  LazyEditorVersionHistorySlideover,
  LazyProjectSlideoverXmlEditor,
  LazyCodecSlideoverAction,
  LazyUiConfirmSlideover
} from '#components'

import type { DockviewReadyEvent, DockviewTheme } from 'dockview-vue'
import * as dockviewVuePkg from 'dockview-vue'
import type { DockviewPanelApi } from 'dockview-core'

import type { DropdownMenuItem } from '@nuxt/ui'

import { useEditorStore } from '@/stores/editor/editor.store'
import { useEditorUiStore } from '@/stores/editor/editor.ui.store'
import { PolygonType, createPageXmlLabelSet } from '@/models/editor'
import type { Region, RegionKind } from '@/models/editor/region'
import type { TextLine } from '@/models/editor/text'
import { getEditorSession } from '@/session/editor/editor-session'
import { MergeElementsCommand } from '@/commands/editor/merge-elements-command'
import type { Commander } from '@/commands/editor/commander'
import type { MergeSettings } from '@/components/editor/slideover/merge-settings.vue'
import { createSkeletonPageData, type PageResponse } from '@/services/editor/project-loader'
import { useEditorSessionStore } from '@/stores/editor/editor.session.store'
import type { LabelSet as ApiLabelSet, LabelDefinition as ApiLabelDefinition } from '@/types/label-set'
import type { ValidateCodecAgainstSourcesResponse } from '@/types/codec'
import type { Dictionary } from '@/types/dictionary'
import type { RenderablePolygon, RenderablePolyline } from '@/types/editor/rendering'
import type { TreeItemData } from '@/components/editor/sidebar/tree-item.vue'
import { getCanvasId, getPagePanelId, getProjectPanelId, parseCanvasId, parseProjectPanelId } from '@/stores/editor/editor.keys'
import { useProjectDockviewRegistry } from '@/composables/editor/use-project-dockview-registry'
import { useProjectTabCloseState } from '@/composables/editor/use-project-tab-close-state'
import { useEditorCommandCenter } from '@/composables/editor/use-editor-command-center'
import type { OpenProjectPagesSelection } from '@/components/editor/modal/open-project-pages.vue'

import EditorEmpty from '@/components/editor/empty.vue'
import { useEditorIndexStatusPolling } from '@/composables/editor/use-editor-index-status-polling'
import { useEditorMetadataApply } from '@/composables/editor/use-editor-metadata-apply'
import { useEditorSidebarState } from '@/composables/editor/use-editor-sidebar-state'
import { useEditorSessionRestore } from '@/composables/editor/use-editor-session-restore'
import { useEditorTaskState } from '@/composables/editor/use-editor-task-state'
import { useEditorCollaboration } from '@/composables/editor/use-editor-collaboration'
import { UpdateReadingOrderCommand } from '@/commands'
import type { ReadingOrder } from '@/models/editor'
import type { EditorCanvasControls } from '@/types/editor/canvas-controls'

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
type RegisteredDockviewApi = Exclude<typeof dockviewApi.value, null>

function getErrorMessage(error: unknown, fallback: string): string {
  if (typeof error !== 'object' || error === null) return fallback
  const data = 'data' in error ? (error.data as { message?: unknown } | undefined) : undefined
  if (typeof data?.message === 'string' && data.message.trim().length > 0) return data.message
  if (error instanceof Error && error.message.trim().length > 0) return error.message
  return fallback
}

const LEFT_RAIL_WIDTH_PX = 64
const RIGHT_RAIL_WIDTH_PX = 48

const MIN_LEFT_WIDTH_PX = 250
const MAX_LEFT_WIDTH_PX = 500
const MIN_RIGHT_WIDTH_PX = 250
const MAX_RIGHT_WIDTH_PX = 800

type ResizeSide = 'left' | 'right'
const resizingSide = ref<ResizeSide | null>(null)
let resizeStartX = 0
let resizeStartWidth = 0
let rafLayoutId: number | null = null

const rootLayoutClass = computed(() => {
  switch (editorStore.toolbarLayout) {
    case 'docked-top':
      return 'grid grid-rows-[auto_1fr_auto] grid-cols-1 h-full'

    case 'docked-bottom':
      return 'grid grid-rows-[1fr_auto_auto] grid-cols-1 h-full'

    case 'docked-left':
      return 'grid grid-cols-[auto_1fr] grid-rows-[1fr_auto] h-full'

    case 'docked-right':
      return 'grid grid-cols-[1fr_auto] grid-rows-[1fr_auto] h-full'

    case 'floating':
    default:
      return 'grid grid-rows-[1fr_auto] grid-cols-1 h-full'
  }
})

const toolbarClass = computed(() => {
  switch (editorStore.toolbarLayout) {
    case 'floating':
      return 'fixed bottom-10 left-1/2 -translate-x-1/2 z-50'

    case 'docked-top':
      return 'row-start-1 col-span-full'

    case 'docked-bottom':
      return 'row-start-2 col-span-full'

    case 'docked-left':
      return 'col-start-1 row-span-full'

    case 'docked-right':
      return 'col-start-2 row-span-full'

    default:
      return 'row-start-1 col-span-full'
  }
})

const contentClass = computed(() => {
  switch (editorStore.toolbarLayout) {
    case 'docked-top':
      return 'row-start-2 col-span-full'
    case 'docked-bottom':
      return 'row-start-1 col-span-full'
    case 'docked-left':
      return 'row-start-1 col-start-2'
    case 'docked-right':
      return 'row-start-1 col-start-1'
    case 'floating':
    default:
      return 'row-start-1 col-span-full'
  }
})

const statusBarClass = computed(() => {
  switch (editorStore.toolbarLayout) {
    case 'docked-top':
      return 'row-start-3 col-span-full'
    case 'docked-bottom':
      return 'row-start-3 col-span-full'
    case 'docked-left':
      return 'row-start-2 col-start-2'
    case 'docked-right':
      return 'row-start-2 col-start-1'
    case 'floating':
    default:
      return 'row-start-2 col-span-full'
  }
})

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

const editorStore = useEditorStore()
const editorUiStore = useEditorUiStore()
const imageLoader = useEditorImageLoader()
const sessionStore = useEditorSessionStore()
const collaboration = useEditorCollaboration()
const projectDockviewRegistry = useProjectDockviewRegistry()
const projectTabCloseState = useProjectTabCloseState()
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
  onlyWithOpenSubtasks
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
const xmlEditorSlideover = overlay.create(LazyProjectSlideoverXmlEditor)
const codecActionSlideover = overlay.create(LazyCodecSlideoverAction)
const openProjectPagesModal = overlay.create(LazyEditorModalOpenProjectPages)
const confirmSlideover = overlay.create(LazyUiConfirmSlideover)

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
    const selectedPagesByName = naturalSortBy(selectedPages, 'name')

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
      const firstPageId = selectedPagesByName[0]?.id
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
    const firstPage = naturalSortBy(editorStore.getProjectPages(projectId), 'label')[0]
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

  if (!collaboration.canEditCanvas(canvasId)) {
    toast.add({
      title: 'Page is locked',
      description: 'Only the current editor can restore versions.',
      color: 'warning'
    })
    return
  }

  const instance = versionHistorySlideover.open({
    projectId: canvas.projectId,
    pageId: canvas.pageId,
    xmlId: canvas.xmlFileId,
    annotationBasePath: annotationContext.basePath
  })
  const result = await instance.result
  if (result === 'restored') {
    editorStore.invalidateAnnotationCache(canvas.pageId, canvas.projectId)
    await editorStore.loadPageIntoCanvas(canvasId, canvas.projectId, canvas.pageId)
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

const rightSidebarActionItems = computed<DropdownMenuItem[][]>(() => {
  const layoutActions: DropdownMenuItem[] = []

  if (activeUiMode.value === 'layout') {
    layoutActions.push(
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
      }
    )

    if (canCompleteActivePageSubtasks.value) {
      layoutActions.push({
        label: 'Save + Complete',
        icon: 'i-lucide-check-square',
        disabled: isSavingActiveCanvas.value || isCompletingOpenSubtasks.value || isActivePageLocked.value || !activeCanvasCanEdit.value,
        onSelect: () => {
          void handleSaveAndCompleteOpenSubtasks()
        }
      })
    }
  }

  const utilityActions: DropdownMenuItem[] = [
    {
      label: 'Check Codec',
      icon: 'i-lucide-badge-check',
      disabled: !canCheckCodecForLoadedPages.value,
      onSelect: () => {
        void openCodecValidationForLoadedPages()
      }
    }
  ]

  return layoutActions.length > 0 ? [layoutActions, utilityActions] : [utilityActions]
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

function getProjectContextMenuItems(projectId: string): DropdownMenuItem[][] {
  const hasProject = Boolean(openedProjectById.value[projectId])
  return [[
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
  accordionPanels,
  isCollapsedProjectOpen,
  toggleCollapsedProjectPanel
} = useEditorSidebarState({
  openedProjectIds: computed(() => [...sessionStore.openedProjectIds]),
  currentProjectId,
  isLeftCollapsed: computed(() => editorUiStore.leftCollapsed),
  isRightCollapsed: computed(() => editorUiStore.rightCollapsed),
  expandLeftSidebar: () => editorUiStore.toggleLeftCollapsed(),
  expandRightSidebar: () => editorUiStore.toggleRightCollapsed()
})

onBeforeUnmount(() => {
  for (const projectId of previousPrefetchScopeProjectIds) {
    editorStore.setAdjacentPrefetchPageScope(projectId, null)
  }
  previousPrefetchScopeProjectIds = []
})

function navigateImage(direction: 'next' | 'prev') {
  const projectId = currentProjectId.value
  if (!projectId) return
  const pages = editorStore.getProjectPages(projectId)
  if (pages.length === 0) return
  const currentIdx = pages.findIndex(p => p.id === editorStore.currentPageId)
  const newIdx = direction === 'next'
    ? Math.min(currentIdx + 1, pages.length - 1)
    : Math.max(currentIdx - 1, 0)
  if (newIdx !== currentIdx && pages[newIdx]) {
    void openEditorForPage(projectId, pages[newIdx].id)
  }
}

const activeCanvasId = computed(() => editorStore.activeCanvasId)
const isSavingActiveCanvas = computed(() => {
  const id = activeCanvasId.value
  if (!id) return false
  return editorStore.canvases[id]?.isSavingAnnotations === true
})
const activeUiMode = computed(() => editorStore.effectiveUiMode(activeCanvasId.value))

watch(activeUiMode, (mode) => {
  if (mode !== 'text') return
  void maybeAutoStartContextTour('/editor', { editorMode: 'text' })
})

const activeControls = computed<EditorCanvasControls | null>(() => {
  const id = activeCanvasId.value
  if (!id) return null
  return getEditorSession(id)?.controls.value ?? null
})

const activeSelectedPolygonId = computed(() => {
  const controls = activeControls.value
  const index = controls?.selectedPolygonIndex?.value ?? -1
  const polygons = controls?.polygons ?? []
  return index >= 0 ? polygons[index]?.id ?? null : null
})

const activeSelectedPolylineId = computed(() => {
  const controls = activeControls.value
  const index = controls?.selectedPolylineIndex?.value ?? -1
  const polylines = controls?.polylines ?? []
  return index >= 0 ? polylines[index]?.id ?? null : null
})

const activeHoveredPolygonId = computed(() => {
  const controls = activeControls.value
  return controls?.hoveredPolygonId?.value ?? null
})

const activeHoveredPolylineId = computed(() => {
  const controls = activeControls.value
  return controls?.hoveredPolylineId?.value ?? null
})

type StatusEntityInfo = {
  regionType: RegionKind | 'TextLine' | 'Baseline' | 'Polyline'
  subtype?: string
  id: string
  label?: string
  width?: number
  height?: number
  childCount?: number
}

type StatusPageSummary = {
  pageId: string
  variantLabel?: string
  totalRegions: number
  textRegions: number
  imageRegions: number
  lineDrawings: number
  tableRegions: number
  otherRegions: number
}

const activePolygons = computed<RenderablePolygon[]>(() => (activeControls.value?.polygons ?? []) as RenderablePolygon[])
const activePolylines = computed<RenderablePolyline[]>(() => (activeControls.value?.polylines ?? []) as RenderablePolyline[])
const activePolygonsForSidebar = computed<TreeItemData[]>(() => activePolygons.value as unknown as TreeItemData[])
const activePolylinesForSidebar = computed<TreeItemData[]>(() => activePolylines.value as unknown as TreeItemData[])

function getBounds(points?: { x: number, y: number }[]): { width: number, height: number } | null {
  if (!points || points.length === 0) return null

  let minX = Infinity
  let minY = Infinity
  let maxX = -Infinity
  let maxY = -Infinity

  for (const point of points) {
    if (point.x < minX) minX = point.x
    if (point.y < minY) minY = point.y
    if (point.x > maxX) maxX = point.x
    if (point.y > maxY) maxY = point.y
  }

  return {
    width: Math.max(0, Math.round(maxX - minX)),
    height: Math.max(0, Math.round(maxY - minY))
  }
}

function getChildCount(id: string, polygons: RenderablePolygon[], polylines: RenderablePolyline[]): number {
  let count = 0
  for (const polygon of polygons) {
    if (polygon.parentId === id) count += 1
  }
  for (const polyline of polylines) {
    if (polyline.parentId === id) count += 1
  }
  return count
}

function buildEntityFromPolygon(
  polygon: RenderablePolygon,
  polygons: RenderablePolygon[],
  polylines: RenderablePolyline[]
): StatusEntityInfo {
  const isTextLine = polygon.type === PolygonType.TEXTLINE || polygon.type === 'textline'
  const regionType = isTextLine ? 'TextLine' : (polygon.regionKind ?? 'CustomRegion')
  const subtype = !isTextLine ? polygon.regionSubtype : undefined

  const label = polygon.regionSubtype || polygon.label

  const bounds = getBounds(polygon.points)
  const childCount = getChildCount(polygon.id, polygons, polylines)

  return {
    regionType,
    subtype,
    id: polygon.id,
    label: label || undefined,
    width: bounds?.width,
    height: bounds?.height,
    childCount
  }
}

function buildEntityFromPolyline(
  polyline: RenderablePolyline,
  polygons: RenderablePolygon[],
  polylines: RenderablePolyline[]
): StatusEntityInfo {
  const regionType = polyline.type === PolygonType.BASELINE || polyline.type === 'baseline' ? 'Baseline' : 'Polyline'
  let label = polyline.label

  if (!label || label === 'baseline') {
    if (polyline.parentId) {
      const parentPolygon = polygons.find(polygon => polygon.id === polyline.parentId)
      label = parentPolygon?.regionSubtype || parentPolygon?.label || parentPolygon?.id
    }
  }

  const bounds = getBounds(polyline.points)
  const childCount = getChildCount(polyline.id, polygons, polylines)

  return {
    regionType,
    id: polyline.id,
    label: label || undefined,
    width: bounds?.width,
    height: bounds?.height,
    childCount
  }
}

const activeHoveredEntity = computed<StatusEntityInfo | null>(() => {
  const polygons = activePolygons.value
  const polylines = activePolylines.value

  if (activeHoveredPolylineId.value) {
    const polyline = polylines.find(item => item.id === activeHoveredPolylineId.value)
    return polyline ? buildEntityFromPolyline(polyline, polygons, polylines) : null
  }

  if (activeHoveredPolygonId.value) {
    const polygon = polygons.find(item => item.id === activeHoveredPolygonId.value)
    return polygon ? buildEntityFromPolygon(polygon, polygons, polylines) : null
  }

  return null
})

const activeSelectedEntity = computed<StatusEntityInfo | null>(() => {
  const polygons = activePolygons.value
  const polylines = activePolylines.value

  if (activeSelectedPolylineId.value) {
    const polyline = polylines.find(item => item.id === activeSelectedPolylineId.value)
    return polyline ? buildEntityFromPolyline(polyline, polygons, polylines) : null
  }

  if (activeSelectedPolygonId.value) {
    const polygon = polygons.find(item => item.id === activeSelectedPolygonId.value)
    return polygon ? buildEntityFromPolygon(polygon, polygons, polylines) : null
  }

  return null
})

function collectRegionCounts(regions: Region[] | undefined) {
  const counts = {
    totalRegions: 0,
    textRegions: 0,
    imageRegions: 0,
    lineDrawings: 0,
    tableRegions: 0,
    otherRegions: 0
  }

  if (!regions) return counts

  const stack = [...regions]
  while (stack.length) {
    const region = stack.pop()
    if (!region) continue
    counts.totalRegions += 1

    switch (region.kind) {
      case 'TextRegion':
        counts.textRegions += 1
        break
      case 'ImageRegion':
        counts.imageRegions += 1
        break
      case 'LineDrawingRegion':
        counts.lineDrawings += 1
        break
      case 'TableRegion':
        counts.tableRegions += 1
        break
      default:
        counts.otherRegions += 1
        break
    }

    if (region.regions && region.regions.length) {
      stack.push(...region.regions)
    }
  }

  return counts
}

function collectRegionCountsFromPolygons(polygons: RenderablePolygon[] | undefined) {
  const counts = {
    totalRegions: 0,
    textRegions: 0,
    imageRegions: 0,
    lineDrawings: 0,
    tableRegions: 0,
    otherRegions: 0
  }

  if (!polygons) return counts

  for (const polygon of polygons) {
    if (polygon.type !== PolygonType.REGION && polygon.type !== 'region') continue
    counts.totalRegions += 1

    switch (polygon.regionKind) {
      case 'TextRegion':
        counts.textRegions += 1
        break
      case 'ImageRegion':
        counts.imageRegions += 1
        break
      case 'LineDrawingRegion':
        counts.lineDrawings += 1
        break
      case 'TableRegion':
        counts.tableRegions += 1
        break
      default:
        counts.otherRegions += 1
        break
    }
  }

  return counts
}

const activePageSummary = computed<StatusPageSummary | null>(() => {
  const page = activePage.value
  if (!page) return null

  const hasSyncedPolygons = Array.isArray(activeControls.value?.polygons)
  const counts = hasSyncedPolygons
    ? collectRegionCountsFromPolygons(activePolygons.value)
    : collectRegionCounts(page.regions)
  const pageId = activePageId.value ?? null
  const activeProjectId = currentProjectId.value ?? undefined
  const pageData = pageId ? editorStore.getPage(pageId, activeProjectId) : undefined
  const pageLabel = pageData?.label || pageId || page.imageFilename || '—'
  const variantLabel = pageData ? editorStore.getDisplayedVariantForPage(pageData)?.label : undefined

  return {
    pageId: pageLabel,
    variantLabel,
    totalRegions: counts.totalRegions,
    textRegions: counts.textRegions,
    imageRegions: counts.imageRegions,
    lineDrawings: counts.lineDrawings,
    tableRegions: counts.tableRegions,
    otherRegions: counts.otherRegions
  }
})

const activeCollaborators = computed(() => {
  const canvasId = activeCanvasId.value
  if (!canvasId) return []
  return collaboration.getCanvasCollaborators(canvasId)
})

const activeCanvasEditor = computed(() => {
  const canvasId = activeCanvasId.value
  if (!canvasId) return null
  return collaboration.getCanvasEditor(canvasId)
})

const activePendingTakeover = computed(() => {
  const canvasId = activeCanvasId.value
  if (!canvasId) return null
  return collaboration.getCanvasPendingTakeover(canvasId)
})

const activeCanvasCanEdit = computed(() => {
  const canvasId = activeCanvasId.value
  if (!canvasId) return true
  return collaboration.canEditCanvas(canvasId)
})

const activeAnnotationMode = computed<'PROJECT' | 'DATASET_LINK' | 'DATASET_COPY' | null>(() => {
  const canvasId = activeCanvasId.value
  if (!canvasId) return null
  const annotationContext = resolveCanvasAnnotationContext(canvasId)
  return annotationContext?.mode ?? null
})

const canOpenActiveCanvasXmlEditor = computed(() => {
  const canvasId = activeCanvasId.value
  if (!canvasId) return false
  const canvas = editorStore.canvases[canvasId]
  return Boolean(canvas?.projectId && canvas.pageId && canvas.xmlFileId)
})

const activeSelectedPolygonIds = computed(() => activeControls.value?.selectedPolygonIds?.value ?? [])
const activeSelectedPolylineIds = computed(() => activeControls.value?.selectedPolylineIds?.value ?? [])
const activeHiddenPolygonIds = computed(() => activeControls.value?.hiddenPolygonIds?.value ?? [])
const activeHiddenPolylineIds = computed(() => activeControls.value?.hiddenPolylineIds?.value ?? [])
const activePageId = computed(() => {
  const canvasId = activeCanvasId.value
  const canvasPageId = canvasId ? (editorStore.canvases[canvasId]?.pageId ?? null) : null
  if (canvasPageId) return canvasPageId

  const controlsPageId = activeControls.value?.pageId?.value ?? null
  if (!controlsPageId) return null
  const parsedCanvas = parseCanvasId(controlsPageId)
  return parsedCanvas?.pageId ?? controlsPageId
})

const activeDocument = computed(() => {
  const canvasId = activeCanvasId.value
  if (!canvasId) return null
  return getEditorSession(canvasId)?.document.value ?? null
})

const activePage = computed(() => {
  return activeDocument.value?.page ?? null
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

const isActivePageLocked = computed(() => {
  const pageId = activePageId.value
  if (!pageId) return false
  return editorStore.pages.find(p => p.id === pageId)?.locked ?? false
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

  if (hasBackendFilters.value) {
    const filteredIds = backendFilteredPageIdsByProjectId.value[projectId]
    if (filteredIds) {
      const filteredIdSet = new Set(filteredIds)
      result = result.filter(p => filteredIdSet.has(p.id))
    }
  }

  return result
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
  if (!canvasId || !commander) return

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
        controls?.handleUndo?.()
      },
      handleRedo: () => {
        const controls = activeControls.value
        controls?.handleRedo?.()
      },
      setDrawingMode: (mode: DrawingMode) => {
        const controls = activeControls.value
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
      centerOnSelection: () => {
      },
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
        if (!controls) return
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
      closeActiveTab: () => {
        const canvasId = activeCanvasId.value
        if (!canvasId) return
        const canvas = editorStore.canvases[canvasId]
        const projectId = canvas?.projectId ?? null
        const pageId = canvas?.pageId ?? null
        if (!projectId || !pageId) return

        const innerApi = projectDockviewRegistry.get(projectId)
        if (!innerApi) return

        const panel = innerApi.getPanel(getPagePanelId(projectId, pageId))
        if (!panel) return
        handleCloseRequest({ panelApi: panel.api, projectId, pageId })
      }
    }
  })
}

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
  const api = projectDockviewRegistry.get(projectId)
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
    params: {
      projectId,
      pageId,
      canvasId,
      variantId: canvas?.imageVariantId ?? undefined
    }
  })
}

const loadedProjectMetadata = ref<Set<string>>(new Set())
const isApplyingPageDeepLink = ref(false)

function getSingleQueryValue(value: unknown): string | null {
  if (Array.isArray(value)) {
    const first = value[0]
    value = typeof first === 'string' ? first : null
  }
  if (typeof value !== 'string') return null

  const trimmed = value.trim()
  return trimmed.length > 0 ? trimmed : null
}

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
      defaultGtIndex?: number | null
      defaultRecognitionIndices?: number[] | null
    }>(`/api/workspaces/${selectedWorkspace.value}/projects/${projectId}`)
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

  await loadProjectLabelSet(projectId)

  if (!isAlreadyLoaded) {
    await editorStore.loadPageIntoCanvas(canvasId, projectId, pageId, variantId)
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
  ensurePagePanelExists(projectId, pageId)
  projectDockviewRegistry.get(projectId)?.getPanel(getPagePanelId(projectId, pageId))?.api.setActive()
}

async function applyPageDeepLinkFromQuery(): Promise<void> {
  if (isApplyingPageDeepLink.value) return

  const projectId = getSingleQueryValue(route.query.projectId)
  const pageId = getSingleQueryValue(route.query.pageId)
  const variantId = getSingleQueryValue(route.query.variantId) ?? undefined
  const editorMode = getSingleQueryValue(route.query.editorMode)
  const textView = getSingleQueryValue(route.query.textView)
  const textSearch = getSingleQueryValue(route.query.textSearch)
  if (!projectId || !pageId) return

  isApplyingPageDeepLink.value = true
  try {
    await openEditorForPage(projectId, pageId, variantId)

    const wasOpened = sessionStore.getOpenedPageIds(projectId).includes(pageId)
    if (!wasOpened) {
      toast.add({
        title: 'Unable to open linked page',
        description: 'The page does not exist or you do not have access to it.',
        color: 'error',
        icon: 'i-lucide-alert-circle'
      })
      return
    }

    if (editorMode === 'text') {
      editorStore.setUiMode('text')
      const nextTextSubmode = textView === 'expert' || textView === 'textline' || textView === 'region'
        ? 'expert'
        : 'visual'
      editorUiStore.setTextModeSubmode(nextTextSubmode)
      sessionStore.updateTextViewSettings(current => ({
        ...current,
        mode: 'textline',
        searchQuery: textSearch ?? current.searchQuery
      }))
    }

    const nextQuery = { ...route.query }
    delete nextQuery.projectId
    delete nextQuery.pageId
    delete nextQuery.variantId
    delete nextQuery.editorMode
    delete nextQuery.textView
    delete nextQuery.textSearch
    await router.replace({ path: route.path, query: nextQuery })
  } catch (error) {
    toast.add({
      title: 'Failed to open linked page',
      description: getErrorMessage(error, 'An unexpected error occurred while opening the page link.'),
      color: 'error',
      icon: 'i-lucide-alert-circle'
    })
  } finally {
    isApplyingPageDeepLink.value = false
  }
}

async function applyProjectDeepLinkFromQuery(): Promise<void> {
  if (isApplyingPageDeepLink.value) return

  const scope = getSingleQueryValue(route.query.scope)
  const projectId = getSingleQueryValue(route.query.projectId)
  const pageId = getSingleQueryValue(route.query.pageId)
  if (scope !== 'project' || !projectId || pageId) return

  isApplyingPageDeepLink.value = true
  try {
    const loaded = await ensureFullProjectPagesLoaded(projectId)
    if (!loaded) {
      toast.add({
        title: 'Unable to open linked project',
        description: 'The project does not exist or you do not have access to it.',
        color: 'error',
        icon: 'i-lucide-alert-circle'
      })
      return
    }

    const pages = naturalSortBy(editorStore.getProjectPages(projectId), 'label')
    if (pages.length === 0) {
      toast.add({
        title: 'Unable to open linked project',
        description: 'No accessible pages were found for this project.',
        color: 'error',
        icon: 'i-lucide-alert-circle'
      })
      return
    }

    const firstPage = pages[0]
    if (firstPage) {
      await openEditorForPage(projectId, firstPage.id)
    }

    const nextQuery = { ...route.query }
    delete nextQuery.projectId
    delete nextQuery.scope
    delete nextQuery.variantId
    await router.replace({ path: route.path, query: nextQuery })
  } catch (error) {
    toast.add({
      title: 'Failed to open linked project',
      description: getErrorMessage(error, 'An unexpected error occurred while opening the project link.'),
      color: 'error',
      icon: 'i-lucide-alert-circle'
    })
  } finally {
    isApplyingPageDeepLink.value = false
  }
}

async function applyEditorDeepLinkFromQuery(): Promise<void> {
  const projectId = getSingleQueryValue(route.query.projectId)
  const pageId = getSingleQueryValue(route.query.pageId)
  if (projectId && pageId) {
    await applyPageDeepLinkFromQuery()
    return
  }

  await applyProjectDeepLinkFromQuery()
}

function handleSelectPage(pageId: string, variantId?: string, projectId?: string) {
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

const onReady = (event: DockviewReadyEvent) => {
  dockviewApi.value = event.api

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
      const stillExists = dockviewApi.value?.getPanel?.(panelId)
      if (stillExists) return

      const projectId = parseProjectPanelId(panelId)
      if (projectId) {
        projectTabCloseState.consumeAutoClosed(projectId)
        projectTabCloseState.consumeExplicitClose(projectId)
        clearProjectTabState(projectId)
      }
    }, 100)
  })

  for (const projectId of sessionStore.openedProjectIds) {
    ensureProjectPanelExists(event.api, projectId)
  }
  tryCreateInitialPanels()
}
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
      :left-rail-width-px="LEFT_RAIL_WIDTH_PX"
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
      @confirm-unload-active-project="confirmAndUnloadProject"
    >
      <EditorProjectListShell
        v-model:project-accordion-panels="projectAccordionPanels"
        :collapsed="editorUiStore.leftCollapsed"
        :projects="openedProjectsForSidebar"
        :project-accordion-items="projectAccordionItems"
        :page-name-filter="pageNameFilter"
        :only-with-open-subtasks="onlyWithOpenSubtasks"
        :has-backend-filters="hasBackendFilters"
        :backend-filtered-page-ids-by-project-id="backendFilteredPageIdsByProjectId"
        :get-project-context-menu-items="getProjectContextMenuItems"
        :is-collapsed-project-open="isCollapsedProjectOpen"
        :toggle-collapsed-project-panel="toggleCollapsedProjectPanel"
        @select-page="handleSelectPage"
        @unload-page="handleUnloadPage"
      />
    </EditorLeftSidebar>

    <div
      v-show="!editorUiStore.leftCollapsed"
      class="group h-full w-0 shrink-0 cursor-col-resize touch-none relative overflow-visible"
      @pointerdown="(e) => startResize('left', e)"
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
      class="flex-1 min-w-0 min-h-0 overflow-hidden relative"
      :class="rootLayoutClass"
    >
      <EditorToolbar
        v-if="activeCanvasId"
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
      v-show="!editorUiStore.rightCollapsed"
      class="group h-full w-0 shrink-0 cursor-col-resize touch-none relative overflow-visible"
      @pointerdown="(e) => startResize('right', e)"
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
      :right-rail-width-px="RIGHT_RAIL_WIDTH_PX"
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
        @apply-metadata="handleApplyMetadata"
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
        @apply-metadata="handleApplyMetadata"
      />
    </EditorRightSidebar>
  </div>
</template>
