import type { ComputedRef, Ref } from 'vue'
import type { Commander } from '@/commands'
import { DeleteSelectedElementsCommand } from '@/commands/editor/delete-selected-elements-command'
import { DRAWING_MODES, type DrawingMode, VIEW_MODES, type ViewMode } from '@/composables/editor/use-canvas-control'
import {
  SHORTCUT_DEFINITIONS,
  SHORTCUT_HELP_GROUPS,
  getEffectiveShortcutBindings,
  serializeKeyboardEventToBinding,
  type ShortcutCommandId,
  type ShortcutDefinition,
  type ShortcutScope
} from '@/composables/editor/shortcut-registry'
import { getTooltipProps } from '@/composables/editor/use-shortcut-bindings'
import { getEditorSession } from '@/session/editor/editor-session'
import type { EditorCanvasControls } from '@/types/editor/canvas-controls'
import type { TextModeSubmode } from '@/stores/editor/types'

export { SHORTCUT_DEFINITIONS, SHORTCUT_HELP_GROUPS, getTooltipProps }
export type {
  ShortcutCommandId as ShortcutKey,
  ShortcutDefinition,
  ShortcutHelpGroupId
} from '@/composables/editor/shortcut-registry'

type TextViewShortcutHandlers = Pick<Record<ShortcutCommandId, () => boolean>, 'nextTextField' | 'nextTextlineGtField' | 'prevTextlineGtField' | 'prevTextField' | 'blurTextField' | 'nextSameIndexField' | 'createGtFromRecognition'>

interface RegisteredTextViewShortcutScope {
  rootEl: Ref<HTMLElement | null>
  handlers: TextViewShortcutHandlers
}

const textViewScopeRegistry = new Map<string, RegisteredTextViewShortcutScope>()

export interface KeyboardShortcutsOptions {
  canvasId: Ref<string | null>
  isDrawingMode: Ref<boolean>
  drawingMode: Ref<DrawingMode>
  viewMode: Ref<ViewMode>
  selectedPolygonIds: Ref<string[]>
  selectedPolylineIds: Ref<string[]>
  polygons: Ref<{ id: string }[]> | ComputedRef<{ id: string }[]>
  polylines: Ref<{ id: string }[]> | ComputedRef<{ id: string }[]>
  selectedPolygonIndex: Ref<number>
  selectedPolylineIndex: Ref<number>
  callbacks: {
    handleUndo: () => void
    handleRedo: () => void
    setDrawingMode: (mode: DrawingMode) => void
    setViewMode: (mode: ViewMode) => void
    selectPolygonByIndex: (index: number) => void
    selectPolylineByIndex: (index: number) => void
    clearSelection: () => void
    selectAll: () => void
    zoomIn: () => void
    zoomOut: () => void
    fitToContent: () => void
    resetView: () => void
    centerOnSelection: () => void
    addHoveredToReadingOrder?: () => boolean
    toggleLeftSidebar?: () => void
    toggleRightSidebar?: () => void
    deleteSelected?: () => void
    toggleShortcutsHelp?: () => void
    mergeSelected?: () => void
    setRegionType?: (type: 'region' | 'textline' | 'baseline') => void
    setCutMode?: (mode: 'line' | 'polygon' | 'rectangle') => void
    setUiMode?: (mode: 'layout' | 'text') => void
    setLayoutViewMode?: (mode: ViewMode) => void
    setTextViewMode?: (mode: TextModeSubmode) => void
    toggleVirtualKeyboard?: () => void
    saveDocument?: () => void
    nextImage?: () => void
    prevImage?: () => void
    closeActiveTab?: () => void
    closeActiveTabAndNextPage?: () => void
    closeActiveTabAndPrevPage?: () => void
  }
}

export function useTextViewShortcutScope(options: {
  canvasId: Ref<string | null | undefined> | ComputedRef<string | null | undefined>
  rootEl: Ref<HTMLElement | null>
  handlers: TextViewShortcutHandlers
}) {
  const previousCanvasId = ref<string | null>(null)

  watchEffect(() => {
    const canvasId = options.canvasId.value ?? null
    const previous = previousCanvasId.value

    if (previous && previous !== canvasId) {
      textViewScopeRegistry.delete(previous)
    }

    if (canvasId) {
      textViewScopeRegistry.set(canvasId, {
        rootEl: options.rootEl,
        handlers: options.handlers
      })
    }

    previousCanvasId.value = canvasId
  })

  onBeforeUnmount(() => {
    const canvasId = previousCanvasId.value
    if (canvasId) textViewScopeRegistry.delete(canvasId)
  })
}

function isTypingTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false
  const tagName = target.tagName.toLowerCase()
  return (
    tagName === 'input'
    || tagName === 'textarea'
    || tagName === 'select'
    || target.isContentEditable
  )
}

function resolveActiveScope(
  event: KeyboardEvent,
  canvasId: string | null
): { scope: ShortcutScope, registration?: RegisteredTextViewShortcutScope } {
  if (!canvasId) return { scope: 'global' }

  const registration = textViewScopeRegistry.get(canvasId)
  if (!registration) return { scope: 'global' }

  const rootEl = registration.rootEl.value
  if (!rootEl) return { scope: 'global' }

  const target = event.target
  const isInsideTextView = target instanceof Node && rootEl.contains(target)
  const isBodyTarget = target === document.body

  if (isInsideTextView || isBodyTarget) {
    return { scope: 'text-view', registration }
  }

  return { scope: 'global' }
}

export function useKeyboardShortcuts(options: KeyboardShortcutsOptions) {
  const {
    canvasId,
    selectedPolygonIds,
    selectedPolylineIds,
    polygons,
    polylines,
    selectedPolygonIndex,
    selectedPolylineIndex,
    callbacks
  } = options

  const editorPreferences = useEditorPreferences()

  const resolvedBindings = computed(() =>
    getEffectiveShortcutBindings(editorPreferences.preferences.value.shortcutBindings)
  )

  const isInputFocused = () => isTypingTarget(document.activeElement)

  const getCommandContext = () => {
    const id = canvasId.value
    if (!id) return undefined
    const session = getEditorSession(id)
    return session ? { canvasId: id, session } : undefined
  }

  const getCommander = (): Commander | undefined => {
    const id = canvasId.value
    if (!id) return undefined
    const session = getEditorSession(id)
    const controls: EditorCanvasControls | null | undefined = session?.controls.value
    return controls?.commander
  }

  const deleteSelectedElements = (): boolean => {
    if (isInputFocused()) return false

    const ctx = getCommandContext()
    const commander = getCommander()
    if (!ctx || !commander) return false

    const polygonIds = selectedPolygonIds.value
    const polylineIds = selectedPolylineIds.value
    if (polygonIds.length === 0 && polylineIds.length === 0) return false

    if (callbacks.deleteSelected) {
      callbacks.deleteSelected()
    } else {
      const command = new DeleteSelectedElementsCommand({
        polygonIds,
        polylineIds
      })
      commander.execute(command, ctx)
    }

    return true
  }

  const navigateElements = (direction: 'next' | 'prev'): boolean => {
    if (isInputFocused()) return false

    const isPolygonSelected = selectedPolygonIndex.value >= 0
    const isPolylineSelected = selectedPolylineIndex.value >= 0

    if (isPolygonSelected) {
      const currentIndex = selectedPolygonIndex.value
      const nextIndex = direction === 'next'
        ? Math.min(currentIndex + 1, polygons.value.length - 1)
        : Math.max(currentIndex - 1, 0)

      if (nextIndex !== currentIndex) {
        callbacks.selectPolygonByIndex(nextIndex)
        return true
      }
      return false
    }

    if (isPolylineSelected) {
      const currentIndex = selectedPolylineIndex.value
      const nextIndex = direction === 'next'
        ? Math.min(currentIndex + 1, polylines.value.length - 1)
        : Math.max(currentIndex - 1, 0)

      if (nextIndex !== currentIndex) {
        callbacks.selectPolylineByIndex(nextIndex)
        return true
      }
      return false
    }

    if (polygons.value.length > 0) {
      callbacks.selectPolygonByIndex(direction === 'next' ? 0 : polygons.value.length - 1)
      return true
    }

    return false
  }

  const runGlobalCommand = (
    commandId: ShortcutCommandId,
    options?: { allowWhileTyping?: boolean }
  ): boolean => {
    const allowWhileTyping = options?.allowWhileTyping === true

    switch (commandId) {
      case 'undo':
        if (isInputFocused() && !allowWhileTyping) return false
        callbacks.handleUndo()
        return true
      case 'redo':
        if (isInputFocused() && !allowWhileTyping) return false
        callbacks.handleRedo()
        return true
      case 'selectMode':
        if (isInputFocused()) return false
        callbacks.setDrawingMode(DRAWING_MODES.SELECT)
        return true
      case 'moveMode':
        if (isInputFocused()) return false
        callbacks.setDrawingMode(DRAWING_MODES.MOVE)
        return true
      case 'regionPolygon':
        if (isInputFocused()) return false
        callbacks.setRegionType?.('region')
        callbacks.setDrawingMode(DRAWING_MODES.POLYGON)
        return true
      case 'regionRectangle':
        if (isInputFocused()) return false
        callbacks.setRegionType?.('region')
        callbacks.setDrawingMode(DRAWING_MODES.RECTANGLE)
        return true
      case 'textlinePolygon':
        if (isInputFocused()) return false
        callbacks.setRegionType?.('textline')
        callbacks.setDrawingMode(DRAWING_MODES.POLYGON)
        return true
      case 'textlineRectangle':
        if (isInputFocused()) return false
        callbacks.setRegionType?.('textline')
        callbacks.setDrawingMode(DRAWING_MODES.RECTANGLE)
        return true
      case 'baseline':
        if (isInputFocused()) return false
        callbacks.setRegionType?.('baseline')
        callbacks.setDrawingMode(DRAWING_MODES.POLYLINE)
        return true
      case 'cutLine':
        if (isInputFocused()) return false
        callbacks.setCutMode?.('line')
        return true
      case 'cutPolygon':
        if (isInputFocused()) return false
        callbacks.setCutMode?.('polygon')
        return true
      case 'cutRectangle':
        if (isInputFocused()) return false
        callbacks.setCutMode?.('rectangle')
        return true
      case 'defaultView':
        if (isInputFocused()) return false
        if (callbacks.setLayoutViewMode) callbacks.setLayoutViewMode(VIEW_MODES.DEFAULT)
        else callbacks.setViewMode(VIEW_MODES.DEFAULT)
        return true
      case 'textlineView':
        if (isInputFocused()) return false
        if (callbacks.setLayoutViewMode) callbacks.setLayoutViewMode(VIEW_MODES.TEXTLINE)
        else callbacks.setViewMode(VIEW_MODES.TEXTLINE)
        return true
      case 'baselineView':
        if (isInputFocused()) return false
        if (callbacks.setLayoutViewMode) callbacks.setLayoutViewMode(VIEW_MODES.BASELINE)
        else callbacks.setViewMode(VIEW_MODES.BASELINE)
        return true
      case 'textCanvasView':
        if (isInputFocused()) return false
        callbacks.setTextViewMode?.('visual')
        return true
      case 'textListView':
        if (isInputFocused()) return false
        callbacks.setTextViewMode?.('expert')
        return true
      case 'clearSelection':
        if (isInputFocused()) return false
        callbacks.clearSelection()
        return true
      case 'selectAll':
        if (isInputFocused()) return false
        callbacks.selectAll()
        return true
      case 'delete':
        return deleteSelectedElements()
      case 'merge':
        if (isInputFocused()) return false
        callbacks.mergeSelected?.()
        return true
      case 'nextElement':
        return navigateElements('next')
      case 'prevElement':
        return navigateElements('prev')
      case 'zoomIn':
        if (isInputFocused()) return false
        callbacks.zoomIn()
        return true
      case 'zoomOut':
        if (isInputFocused()) return false
        callbacks.zoomOut()
        return true
      case 'fitToContent':
        if (isInputFocused()) return false
        callbacks.fitToContent()
        return true
      case 'resetView':
        if (isInputFocused()) return false
        callbacks.resetView()
        return true
      case 'centerOnSelection':
        if (isInputFocused()) return false
        callbacks.centerOnSelection()
        return true
      case 'addHoveredToReadingOrder':
        if (isInputFocused()) return false
        return callbacks.addHoveredToReadingOrder?.() ?? false
      case 'toggleLeftSidebar':
        if (isInputFocused()) return false
        callbacks.toggleLeftSidebar?.()
        return true
      case 'toggleRightSidebar':
        if (isInputFocused()) return false
        callbacks.toggleRightSidebar?.()
        return true
      case 'layoutMode':
        if (isInputFocused()) return false
        callbacks.setUiMode?.('layout')
        return true
      case 'textMode':
        if (isInputFocused()) return false
        callbacks.setUiMode?.('text')
        return true
      case 'toggleVirtualKeyboard':
        if (isInputFocused()) return false
        callbacks.toggleVirtualKeyboard?.()
        return true
      case 'save':
        if (isInputFocused()) return false
        callbacks.saveDocument?.()
        return true
      case 'nextImage':
        if (isInputFocused()) return false
        callbacks.nextImage?.()
        return true
      case 'prevImage':
        if (isInputFocused()) return false
        callbacks.prevImage?.()
        return true
      case 'closeActiveTab':
        if (isInputFocused()) return false
        callbacks.closeActiveTab?.()
        return true
      case 'closeActiveTabAndNextPage':
        if (isInputFocused()) return false
        callbacks.closeActiveTabAndNextPage?.()
        return true
      case 'closeActiveTabAndPrevPage':
        if (isInputFocused()) return false
        callbacks.closeActiveTabAndPrevPage?.()
        return true
      case 'showHelp':
        if (isInputFocused()) return false
        callbacks.toggleShortcutsHelp?.()
        return true
      case 'history':
      case 'nextTextField':
      case 'nextTextlineGtField':
      case 'prevTextlineGtField':
      case 'prevTextField':
      case 'blurTextField':
      case 'nextSameIndexField':
      case 'createGtFromRecognition':
        return false
      default:
        return false
    }
  }

  const runScopedCommand = (
    commandId: ShortcutCommandId,
    registration: RegisteredTextViewShortcutScope | undefined
  ): boolean => {
    if (!registration) return false

    switch (commandId) {
      case 'nextTextField':
      case 'nextTextlineGtField':
      case 'prevTextlineGtField':
      case 'prevTextField':
      case 'blurTextField':
      case 'nextSameIndexField':
      case 'createGtFromRecognition':
        return registration.handlers[commandId]()
      default:
        return false
    }
  }

  const runCommand = (
    commandId: ShortcutCommandId,
    registration: RegisteredTextViewShortcutScope | undefined,
    options?: { isTextViewContext?: boolean }
  ): boolean => {
    const definition = SHORTCUT_DEFINITIONS[commandId] as ShortcutDefinition
    if (definition.scope === 'text-view') {
      return runScopedCommand(commandId, registration)
    }

    return runGlobalCommand(commandId, { allowWhileTyping: options?.isTextViewContext })
  }

  const onKeydown = (event: KeyboardEvent) => {
    const binding = serializeKeyboardEventToBinding(event)
    if (!binding) return

    const scopeState = resolveActiveScope(event, canvasId.value)
    const isTextViewContext = scopeState.scope === 'text-view'
    const candidateScopes: ShortcutScope[] = scopeState.scope === 'text-view'
      ? ['text-view', 'global']
      : ['global']

    for (const scope of candidateScopes) {
      for (const [commandId, definition] of Object.entries(SHORTCUT_DEFINITIONS) as Array<[ShortcutCommandId, ShortcutDefinition]>) {
        if (definition.scope !== scope) continue
        if (!(resolvedBindings.value[commandId] ?? []).includes(binding)) continue
        if (commandId === 'clearSelection' && binding === 'escape') continue

        const handled = runCommand(commandId, scopeState.registration, { isTextViewContext })
        if (!handled) continue

        event.preventDefault()
        event.stopImmediatePropagation()
        event.stopPropagation()
        return
      }
    }
  }

  if (import.meta.client) {
    onMounted(() => window.addEventListener('keydown', onKeydown, true))
    onUnmounted(() => window.removeEventListener('keydown', onKeydown, true))
  }

  return {
    deleteSelectedElements,
    navigateElements,
    isInputFocused
  }
}
