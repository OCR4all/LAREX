<script setup lang="ts">
import { useEditorStore } from '@/stores/editor/editor.store'
import { DRAWING_MODES, VIEW_MODES } from '@/composables/editor/use-canvas-control'
import { getTooltipProps } from '@/composables/editor/use-keyboard-shortcuts'
import { useVirtualKeyboardAvailability } from '@/composables/use-virtual-keyboards'
import { PolygonType } from '@/models/editor'
import type { RenderablePolygon, RenderablePolyline } from '@/types/editor/rendering'
import type { DropdownMenuItem, SelectMenuItem } from '@nuxt/ui'
import { useFloatingAnchorPosition } from '@/composables/editor/use-floating-anchor-position'
import { EDITOR_WORKSPACE_FLOATING_ANCHOR_ID, ensureEditorSession, getEditorSession } from '@/session/editor/editor-session'
import {
  getOrCreateSessionCommander,
  jumpSessionCommandHistory,
  redoSessionCommand,
  undoSessionCommand
} from '@/session/editor/canvas-commander'
import type { Commander } from '@/commands/editor/commander'
import type { EditorCanvasControls } from '@/types/editor/canvas-controls'
import type { LayoutViewMode, TextModeSubmode, VirtualKeyboardMode } from '@/stores/editor/types'
import type { FloatingControlOffset } from '@/utils/editor/floating-anchor-position'

const editorStore = useEditorStore()
const uiStore = useEditorUiStore()

const emit = defineEmits<{
  merge: []
}>()

type HistoryItem = ReturnType<Commander['getDetailedHistory']>[number]
type ModeViewValue = `layout:${LayoutViewMode}` | `text:${TextModeSubmode}`
type ModeViewOptionBase = {
  value: ModeViewValue
  modeLabel: 'Layout' | 'Text'
  label: string
  description: string
  icon: string
  kbds?: string[]
}
type LayoutModeViewOption = ModeViewOptionBase & {
  mode: 'layout'
  view: LayoutViewMode
}
type TextModeViewOption = ModeViewOptionBase & {
  mode: 'text'
  view: TextModeSubmode
}
type ModeViewOption = LayoutModeViewOption | TextModeViewOption
type ModeViewGroupLabel = SelectMenuItem & {
  type: 'label'
  label: string
  value: ModeViewValue
}

const toolbarLayoutItems = computed<DropdownMenuItem[][]>(() => [
  [
    {
      label: 'Floating',
      icon: 'i-lucide-panel-bottom-dashed',
      type: 'checkbox',
      checked: editorStore.toolbarLayout === 'floating',
      onUpdateChecked(checked: boolean) {
        if (checked) editorStore.setToolbarLayout('floating')
      },
      onSelect(e: Event) {
        e.preventDefault()
        editorStore.setToolbarLayout('floating')
      }
    }
  ],
  [
    {
      label: 'Top (Docked)',
      icon: 'i-lucide-panel-top',
      onSelect() {
        editorStore.setToolbarLayout('docked-top')
      }
    },
    {
      label: 'Bottom (Docked)',
      icon: 'i-lucide-panel-bottom',
      onSelect() {
        editorStore.setToolbarLayout('docked-bottom')
      }
    },
    {
      label: 'Left (Docked)',
      icon: 'i-lucide-panel-left',
      onSelect() {
        editorStore.setToolbarLayout('docked-left')
      }
    },
    {
      label: 'Right (Docked)',
      icon: 'i-lucide-panel-right',
      onSelect() {
        editorStore.setToolbarLayout('docked-right')
      }
    }
  ]
])

const toolbarLayoutIcon = computed(() => {
  switch (editorStore.toolbarLayout) {
    case 'floating':
      return 'i-lucide-panel-bottom-dashed'
    case 'docked-top':
      return 'i-lucide-panel-top'
    case 'docked-bottom':
      return 'i-lucide-panel-bottom'
    case 'docked-left':
      return 'i-lucide-panel-left'
    case 'docked-right':
      return 'i-lucide-panel-right'
    default:
      return 'i-lucide-panel-top'
  }
})

const props = defineProps({
  canvasId: {
    type: String,
    default: null
  }
})

const toolbarActiveToolClass = 'bg-primary text-inverted hover:bg-primary/75 active:bg-primary/75 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary'
const toolbarActiveDropdownItemClass = 'text-inverted before:bg-primary data-highlighted:text-inverted data-highlighted:before:bg-primary/75 data-[state=open]:text-inverted data-[state=open]:before:bg-primary/75'
const toolbarActiveDropdownItemUi = {
  itemLeadingIcon: 'text-inverted group-data-highlighted:text-inverted group-data-[state=open]:text-inverted',
  itemTrailingIcon: 'text-inverted group-data-highlighted:text-inverted group-data-[state=open]:text-inverted'
} as const

function activeToolClass(active: boolean): string | undefined {
  return active ? toolbarActiveToolClass : undefined
}

function activeDropdownItemClass(active: boolean): string {
  return active ? toolbarActiveDropdownItemClass : ''
}

function activeDropdownItemUi(active: boolean): typeof toolbarActiveDropdownItemUi | undefined {
  return active ? toolbarActiveDropdownItemUi : undefined
}

const currentCanvasId = computed(() => props.canvasId ?? editorStore.activeCanvasId)
const floatingAnchorId = computed(() => EDITOR_WORKSPACE_FLOATING_ANCHOR_ID)

const isFloating = computed(() => {
  return editorStore.toolbarLayout === 'floating'
})

const isVertical = computed(() => {
  return ['docked-left', 'docked-right'].includes(editorStore.toolbarLayout)
})

const modeViewMenuSide = computed<'top' | 'bottom' | 'left' | 'right'>(() => {
  if (editorStore.toolbarLayout === 'docked-left') return 'right'
  if (editorStore.toolbarLayout === 'docked-right') return 'left'
  if (editorStore.toolbarLayout === 'docked-top') return 'bottom'
  return 'top'
})

const toolbarStyle = computed(() => {
  switch (editorStore.toolbarLayout) {
    case 'floating':
      return 'border border-default rounded-xl shadow-2xl'

    case 'docked-top':
      return 'row-start-1 col-span-full border-b border-default'

    case 'docked-bottom':
      return 'row-start-2 col-span-full border-t border-default'

    case 'docked-left':
      return 'col-start-1 row-span-full h-full border-r border-default'

    case 'docked-right':
      return 'col-start-2 row-span-full h-full border-l border-default'

    default:
      return 'row-start-1 col-span-full'
  }
})

const toolbarShellRef = ref<HTMLElement | null>(null)
const DEFAULT_FLOATING_TOOLBAR_BOTTOM_GAP = 56
const DEFAULT_FLOATING_TOOLBAR_BOTTOM = 56
const {
  style: floatingToolbarStyle,
  isDragging: isDraggingToolbar,
  startDrag: startToolbarDrag
} = useFloatingAnchorPosition({
  enabled: isFloating,
  anchorId: floatingAnchorId,
  shellRef: toolbarShellRef,
  placement: 'toolbar',
  fallbackSize: { width: 360, height: 48 },
  gap: DEFAULT_FLOATING_TOOLBAR_BOTTOM_GAP,
  includeFixedPosition: true,
  viewportMargin: { bottom: DEFAULT_FLOATING_TOOLBAR_BOTTOM },
  getOffset: () => uiStore.toolbarFloatingOffset,
  setOffset: (offset: FloatingControlOffset | null) => {
    if (!offset) return
    uiStore.setToolbarFloatingOffset(offset.dx, offset.dy, { persist: false })
  }
})

const effectiveUiMode = computed(() => editorStore.effectiveUiMode(currentCanvasId.value))

const perPanelUiModeModel = computed({
  get: () => editorStore.uiModeScope === 'per-canvas',
  set: next => editorStore.setUiModeScope(next ? 'per-canvas' : 'global')
})

const currentCanvasState = computed<EditorCanvasControls | undefined>(() => {
  const id = currentCanvasId.value
  if (!id) return undefined
  if (import.meta.client) {
    return ensureEditorSession(id).controls.value ?? undefined
  }
  return getEditorSession(id)?.controls.value ?? undefined
})

const drawingMode = computed(() => currentCanvasState.value?.drawingMode?.value || DRAWING_MODES.SELECT)
const selectedPolygonIndex = computed(() => currentCanvasState.value?.selectedPolygonIndex?.value ?? -1)

const activeCommander = computed<Commander | null>(() => {
  if (!import.meta.client) return null
  const canvasId = currentCanvasId.value
  if (!canvasId) return null
  return getOrCreateSessionCommander(canvasId)
})

const detachedCommanderState = ref<ReturnType<Commander['getState']>>({
  currentIndex: -1,
  totalCount: 0,
  canUndo: false,
  canRedo: false
})

function syncDetachedCommanderState(): void {
  const commander = activeCommander.value
  detachedCommanderState.value = commander
    ? commander.getState()
    : {
        currentIndex: -1,
        totalCount: 0,
        canUndo: false,
        canRedo: false
      }
}

const canUndo = computed(() => {
  if (currentCanvasState.value?.canUndo) return currentCanvasState.value.canUndo.value
  return detachedCommanderState.value.canUndo
})
const canRedo = computed(() => {
  if (currentCanvasState.value?.canRedo) return currentCanvasState.value.canRedo.value
  return detachedCommanderState.value.canRedo
})
const canEditCurrentCanvas = computed(() => currentCanvasState.value?.isCanvasEditable?.value ?? false)

const selectedRegionType = computed({
  get: () => currentCanvasState.value?.regionType?.value ?? PolygonType.REGION,
  set: (value) => {
    if (!value) return
    currentCanvasState.value?.setRegionType?.(value)
  }
})

const selectedViewMode = computed({
  get: () => currentCanvasState.value?.viewMode?.value ?? VIEW_MODES.DEFAULT,
  set: (mode) => {
    if (!mode) return
    currentCanvasState.value?.setViewMode?.(mode)
  }
})

const historyItems = ref<HistoryItem[]>([])

const historyDropdownItems = computed(() => {
  const currentIndex = currentCanvasState.value?.historyState?.currentIndex
    ?? detachedCommanderState.value.currentIndex
    ?? -1

  if (historyItems.value.length === 0) {
    return [{ label: 'No commands in history', disabled: true }]
  }

  const initialStateItem = {
    label: `0. Initial state${currentIndex === -1 ? ' →' : ''}`,
    disabled: currentIndex === -1,
    onSelect: () => handleHistoryItemClick(-1)
  }

  const commandItems = historyItems.value.map(item => ({
    label: `${item.index + 1}. ${item.description}${item.isCurrent ? ' →' : ''}`,
    disabled: item.index === currentIndex,
    onSelect: () => handleHistoryItemClick(item.index)
  }))

  return [initialStateItem, ...commandItems]
})

const updateHistoryItems = () => {
  syncDetachedCommanderState()
  const commander = currentCanvasState.value?.commander ?? activeCommander.value
  historyItems.value = commander ? commander.getDetailedHistory() : []
}

watch(
  () => {
    const canvasId = currentCanvasId.value
    if (!canvasId || !import.meta.client) return [null, null] as const
    const session = getEditorSession(canvasId)
    return [
      session?.document.value ?? null,
      session?.controls.value?.historyState?.currentIndex ?? null
    ] as const
  },
  () => {
    updateHistoryItems()
  },
  { immediate: true }
)

const handleHistoryItemClick = (targetIndex: number) => {
  if (currentCanvasState.value?.jumpToHistory) {
    currentCanvasState.value.jumpToHistory(targetIndex)
    updateHistoryItems()
    return
  }

  const canvasId = currentCanvasId.value
  if (!canvasId) return

  if (jumpSessionCommandHistory(canvasId, targetIndex)) {
    updateHistoryItems()
  }
}

const isSelectMode = computed(() => drawingMode.value === DRAWING_MODES.SELECT)
const isMoveMode = computed(() => drawingMode.value === DRAWING_MODES.MOVE)
const isDrawingMode = computed(() => drawingMode.value !== DRAWING_MODES.SELECT && drawingMode.value !== DRAWING_MODES.MOVE)
const isPolygonMode = computed(() => drawingMode.value === DRAWING_MODES.POLYGON)
const isRectangleMode = computed(() => drawingMode.value === DRAWING_MODES.RECTANGLE)
const isPolylineMode = computed(() => drawingMode.value === DRAWING_MODES.POLYLINE)
const isCutLineMode = computed(() => drawingMode.value === DRAWING_MODES.CUT_LINE)
const isCutPolygonMode = computed(() => drawingMode.value === DRAWING_MODES.CUT_POLYGON)
const isCutRectangleMode = computed(() => drawingMode.value === DRAWING_MODES.CUT_RECTANGLE)
const isCutMode = computed(() => isCutLineMode.value || isCutPolygonMode.value || isCutRectangleMode.value)

const isRegionTypeRegion = computed(() => selectedRegionType.value === PolygonType.REGION)
const isRegionTypeTextline = computed(() => selectedRegionType.value === PolygonType.TEXTLINE)
const isRegionTypeBaseline = computed(() => selectedRegionType.value === PolygonType.BASELINE)

const isTextUiMode = computed(() => effectiveUiMode.value === 'text')

const editorModeModel = computed({
  get: () => effectiveUiMode.value,
  set: (mode: 'layout' | 'text') => {
    uiStore.setUiMode(mode, currentCanvasId.value)
  }
})

const textModeSubmodeModel = computed({
  get: () => uiStore.textModeSubmode,
  set: (next: TextModeSubmode) => {
    uiStore.setTextModeSubmode(next)
  }
})

const modeViewOptions = computed<ModeViewOption[]>(() => [
  {
    value: `layout:${VIEW_MODES.DEFAULT}`,
    mode: 'layout',
    modeLabel: 'Layout',
    view: VIEW_MODES.DEFAULT,
    label: 'Hierarchy',
    description: 'Browse and edit the region hierarchy.',
    icon: 'i-lucide-layers',
    kbds: getTooltipProps('defaultView').kbds
  },
  {
    value: `layout:${VIEW_MODES.TEXTLINE}`,
    mode: 'layout',
    modeLabel: 'Layout',
    view: VIEW_MODES.TEXTLINE,
    label: 'Text lines',
    description: 'Focus selection and editing on text-line geometry.',
    icon: 'i-lucide-type',
    kbds: getTooltipProps('textlineView').kbds
  },
  {
    value: `layout:${VIEW_MODES.BASELINE}`,
    mode: 'layout',
    modeLabel: 'Layout',
    view: VIEW_MODES.BASELINE,
    label: 'Baselines',
    description: 'Focus selection and editing on baselines.',
    icon: 'i-lucide-baseline',
    kbds: getTooltipProps('baselineView').kbds
  },
  {
    value: 'text:visual',
    mode: 'text',
    modeLabel: 'Text',
    view: 'visual',
    label: 'Canvas',
    description: 'Correct ground truth directly on the page canvas.',
    icon: 'i-lucide-notebook-pen',
    kbds: getTooltipProps('textCanvasView').kbds
  },
  {
    value: 'text:expert',
    mode: 'text',
    modeLabel: 'Text',
    view: 'expert',
    label: 'List',
    description: 'Review text lines with search, sorting, and filters.',
    icon: 'i-lucide-list-filter',
    kbds: getTooltipProps('textListView').kbds
  }
])

const modeViewMenuItems = computed<Array<Array<ModeViewGroupLabel | ModeViewOption>>>(() => [
  [
    {
      type: 'label',
      label: 'Layout',
      value: `layout:${VIEW_MODES.DEFAULT}`
    },
    ...modeViewOptions.value.filter(option => option.mode === 'layout')
  ],
  [
    {
      type: 'label',
      label: 'Text',
      value: 'text:visual'
    },
    ...modeViewOptions.value.filter(option => option.mode === 'text')
  ]
])

const modeViewModel = computed<ModeViewValue>({
  get: (): ModeViewValue => {
    if (isTextUiMode.value) return `text:${textModeSubmodeModel.value}`
    return `layout:${selectedViewMode.value}`
  },
  set: (value: ModeViewValue) => {
    const option = modeViewOptions.value.find(item => item.value === value)
    if (option) selectModeView(option)
  }
})

const activeModeViewOption = computed<ModeViewOption>(() =>
  modeViewOptions.value.find(option => option.value === modeViewModel.value)
  ?? modeViewOptions.value[0]!
)
const modeViewLabel = computed(() =>
  `${activeModeViewOption.value.modeLabel} · ${activeModeViewOption.value.label}`
)
const modeViewAriaLabel = computed(() =>
  `Editor mode and view: ${activeModeViewOption.value.modeLabel}, ${activeModeViewOption.value.label}`
)

const selectedPolygon = computed<RenderablePolygon | undefined>(() => {
  const list = currentCanvasState.value?.polygons as RenderablePolygon[] | undefined
  const idx = selectedPolygonIndex.value
  if (!list || idx < 0 || idx >= list.length) return undefined
  return list[idx]
})

const isSelectedRegion = computed(() => selectedPolygon.value?.type === PolygonType.REGION)
const isSelectedTextline = computed(() => selectedPolygon.value?.type === PolygonType.TEXTLINE)
/** Check if selected region is a TextRegion (can contain TextLines) */
const isSelectedTextRegion = computed(() =>
  isSelectedRegion.value && selectedPolygon.value?.regionKind === 'TextRegion'
)

const selectedTextlineHasBaseline = computed(() => {
  if (!isSelectedTextline.value) return false
  const textlineId = selectedPolygon.value?.id
  if (!textlineId) return false
  const baselines = (currentCanvasState.value?.polylines as RenderablePolyline[] | undefined) ?? []
  return baselines.some(b => b.parentId === textlineId)
})

const canCreateRegion = computed(() => canEditCurrentCanvas.value && !isSelectedTextline.value)
/**
 * TextLines can be created:
 * 1. When a TextRegion is selected (traditional mode)
 * 2. When in Textline view mode (auto-parent mode - will create helper region if needed)
 */
const canCreateTextline = computed(() =>
  canEditCurrentCanvas.value && (isSelectedTextRegion.value || selectedViewMode.value === VIEW_MODES.TEXTLINE)
)
/**
 * Baselines can be created:
 * 1. When a TextLine is selected and has no baseline yet (traditional mode)
 * 2. When in Baseline view mode (auto-parent mode - will create helper textline/region if needed)
 */
const canCreateBaseline = computed(() =>
  canEditCurrentCanvas.value && ((isSelectedTextline.value && !selectedTextlineHasBaseline.value) || selectedViewMode.value === VIEW_MODES.BASELINE)
)

const selectedPolygonIds = computed(() => currentCanvasState.value?.selectedPolygonIds?.value ?? [])
const selectedPolylineIds = computed(() => currentCanvasState.value?.selectedPolylineIds?.value ?? [])

const canMerge = computed(() => {
  if (!canEditCurrentCanvas.value) return false
  if (selectedPolylineIds.value.length > 0) return false
  if (selectedPolygonIds.value.length < 2) return false

  const polygonList = currentCanvasState.value?.polygons as RenderablePolygon[] | undefined
  if (!polygonList) return false

  const selectedPolygons = polygonList.filter(p => selectedPolygonIds.value.includes(p.id))
  if (selectedPolygons.length < 2) return false

  const types = new Set(selectedPolygons.map(p => p.type))
  return types.size === 1 && (types.has(PolygonType.REGION) || types.has(PolygonType.TEXTLINE))
})

const handleMerge = () => {
  if (!canMerge.value) return
  emit('merge')
}

const handleToggleActionWand = () => {
  if (!canEditCurrentCanvas.value) return
  const controls = currentCanvasId.value ? getEditorSession(currentCanvasId.value)?.controls.value : null
  controls?.toggleSelectMode?.()
  uiStore.toggleActionWand()
}

function cancelActionWand() {
  if (uiStore.actionWandActive) {
    uiStore.setActionWandActive(false)
  }
}

function canActivateEntry(entry: 'region' | 'textline' | 'baseline') {
  if (!canEditCurrentCanvas.value) return false
  if (entry === 'region') return canCreateRegion.value
  if (entry === 'textline') return canCreateTextline.value
  return canCreateBaseline.value
}

type ShapeOption = 'polygon' | 'rectangle' | 'polyline'

const preferredShapeByEntry = reactive<{ region: ShapeOption, textline: ShapeOption }>({
  region: 'polygon',
  textline: 'polygon'
})

function getPrimaryShapeForEntry(entry: 'region' | 'textline' | 'baseline'): ShapeOption {
  if (entry === 'baseline') return 'polyline'

  return entry === 'region' ? preferredShapeByEntry.region : preferredShapeByEntry.textline
}

function getIconForShape(option: ShapeOption): string {
  if (option === 'rectangle') return 'i-lucide-square'
  if (option === 'polyline') return 'i-lucide-activity'
  return 'i-lucide-pen-tool'
}

function getEntryToolLabel(entry: 'region' | 'textline'): string {
  const element = entry === 'region' ? 'Region' : 'Text line'
  const shape = getPrimaryShapeForEntry(entry)
  return `${element} ${shape}`
}

const handleToggleSelectMode = () => {
  cancelActionWand()
  if (currentCanvasState.value?.toggleSelectMode) {
    currentCanvasState.value.toggleSelectMode()
  }
}

const handleToggleMoveMode = () => {
  cancelActionWand()
  if (currentCanvasState.value?.toggleMoveMode) {
    currentCanvasState.value.toggleMoveMode()
  }
}

const handleUndo = () => {
  if (currentCanvasState.value?.handleUndo) {
    currentCanvasState.value.handleUndo()
    updateHistoryItems()
    return
  }

  const canvasId = currentCanvasId.value
  if (!canvasId) return

  if (undoSessionCommand(canvasId)) {
    updateHistoryItems()
  }
}

const handleRedo = () => {
  if (currentCanvasState.value?.handleRedo) {
    currentCanvasState.value.handleRedo()
    updateHistoryItems()
    return
  }

  const canvasId = currentCanvasId.value
  if (!canvasId) return

  if (redoSessionCommand(canvasId)) {
    updateHistoryItems()
  }
}

function setEntryAndMode(entry: 'region' | 'textline' | 'baseline', option?: ShapeOption) {
  if (!currentCanvasState.value) return
  if (!canActivateEntry(entry)) return
  cancelActionWand()

  const next = option ?? getPrimaryShapeForEntry(entry)

  if (entry === 'region') {
    preferredShapeByEntry.region = next
  } else if (entry === 'textline') {
    preferredShapeByEntry.textline = next
  }

  if (entry === 'region') {
    selectedRegionType.value = PolygonType.REGION
  } else if (entry === 'textline') {
    selectedRegionType.value = PolygonType.TEXTLINE
  } else {
    selectedRegionType.value = PolygonType.BASELINE
  }

  if (next === 'polygon') {
    currentCanvasState.value.togglePolygonMode?.()
  } else if (next === 'rectangle') {
    currentCanvasState.value.toggleRectangleMode?.()
  } else {
    currentCanvasState.value.togglePolylineMode?.()
  }
}

watchEffect(() => {
  if (!isDrawingMode.value) return

  if (isRegionTypeRegion.value && (isPolygonMode.value || isRectangleMode.value)) {
    preferredShapeByEntry.region = isRectangleMode.value ? 'rectangle' : 'polygon'
  }

  if (isRegionTypeTextline.value && (isPolygonMode.value || isRectangleMode.value)) {
    preferredShapeByEntry.textline = isRectangleMode.value ? 'rectangle' : 'polygon'
  }
})

const virtualKeyboardMode = computed(() => uiStore.virtualKeyboardMode)
const { hasKeyboards } = useVirtualKeyboardAvailability()
const isCompact = computed(() => uiStore.toolbarCompact)
const isTextVisualMode = computed(() =>
  isTextUiMode.value && uiStore.textModeSubmode === 'visual'
)
const forcedLayoutViewModeByCanvasId = ref<Record<string, LayoutViewMode>>({})

function selectModeView(option: ModeViewOption): void {
  if (option.mode === 'text') {
    textModeSubmodeModel.value = option.view
    editorModeModel.value = 'text'
    return
  }

  const canvasId = currentCanvasId.value
  if (canvasId && forcedLayoutViewModeByCanvasId.value[canvasId]) {
    const { [canvasId]: _discardedRestoreMode, ...nextMap } = forcedLayoutViewModeByCanvasId.value
    forcedLayoutViewModeByCanvasId.value = nextMap
  }

  editorModeModel.value = 'layout'
  selectedViewMode.value = option.view
}

watch(
  () => [currentCanvasId.value, isTextVisualMode.value, effectiveUiMode.value, selectedViewMode.value] as const,
  ([canvasId, visualTextMode, uiMode, currentView]) => {
    if (!canvasId) return
    const controls = currentCanvasState.value
    if (!controls) return

    if (visualTextMode) {
      if (!forcedLayoutViewModeByCanvasId.value[canvasId]) {
        forcedLayoutViewModeByCanvasId.value = {
          ...forcedLayoutViewModeByCanvasId.value,
          [canvasId]: currentView as LayoutViewMode
        }
      }
      if (currentView !== VIEW_MODES.TEXTLINE) {
        controls.setViewMode?.(VIEW_MODES.TEXTLINE, { persistAsLayoutPreference: false })
      }
      return
    }

    if (uiMode !== 'layout') return
    const restoreMode = forcedLayoutViewModeByCanvasId.value[canvasId]
    if (!restoreMode) return

    const { [canvasId]: _restoredMode, ...nextMap } = forcedLayoutViewModeByCanvasId.value
    forcedLayoutViewModeByCanvasId.value = nextMap

    const explicitlySelectedMode = uiStore.lastLayoutViewMode
    const targetMode = explicitlySelectedMode === currentView
      ? explicitlySelectedMode
      : restoreMode

    if (targetMode !== currentView) {
      controls.setViewMode?.(targetMode, { persistAsLayoutPreference: true })
    }
  },
  { immediate: true }
)

const showVirtualKeyboardControls = computed(() => !isCompact.value || hasKeyboards.value)
const showSelectAndMove = computed(() => !isCompact.value || !!currentCanvasState.value)
const showRegionTools = computed(() => !isCompact.value || (!!currentCanvasState.value && canCreateRegion.value))
const showTextlineTools = computed(() => !isCompact.value || (!!currentCanvasState.value && canCreateTextline.value))
const showBaselineTool = computed(() => !isCompact.value || (!!currentCanvasState.value && canCreateBaseline.value))
const showCutTools = computed(() => !isCompact.value || !!currentCanvasState.value)
const showMergeTool = computed(() => !isCompact.value || (!!currentCanvasState.value && canMerge.value))
const showActionTool = computed(() => !isCompact.value || !!currentCanvasState.value)
const hasUndoRedoRuntime = computed(() => Boolean(currentCanvasState.value || activeCommander.value))
const showUndoTool = computed(() => !isCompact.value || (hasUndoRedoRuntime.value && canUndo.value))
const showRedoTool = computed(() => !isCompact.value || (hasUndoRedoRuntime.value && canRedo.value))
const showHistoryTool = computed(() => !isCompact.value || hasUndoRedoRuntime.value)
const showMoreMenu = computed(() => !isCompact.value || !!currentCanvasState.value)

const vkModeIcon = computed(() => {
  switch (virtualKeyboardMode.value) {
    case 'floating': return 'i-lucide-app-window'
    default: return 'i-lucide-keyboard-off'
  }
})

const cycleVirtualKeyboardMode = () => {
  const modes: VirtualKeyboardMode[] = ['off', 'floating']
  const currentMode = virtualKeyboardMode.value ?? 'off'
  const currentIndex = modes.indexOf(currentMode)
  const nextMode: VirtualKeyboardMode = currentIndex >= 0
    ? (modes[(currentIndex + 1) % modes.length] ?? 'off')
    : 'off'
  uiStore.setVirtualKeyboardMode(nextMode)
}

const vkDropdownItems = computed(() => [
  [
    {
      label: 'Off',
      icon: 'i-lucide-keyboard-off',
      onSelect: () => uiStore.setVirtualKeyboardMode('off')
    },
    {
      label: 'Floating',
      icon: 'i-lucide-app-window',
      onSelect: () => uiStore.setVirtualKeyboardMode('floating')
    }
  ]
])

const regionDropdownItems = computed<DropdownMenuItem[][]>(() => [
  [
    {
      label: 'Rectangle',
      icon: 'i-lucide-square',
      kbds: getTooltipProps('regionRectangle').kbds,
      color: 'neutral',
      active: isRegionTypeRegion.value && isRectangleMode.value,
      class: activeDropdownItemClass(isRegionTypeRegion.value && isRectangleMode.value),
      ui: activeDropdownItemUi(isRegionTypeRegion.value && isRectangleMode.value),
      disabled: !currentCanvasState.value || !canCreateRegion.value,
      onSelect: () => setEntryAndMode('region', 'rectangle')
    },
    {
      label: 'Polygon',
      icon: 'i-lucide-pen-tool',
      kbds: getTooltipProps('regionPolygon').kbds,
      color: 'neutral',
      active: isRegionTypeRegion.value && isPolygonMode.value,
      class: activeDropdownItemClass(isRegionTypeRegion.value && isPolygonMode.value),
      ui: activeDropdownItemUi(isRegionTypeRegion.value && isPolygonMode.value),
      disabled: !currentCanvasState.value || !canCreateRegion.value,
      onSelect: () => setEntryAndMode('region', 'polygon')
    }
  ]
])

const textlineDropdownItems = computed(() => [
  [
    {
      label: 'Rectangle',
      icon: 'i-lucide-square',
      kbds: getTooltipProps('textlineRectangle').kbds,
      disabled: !currentCanvasState.value || !canCreateTextline.value,
      active: isRegionTypeTextline.value && isRectangleMode.value,
      class: activeDropdownItemClass(isRegionTypeTextline.value && isRectangleMode.value),
      ui: activeDropdownItemUi(isRegionTypeTextline.value && isRectangleMode.value),
      onSelect: () => setEntryAndMode('textline', 'rectangle')
    },
    {
      label: 'Polygon',
      icon: 'i-lucide-pen-tool',
      kbds: getTooltipProps('textlinePolygon').kbds,
      disabled: !currentCanvasState.value || !canCreateTextline.value,
      active: isRegionTypeTextline.value && isPolygonMode.value,
      class: activeDropdownItemClass(isRegionTypeTextline.value && isPolygonMode.value),
      ui: activeDropdownItemUi(isRegionTypeTextline.value && isPolygonMode.value),
      onSelect: () => setEntryAndMode('textline', 'polygon')
    }
  ]
])

const cutDropdownItems = computed(() => [
  [
    {
      label: 'Cut Line',
      icon: 'i-lucide-scissors',
      kbds: getTooltipProps('cutLine').kbds,
      disabled: !currentCanvasState.value || !canEditCurrentCanvas.value,
      active: isCutLineMode.value,
      class: activeDropdownItemClass(isCutLineMode.value),
      ui: activeDropdownItemUi(isCutLineMode.value),
      onSelect: () => handleToggleCutMode('line')
    },
    {
      label: 'Cut Rectangle',
      icon: 'i-carbon-cut-out',
      kbds: getTooltipProps('cutRectangle').kbds,
      disabled: !currentCanvasState.value || !canEditCurrentCanvas.value,
      active: isCutRectangleMode.value,
      class: activeDropdownItemClass(isCutRectangleMode.value),
      ui: activeDropdownItemUi(isCutRectangleMode.value),
      onSelect: () => handleToggleCutMode('rectangle')
    },
    {
      label: 'Cut Polygon',
      icon: 'i-ooui-cut-ltr',
      kbds: getTooltipProps('cutPolygon').kbds,
      disabled: !currentCanvasState.value || !canEditCurrentCanvas.value,
      active: isCutPolygonMode.value,
      class: activeDropdownItemClass(isCutPolygonMode.value),
      ui: activeDropdownItemUi(isCutPolygonMode.value),
      onSelect: () => handleToggleCutMode('polygon')
    }
  ]
])

type CutToolMode = 'line' | 'polygon' | 'rectangle'
type CutShortcutId = 'cutLine' | 'cutPolygon' | 'cutRectangle'

const cutToolConfig: Record<CutToolMode, { icon: string, shortcutId: CutShortcutId }> = {
  line: {
    icon: 'i-lucide-scissors',
    shortcutId: 'cutLine'
  },
  polygon: {
    icon: 'i-ooui-cut-ltr',
    shortcutId: 'cutPolygon'
  },
  rectangle: {
    icon: 'i-carbon-cut-out',
    shortcutId: 'cutRectangle'
  }
}

const preferredCutMode = ref<CutToolMode>('line')

const activeCutMode = computed<CutToolMode | null>(() => {
  if (isCutLineMode.value) return 'line'
  if (isCutPolygonMode.value) return 'polygon'
  if (isCutRectangleMode.value) return 'rectangle'
  return null
})

watchEffect(() => {
  if (activeCutMode.value) {
    preferredCutMode.value = activeCutMode.value
  }
})

const primaryCutMode = computed<CutToolMode>(() => activeCutMode.value ?? preferredCutMode.value)
const primaryCutToolIcon = computed(() => cutToolConfig[primaryCutMode.value].icon)
const primaryCutTooltip = computed(() => getTooltipProps(cutToolConfig[primaryCutMode.value].shortcutId))

function handleToggleCutMode(mode: CutToolMode) {
  if (!currentCanvasState.value) return
  cancelActionWand()

  preferredCutMode.value = mode

  if (mode === 'line') {
    currentCanvasState.value.toggleCutLineMode?.()
  } else if (mode === 'polygon') {
    currentCanvasState.value.toggleCutPolygonMode?.()
  } else {
    currentCanvasState.value.toggleCutRectangleMode?.()
  }
}

const moreOptionsDropdownItems = computed<DropdownMenuItem[][]>(() => [
  [
    {
      label: 'Compact toolbar',
      icon: 'i-lucide-minimize-2',
      type: 'checkbox',
      checked: isCompact.value,
      onUpdateChecked(checked: boolean) {
        uiStore.setToolbarCompact(checked)
      },
      onSelect(e: Event) {
        e.preventDefault()
      }
    }
  ],
  [
    {
      label: 'Shortcut Settings',
      icon: 'i-lucide-sliders-horizontal',
      onSelect: () => uiStore.openShortcutSettings()
    },
    {
      label: 'Keyboard Shortcuts',
      icon: 'i-lucide-circle-help',
      onSelect: () => uiStore.toggleShortcutsHelp()
    },
    {
      label: 'Lock View',
      icon: perPanelUiModeModel.value ? 'i-lucide-unlock' : 'i-lucide-lock',
      type: 'checkbox',
      checked: !perPanelUiModeModel.value,
      onUpdateChecked(checked: boolean) {
        perPanelUiModeModel.value = !checked
      },
      onSelect(e: Event) {
        e.preventDefault()
      }
    }
  ]
])
</script>

<template>
  <div
    data-tour="editor-toolbar"
    :class="[
      'z-50 print:hidden'
    ]"
    :style="floatingToolbarStyle"
  >
    <div
      ref="toolbarShellRef"
      :class="[
        'flex items-center justify-between dark:bg-neutral-900 bg-neutral-50 border-default',
        toolbarStyle,
        isVertical ? 'flex-col px-1 py-2 overflow-y-auto' : 'flex-row px-2 py-1 overflow-x-auto',
        isDraggingToolbar ? 'cursor-grabbing select-none' : ''
      ]"
    >
      <div class="flex items-center" :class="[(isVertical ? 'flex-col' : 'flex-row'), (isCompact ? 'gap-0' : 'gap-1')]">
        <template v-if="isFloating">
          <UTooltip :delay-duration="0" text="Drag toolbar">
            <UButton
              variant="ghost"
              size="sm"
              :icon="isVertical ? 'i-lucide-grip-horizontal' : 'i-lucide-grip-vertical'"
              color="neutral"
              aria-label="Drag toolbar"
              :class="[
                'touch-none shrink-0',
                isDraggingToolbar ? 'cursor-grabbing' : 'cursor-grab'
              ]"
              @pointerdown.prevent.stop="startToolbarDrag"
              @click.prevent.stop
            />
          </UTooltip>

          <USeparator
            :orientation="isVertical ? 'horizontal' : 'vertical'"
            class="h-6 mx-1"
          />
        </template>

        <template v-if="isTextUiMode">
          <template v-if="showVirtualKeyboardControls">
            <div class="flex items-center">
              <UFieldGroup>
                <UTooltip :delay-duration="0" v-bind="getTooltipProps('toggleVirtualKeyboard')">
                  <UButton
                    variant="ghost"
                    size="sm"
                    :icon="vkModeIcon"
                    :active="virtualKeyboardMode !== 'off'"
                    :aria-pressed="virtualKeyboardMode !== 'off'"
                    :aria-label="getTooltipProps('toggleVirtualKeyboard').text"
                    color="neutral"
                    :class="activeToolClass(virtualKeyboardMode !== 'off')"
                    :disabled="!hasKeyboards"
                    @click="cycleVirtualKeyboardMode"
                  />
                </UTooltip>
                <UDropdownMenu :items="vkDropdownItems" :popper="{ placement: 'top' }">
                  <UButton
                    variant="ghost"
                    size="sm"
                    icon="i-lucide-chevron-up"
                    color="neutral"
                    :active="virtualKeyboardMode !== 'off'"
                    :class="activeToolClass(virtualKeyboardMode !== 'off')"
                    :disabled="!hasKeyboards"
                    aria-label="Virtual keyboard mode"
                  />
                </UDropdownMenu>
              </UFieldGroup>
            </div>

            <USeparator
              :orientation="isVertical ? 'horizontal' : 'vertical'"
              class="h-6 mx-1"
            />
          </template>

          <div
            v-if="showUndoTool || showRedoTool || showHistoryTool"
            data-tour="undo-redo"
            class="flex items-center gap-1"
            :class="isVertical ? 'flex-col' : 'flex-row'"
          >
            <UTooltip v-if="showUndoTool" :delay-duration="0" v-bind="getTooltipProps('undo')">
              <UButton
                variant="ghost"
                icon="i-lucide-undo"
                color="neutral"
                size="sm"
                :aria-label="getTooltipProps('undo').text"
                :disabled="!canUndo"
                @click="handleUndo"
              />
            </UTooltip>
            <UTooltip v-if="showRedoTool" :delay-duration="0" v-bind="getTooltipProps('redo')">
              <UButton
                variant="ghost"
                icon="i-lucide-redo"
                color="neutral"
                size="sm"
                :aria-label="getTooltipProps('redo').text"
                :disabled="!canRedo"
                @click="handleRedo"
              />
            </UTooltip>

            <UDropdownMenu v-if="showHistoryTool" :items="historyDropdownItems">
              <UTooltip :delay-duration="0" v-bind="getTooltipProps('history')">
                <UButton
                  variant="ghost"
                  icon="i-lucide-history"
                  color="neutral"
                  size="sm"
                  :aria-label="getTooltipProps('history').text"
                  :disabled="!hasUndoRedoRuntime"
                  @click="updateHistoryItems"
                />
              </UTooltip>
            </UDropdownMenu>
          </div>

          <USeparator
            v-if="showUndoTool || showRedoTool || showHistoryTool"
            :orientation="isVertical ? 'horizontal' : 'vertical'"
            class="h-6 mx-1"
          />

          <UDropdownMenu :items="toolbarLayoutItems">
            <UButton
              :icon="toolbarLayoutIcon"
              color="neutral"
              size="sm"
              variant="ghost"
              aria-label="Toolbar layout"
            />
          </UDropdownMenu>

          <UDropdownMenu v-if="showMoreMenu" :items="moreOptionsDropdownItems">
            <UButton
              variant="ghost"
              icon="i-lucide-more-vertical"
              color="neutral"
              size="xs"
              aria-label="Toolbar settings"
            />
          </UDropdownMenu>
        </template>
        <template v-else>
          <template v-if="showSelectAndMove">
            <UTooltip :delay-duration="0" v-bind="getTooltipProps('selectMode')">
              <UButton
                variant="ghost"
                size="sm"
                icon="i-lucide-mouse-pointer-2"
                color="neutral"
                :active="isSelectMode && !uiStore.actionWandActive"
                :aria-pressed="isSelectMode && !uiStore.actionWandActive"
                :aria-label="getTooltipProps('selectMode').text"
                :class="activeToolClass(isSelectMode && !uiStore.actionWandActive)"
                :disabled="!currentCanvasState"
                @click="handleToggleSelectMode"
              />
            </UTooltip>

            <UTooltip :delay-duration="0" v-bind="getTooltipProps('moveMode')">
              <UButton
                variant="ghost"
                size="sm"
                icon="i-lucide-move"
                color="neutral"
                :active="isMoveMode"
                :aria-pressed="isMoveMode"
                :aria-label="getTooltipProps('moveMode').text"
                :class="activeToolClass(isMoveMode)"
                :disabled="!currentCanvasState || !canEditCurrentCanvas"
                @click="handleToggleMoveMode"
              />
            </UTooltip>
          </template>

          <USeparator
            v-if="showSelectAndMove && (showRegionTools || showTextlineTools || showBaselineTool || showCutTools || showMergeTool)"
            :orientation="isVertical ? 'horizontal' : 'vertical'"
            class="h-6 mx-1"
          />

          <template v-if="!isFloating">
            <div v-if="showRegionTools" data-tour="region-tools" class="contents">
              <UTooltip :delay-duration="0" v-bind="getTooltipProps('regionRectangle')">
                <UButton
                  variant="ghost"
                  size="sm"
                  icon="i-lucide-square"
                  color="neutral"
                  :active="isRegionTypeRegion && isRectangleMode"
                  :aria-pressed="isRegionTypeRegion && isRectangleMode"
                  :aria-label="getTooltipProps('regionRectangle').text"
                  :class="activeToolClass(isRegionTypeRegion && isRectangleMode)"
                  :disabled="!currentCanvasState || !canCreateRegion"
                  @click="setEntryAndMode('region', 'rectangle')"
                />
              </UTooltip>

              <UTooltip :delay-duration="0" v-bind="getTooltipProps('regionPolygon')">
                <UButton
                  variant="ghost"
                  size="sm"
                  icon="i-lucide-pen-tool"
                  color="neutral"
                  :active="isRegionTypeRegion && isPolygonMode"
                  :aria-pressed="isRegionTypeRegion && isPolygonMode"
                  :aria-label="getTooltipProps('regionPolygon').text"
                  :class="activeToolClass(isRegionTypeRegion && isPolygonMode)"
                  :disabled="!currentCanvasState || !canCreateRegion"
                  @click="setEntryAndMode('region', 'polygon')"
                />
              </UTooltip>
            </div>

            <USeparator
              v-if="showRegionTools && showTextlineTools"
              :orientation="isVertical ? 'horizontal' : 'vertical'"
              class="h-6 mx-1"
            />

            <div v-if="showTextlineTools" data-tour="textline-tools" class="contents">
              <UTooltip :delay-duration="0" v-bind="getTooltipProps('textlineRectangle')">
                <UButton
                  variant="ghost"
                  size="sm"
                  icon="i-lucide-square"
                  color="neutral"
                  :active="isRegionTypeTextline && isRectangleMode"
                  :aria-pressed="isRegionTypeTextline && isRectangleMode"
                  :aria-label="getTooltipProps('textlineRectangle').text"
                  :class="activeToolClass(isRegionTypeTextline && isRectangleMode)"
                  :disabled="!currentCanvasState || !canCreateTextline"
                  @click="setEntryAndMode('textline', 'rectangle')"
                />
              </UTooltip>

              <UTooltip :delay-duration="0" v-bind="getTooltipProps('textlinePolygon')">
                <UButton
                  variant="ghost"
                  size="sm"
                  icon="i-lucide-pen-tool"
                  color="neutral"
                  :active="isRegionTypeTextline && isPolygonMode"
                  :aria-pressed="isRegionTypeTextline && isPolygonMode"
                  :aria-label="getTooltipProps('textlinePolygon').text"
                  :class="activeToolClass(isRegionTypeTextline && isPolygonMode)"
                  :disabled="!currentCanvasState || !canCreateTextline"
                  @click="setEntryAndMode('textline', 'polygon')"
                />
              </UTooltip>
            </div>
          </template>

          <template v-else>
            <div v-if="showRegionTools" data-tour="region-tools" class="flex items-center">
              <UFieldGroup>
                <UTooltip :delay-duration="0" v-bind="getTooltipProps('regionPolygon')">
                  <UButton
                    variant="ghost"
                    size="md"
                    color="neutral"
                    :active="isRegionTypeRegion && (isPolygonMode || isRectangleMode)"
                    :aria-pressed="isRegionTypeRegion && (isPolygonMode || isRectangleMode)"
                    :aria-label="getEntryToolLabel('region')"
                    :class="activeToolClass(isRegionTypeRegion && (isPolygonMode || isRectangleMode))"
                    :disabled="!currentCanvasState || !canCreateRegion"
                    @click="setEntryAndMode('region', getPrimaryShapeForEntry('region'))"
                  >
                    <Icon :name="getIconForShape(getPrimaryShapeForEntry('region'))" class="h-4 w-4" />
                  </UButton>
                </UTooltip>

                <UDropdownMenu :items="regionDropdownItems" :popper="{ placement: 'top' }">
                  <UButton
                    variant="ghost"
                    size="sm"
                    icon="i-lucide-chevron-up"
                    color="neutral"
                    :active="isRegionTypeRegion && (isPolygonMode || isRectangleMode)"
                    :class="activeToolClass(isRegionTypeRegion && (isPolygonMode || isRectangleMode))"
                    :disabled="!currentCanvasState || !canCreateRegion"
                    aria-label="Region tools"
                  />
                </UDropdownMenu>
              </UFieldGroup>
            </div>

            <USeparator
              v-if="showRegionTools && showTextlineTools"
              :orientation="isVertical ? 'horizontal' : 'vertical'"
              class="h-6 mx-1"
            />

            <div v-if="showTextlineTools" data-tour="textline-tools" class="flex items-center">
              <UFieldGroup>
                <UTooltip :delay-duration="0" v-bind="getTooltipProps('textlinePolygon')">
                  <UButton
                    variant="ghost"
                    size="md"
                    color="neutral"
                    :active="isRegionTypeTextline && (isPolygonMode || isRectangleMode)"
                    :aria-pressed="isRegionTypeTextline && (isPolygonMode || isRectangleMode)"
                    :aria-label="getEntryToolLabel('textline')"
                    :class="activeToolClass(isRegionTypeTextline && (isPolygonMode || isRectangleMode))"
                    :disabled="!currentCanvasState || !canCreateTextline"
                    @click="setEntryAndMode('textline', getPrimaryShapeForEntry('textline'))"
                  >
                    <Icon :name="getIconForShape(getPrimaryShapeForEntry('textline'))" class="h-4 w-4" />
                  </UButton>
                </UTooltip>

                <UDropdownMenu
                  :items="textlineDropdownItems"
                  :popper="{ placement: 'top' }"
                >
                  <UButton
                    variant="ghost"
                    size="sm"
                    icon="i-lucide-chevron-up"
                    color="neutral"
                    :active="isRegionTypeTextline && (isPolygonMode || isRectangleMode)"
                    :class="activeToolClass(isRegionTypeTextline && (isPolygonMode || isRectangleMode))"
                    :disabled="!currentCanvasState || !canCreateTextline"
                    aria-label="Textline tools"
                  />
                </UDropdownMenu>
              </UFieldGroup>
            </div>
          </template>

          <USeparator
            v-if="showBaselineTool && (showRegionTools || showTextlineTools)"
            :orientation="isVertical ? 'horizontal' : 'vertical'"
            class="h-6 mx-1"
          />

          <UTooltip v-if="showBaselineTool" :delay-duration="0" v-bind="canCreateBaseline ? getTooltipProps('baseline') : { text: 'Select a TextLine or switch to Baseline view' }">
            <UButton
              variant="ghost"
              size="sm"
              icon="i-lucide-activity"
              color="neutral"
              :active="isRegionTypeBaseline && isPolylineMode"
              :aria-pressed="isRegionTypeBaseline && isPolylineMode"
              :aria-label="getTooltipProps('baseline').text"
              :class="activeToolClass(isRegionTypeBaseline && isPolylineMode)"
              :disabled="!currentCanvasState || !canCreateBaseline"
              @click="setEntryAndMode('baseline', 'polyline')"
            />
          </UTooltip>

          <USeparator
            v-if="(showRegionTools || showTextlineTools || showBaselineTool) && (showCutTools || showMergeTool)"
            :orientation="isVertical ? 'horizontal' : 'vertical'"
            class="h-6 mx-1"
          />

          <div v-if="showCutTools && !isFloating" data-tour="cut-tools" class="contents">
            <UTooltip :delay-duration="0" v-bind="getTooltipProps('cutLine')">
              <UButton
                variant="ghost"
                size="sm"
                icon="i-lucide-scissors"
                color="neutral"
                :active="isCutLineMode"
                :aria-pressed="isCutLineMode"
                :aria-label="getTooltipProps('cutLine').text"
                :class="activeToolClass(isCutLineMode)"
                :disabled="!currentCanvasState || !canEditCurrentCanvas"
                @click="handleToggleCutMode('line')"
              />
            </UTooltip>

            <UTooltip :delay-duration="0" v-bind="getTooltipProps('cutRectangle')">
              <UButton
                variant="ghost"
                size="sm"
                icon="i-lucide-square-minus"
                color="neutral"
                :active="isCutRectangleMode"
                :aria-pressed="isCutRectangleMode"
                :aria-label="getTooltipProps('cutRectangle').text"
                :class="activeToolClass(isCutRectangleMode)"
                :disabled="!currentCanvasState || !canEditCurrentCanvas"
                @click="handleToggleCutMode('rectangle')"
              />
            </UTooltip>

            <UTooltip :delay-duration="0" v-bind="getTooltipProps('cutPolygon')">
              <UButton
                variant="ghost"
                size="sm"
                icon="i-lucide-pen-tool"
                color="neutral"
                :active="isCutPolygonMode"
                :aria-pressed="isCutPolygonMode"
                :aria-label="getTooltipProps('cutPolygon').text"
                :class="activeToolClass(isCutPolygonMode)"
                :disabled="!currentCanvasState || !canEditCurrentCanvas"
                @click="handleToggleCutMode('polygon')"
              />
            </UTooltip>
          </div>

          <div v-if="showCutTools && isFloating" data-tour="cut-tools" class="flex items-center">
            <UFieldGroup>
              <UTooltip :delay-duration="0" v-bind="primaryCutTooltip">
                <UButton
                  variant="ghost"
                  size="md"
                  color="neutral"
                  :active="isCutMode"
                  :aria-pressed="isCutMode"
                  :aria-label="primaryCutTooltip.text"
                  :class="activeToolClass(isCutMode)"
                  :disabled="!currentCanvasState || !canEditCurrentCanvas"
                  @click="handleToggleCutMode(preferredCutMode)"
                >
                  <Icon :name="primaryCutToolIcon" class="h-4 w-4" />
                </UButton>
              </UTooltip>

              <UDropdownMenu :items="cutDropdownItems" :popper="{ placement: 'top' }">
                <UButton
                  variant="ghost"
                  size="sm"
                  icon="i-lucide-chevron-up"
                  color="neutral"
                  :active="isCutMode"
                  :class="activeToolClass(isCutMode)"
                  :disabled="!currentCanvasState || !canEditCurrentCanvas"
                  aria-label="Cut tools"
                />
              </UDropdownMenu>
            </UFieldGroup>
          </div>

          <UTooltip v-if="showMergeTool" :delay-duration="0" v-bind="canMerge ? getTooltipProps('merge') : { text: 'Select 2+ elements of the same type to merge' }">
            <UButton
              variant="ghost"
              size="sm"
              icon="i-lucide-merge"
              color="neutral"
              :aria-label="canMerge ? getTooltipProps('merge').text : 'Merge selected elements'"
              :disabled="!currentCanvasState || !canMerge"
              @click="handleMerge"
            />
          </UTooltip>

          <UTooltip
            v-if="showActionTool"
            :delay-duration="0"
            :text="uiStore.actionWandActive ? 'Cancel Action target picker' : 'Pick a page, region, or textline for an Action'"
          >
            <UButton
              variant="ghost"
              size="sm"
              icon="i-lucide-wand-sparkles"
              color="neutral"
              :active="uiStore.actionWandActive"
              :aria-pressed="uiStore.actionWandActive"
              :aria-label="uiStore.actionWandActive ? 'Cancel Action target picker' : 'Pick an Action target'"
              :class="activeToolClass(uiStore.actionWandActive)"
              :disabled="!currentCanvasState || !canEditCurrentCanvas"
              @click="handleToggleActionWand"
            />
          </UTooltip>

          <USeparator
            v-if="showSelectAndMove || showRegionTools || showTextlineTools || showBaselineTool || showCutTools || showMergeTool || showActionTool"
            :orientation="isVertical ? 'horizontal' : 'vertical'"
            class="h-6 mx-1"
          />

          <div
            v-if="showUndoTool || showRedoTool || showHistoryTool"
            data-tour="undo-redo"
            class="flex items-center gap-1"
            :class="isVertical ? 'flex-col' : 'flex-row'"
          >
            <UTooltip v-if="showUndoTool" :delay-duration="0" v-bind="getTooltipProps('undo')">
              <UButton
                variant="ghost"
                icon="i-lucide-undo"
                color="neutral"
                size="sm"
                :aria-label="getTooltipProps('undo').text"
                :disabled="!canUndo || !currentCanvasState"
                @click="handleUndo"
              />
            </UTooltip>
            <UTooltip v-if="showRedoTool" :delay-duration="0" v-bind="getTooltipProps('redo')">
              <UButton
                variant="ghost"
                icon="i-lucide-redo"
                color="neutral"
                size="sm"
                :aria-label="getTooltipProps('redo').text"
                :disabled="!canRedo || !currentCanvasState"
                @click="handleRedo"
              />
            </UTooltip>

            <UDropdownMenu v-if="showHistoryTool" :items="historyDropdownItems">
              <UTooltip :delay-duration="0" v-bind="getTooltipProps('history')">
                <UButton
                  variant="ghost"
                  icon="i-lucide-history"
                  color="neutral"
                  size="sm"
                  :aria-label="getTooltipProps('history').text"
                  :disabled="!currentCanvasState"
                  @click="updateHistoryItems"
                />
              </UTooltip>
            </UDropdownMenu>
          </div>

          <USeparator :orientation="isVertical ? 'horizontal' : 'vertical'" class="h-6 mx-1" />

          <UDropdownMenu :items="toolbarLayoutItems">
            <UButton
              :icon="toolbarLayoutIcon"
              color="neutral"
              size="sm"
              variant="ghost"
              aria-label="Toolbar layout"
            />
          </UDropdownMenu>

          <UDropdownMenu v-if="showMoreMenu" :items="moreOptionsDropdownItems">
            <UButton
              variant="ghost"
              icon="i-lucide-more-vertical"
              color="neutral"
              size="xs"
              aria-label="Toolbar settings"
            />
          </UDropdownMenu>
        </template>
      </div>
      <div
        data-tour="editor-mode-tabs"
        class="flex items-center"
      >
        <USelectMenu
          v-model="modeViewModel"
          data-tour="context-view-selector"
          :items="modeViewMenuItems"
          value-key="value"
          label-key="label"
          :search-input="false"
          size="sm"
          color="primary"
          variant="soft"
          :aria-label="modeViewAriaLabel"
          :title="(isCompact || isVertical) ? modeViewAriaLabel : undefined"
          :content="{ side: modeViewMenuSide, align: 'end' }"
          :class="(isCompact || isVertical) ? 'w-12' : 'min-w-44'"
          :ui="{
            base: 'justify-between',
            content: 'w-88 max-w-[calc(100vw-1rem)] max-h-[min(26rem,var(--reka-combobox-content-available-height,26rem))]',
            itemDescription: 'whitespace-normal',
            itemTrailingIcon: 'text-primary'
          }"
        >
          <template #default>
            <span class="flex min-w-0 flex-1 items-center gap-1.5">
              <Icon :name="activeModeViewOption.icon" class="size-4 shrink-0" />
              <span v-if="!isCompact && !isVertical" class="truncate">
                <span class="font-medium">{{ activeModeViewOption.modeLabel }}</span>
                <span class="px-1 text-muted" aria-hidden="true">·</span>
                <span>{{ activeModeViewOption.label }}</span>
              </span>
              <span v-else class="sr-only">{{ modeViewLabel }}</span>
              <Icon name="i-lucide-chevron-down" class="ms-auto size-3 shrink-0 text-muted" />
            </span>
          </template>

          <template #item-trailing="{ item }">
            <div v-if="'kbds' in item && item.kbds?.length" class="flex items-center gap-1">
              <UKbd v-for="kbd in item.kbds" :key="kbd" size="sm">
                {{ kbd }}
              </UKbd>
            </div>
          </template>
        </USelectMenu>
      </div>
    </div>
  </div>
</template>
