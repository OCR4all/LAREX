/**
 * Composable for defining keyboard shortcuts throughout the editor.
 * Uses Nuxt UI's defineShortcuts for cross-platform modifier key handling.
 */
import type { Commander } from '@/commands'
import { getEditorSession } from '@/session/editor/editor-session'
import { DeleteSelectedElementsCommand } from '@/commands/editor/delete-selected-elements-command'
import { DRAWING_MODES, type DrawingMode, VIEW_MODES, type ViewMode } from '@/composables/editor/use-canvas-control'

export interface KeyboardShortcutsOptions {
  /** The active canvas ID */
  canvasId: Ref<string | null>

  /** Whether the editor is in drawing mode */
  isDrawingMode: Ref<boolean>

  /** Current drawing mode */
  drawingMode: Ref<DrawingMode>

  /** Current view mode */
  viewMode: Ref<ViewMode>

  /** Selected polygon IDs */
  selectedPolygonIds: Ref<string[]>

  /** Selected polyline IDs */
  selectedPolylineIds: Ref<string[]>

  /** Polygon array for navigation */
  polygons: { id: string }[]

  /** Polyline array for navigation */
  polylines: { id: string }[]

  /** Current selected polygon index */
  selectedPolygonIndex: Ref<number>

  /** Current selected polyline index */
  selectedPolylineIndex: Ref<number>

  /** Callbacks */
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
    centerOnSelection: () => void
    toggleLeftSidebar?: () => void
    toggleRightSidebar?: () => void
    deleteSelected?: () => void
    toggleShortcutsHelp?: () => void
    mergeSelected?: () => void
    /** Set region type for drawing */
    setRegionType?: (type: 'region' | 'textline' | 'baseline') => void
    /** Set cut mode */
    setCutMode?: (mode: 'line' | 'polygon' | 'rectangle') => void
    /** Toggle editor UI mode */
    setUiMode?: (mode: 'layout' | 'text') => void
    /** Toggle virtual keyboard */
    toggleVirtualKeyboard?: () => void
    /** Save document */
    saveDocument?: () => void
    /** Navigate to next image */
    nextImage?: () => void
    /** Navigate to previous image */
    prevImage?: () => void
    /** Close the active dockview tab */
    closeActiveTab?: () => void
  }
}

/**
 * Define global keyboard shortcuts for the editor.
 * This composable should be called from the main editor page/layout.
 */
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

  const isInputFocused = () => {
    const active = document.activeElement
    if (!active) return false
    const tagName = active.tagName.toLowerCase()
    return (
      tagName === 'input'
      || tagName === 'textarea'
      || tagName === 'select'
      || (active as HTMLElement).isContentEditable
    )
  }

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
    return (session?.controls.value as any)?.commander
  }

  const deleteSelectedElements = () => {
    if (isInputFocused()) return

    const ctx = getCommandContext()
    const commander = getCommander()
    if (!ctx || !commander) return

    const polygonIds = selectedPolygonIds.value
    const polylineIds = selectedPolylineIds.value

    if (polygonIds.length === 0 && polylineIds.length === 0) return

    if (callbacks.deleteSelected) {
      callbacks.deleteSelected()
    } else {
      const command = new DeleteSelectedElementsCommand({
        polygonIds,
        polylineIds
      })
      commander.execute(command, ctx)
    }
  }

  const navigateElements = (direction: 'next' | 'prev') => {
    if (isInputFocused()) return

    const isPolygonSelected = selectedPolygonIndex.value >= 0
    const isPolylineSelected = selectedPolylineIndex.value >= 0

    if (isPolygonSelected) {
      const currentIndex = selectedPolygonIndex.value
      const newIndex = direction === 'next'
        ? Math.min(currentIndex + 1, polygons.length - 1)
        : Math.max(currentIndex - 1, 0)

      if (newIndex !== currentIndex) {
        callbacks.selectPolygonByIndex(newIndex)
      }
    } else if (isPolylineSelected) {
      const currentIndex = selectedPolylineIndex.value
      const newIndex = direction === 'next'
        ? Math.min(currentIndex + 1, polylines.length - 1)
        : Math.max(currentIndex - 1, 0)

      if (newIndex !== currentIndex) {
        callbacks.selectPolylineByIndex(newIndex)
      }
    } else if (polygons.length > 0) {
      callbacks.selectPolygonByIndex(direction === 'next' ? 0 : polygons.length - 1)
    }
  }

  defineShortcuts({
    'meta_z': {
      handler: () => {
        if (isInputFocused()) return
        callbacks.handleUndo()
      }
    },
    'meta_shift_z': {
      handler: () => {
        if (isInputFocused()) return
        callbacks.handleRedo()
      }
    },
    'meta_y': {
      handler: () => {
        if (isInputFocused()) return
        callbacks.handleRedo()
      }
    },

    'v': {
      handler: () => {
        if (isInputFocused()) return
        callbacks.setDrawingMode(DRAWING_MODES.SELECT)
      }
    },
    'g': {
      handler: () => {
        if (isInputFocused()) return
        callbacks.setDrawingMode(DRAWING_MODES.MOVE)
      }
    },

    'p': {
      handler: () => {
        if (isInputFocused()) return
        callbacks.setRegionType?.('region')
        callbacks.setDrawingMode(DRAWING_MODES.POLYGON)
      }
    },
    'r': {
      handler: () => {
        if (isInputFocused()) return
        callbacks.setRegionType?.('region')
        callbacks.setDrawingMode(DRAWING_MODES.RECTANGLE)
      }
    },

    't': {
      handler: () => {
        if (isInputFocused()) return
        callbacks.setRegionType?.('textline')
        callbacks.setDrawingMode(DRAWING_MODES.POLYGON)
      }
    },
    'shift_t': {
      handler: () => {
        if (isInputFocused()) return
        callbacks.setRegionType?.('textline')
        callbacks.setDrawingMode(DRAWING_MODES.RECTANGLE)
      }
    },

    'b': {
      handler: () => {
        if (isInputFocused()) return
        callbacks.setRegionType?.('baseline')
        callbacks.setDrawingMode(DRAWING_MODES.POLYLINE)
      }
    },

    'c': {
      handler: () => {
        if (isInputFocused()) return
        callbacks.setCutMode?.('line')
      }
    },
    'shift_c': {
      handler: () => {
        if (isInputFocused()) return
        callbacks.setCutMode?.('polygon')
      }
    },
    'alt_c': {
      handler: () => {
        if (isInputFocused()) return
        callbacks.setCutMode?.('rectangle')
      }
    },

    '1': {
      handler: () => {
        if (isInputFocused()) return
        callbacks.setViewMode(VIEW_MODES.DEFAULT)
      }
    },
    '2': {
      handler: () => {
        if (isInputFocused()) return
        callbacks.setViewMode(VIEW_MODES.TEXTLINE)
      }
    },
    '3': {
      handler: () => {
        if (isInputFocused()) return
        callbacks.setViewMode(VIEW_MODES.BASELINE)
      }
    },

    'escape': {
      handler: () => {
        if (isInputFocused()) return
        callbacks.clearSelection()
      }
    },
    'meta_a': {
      handler: () => {
        if (isInputFocused()) return
        callbacks.selectAll()
      }
    },
    'delete': {
      handler: deleteSelectedElements
    },
    'backspace': {
      handler: deleteSelectedElements
    },

    'm': {
      handler: () => {
        if (isInputFocused()) return
        callbacks.mergeSelected?.()
      }
    },

    'arrowup': {
      handler: () => navigateElements('prev')
    },
    'arrowdown': {
      handler: () => navigateElements('next')
    },
    'arrowleft': {
      handler: () => {
        if (isInputFocused()) return
      }
    },
    'arrowright': {
      handler: () => {
        if (isInputFocused()) return
      }
    },

    'tab': {
      handler: () => {
        if (isInputFocused()) return
        navigateElements('next')
      }
    },
    'shift_tab': {
      handler: () => {
        if (isInputFocused()) return
        navigateElements('prev')
      }
    },

    'meta_=': {
      handler: () => {
        if (isInputFocused()) return
        callbacks.zoomIn()
      }
    },
    'meta_-': {
      handler: () => {
        if (isInputFocused()) return
        callbacks.zoomOut()
      }
    },
    'meta_0': {
      handler: () => {
        if (isInputFocused()) return
        callbacks.fitToContent()
      }
    },
    'f': {
      handler: () => {
        if (isInputFocused()) return
        callbacks.centerOnSelection()
      }
    },

    'meta_\\': {
      handler: () => {
        if (isInputFocused()) return
        callbacks.toggleLeftSidebar?.()
      }
    },
    'meta_shift_\\': {
      handler: () => {
        if (isInputFocused()) return
        callbacks.toggleRightSidebar?.()
      }
    },

    'space': {
      handler: () => {
        if (isInputFocused()) return
        callbacks.centerOnSelection()
      }
    },

    'shift_?': {
      handler: () => {
        if (isInputFocused()) return
        callbacks.toggleShortcutsHelp?.()
      }
    },

    'l': {
      handler: () => {
        if (isInputFocused()) return
        callbacks.setUiMode?.('layout')
      }
    },
    'shift_l': {
      handler: () => {
        if (isInputFocused()) return
        callbacks.setUiMode?.('text')
      }
    },

    'k': {
      handler: () => {
        if (isInputFocused()) return
        callbacks.toggleVirtualKeyboard?.()
      }
    },

    'meta_s': {
      handler: () => {
        if (isInputFocused()) return
        callbacks.saveDocument?.()
      }
    },

    'meta_arrowdown': {
      handler: () => {
        if (isInputFocused()) return
        callbacks.nextImage?.()
      }
    },
    'meta_arrowup': {
      handler: () => {
        if (isInputFocused()) return
        callbacks.prevImage?.()
      }
    },

  })

  if (import.meta.client) {
    const onAltW = (e: KeyboardEvent) => {
      if (e.code !== 'KeyW' || !e.altKey || e.metaKey || e.ctrlKey || e.shiftKey) return
      if (isInputFocused()) return
      e.preventDefault()
      callbacks.closeActiveTab?.()
    }
    onMounted(() => window.addEventListener('keydown', onAltW))
    onUnmounted(() => window.removeEventListener('keydown', onAltW))
  }

  return {
    deleteSelectedElements,
    navigateElements,
    isInputFocused
  }
}

/**
 * Keyboard shortcut definitions for display in UI (e.g., tooltips, help dialogs)
 * Each entry has: key (display string), kbds (array for UTooltip), description
 */
export const SHORTCUT_DEFINITIONS = {
  undo: { key: '⌘Z', kbds: ['meta', 'Z'], description: 'Undo' },
  redo: { key: '⌘⇧Z', kbds: ['meta', 'shift', 'Z'], description: 'Redo' },

  selectMode: { key: 'V', kbds: ['V'], description: 'Select' },
  moveMode: { key: 'G', kbds: ['G'], description: 'Move' },

  regionPolygon: { key: 'P', kbds: ['P'], description: 'Region (Polygon)' },
  regionRectangle: { key: 'R', kbds: ['R'], description: 'Region (Rectangle)' },

  textlinePolygon: { key: 'T', kbds: ['T'], description: 'Textline (Polygon)' },
  textlineRectangle: { key: '⇧T', kbds: ['shift', 'T'], description: 'Textline (Rectangle)' },

  baseline: { key: 'B', kbds: ['B'], description: 'Baseline' },

  cutLine: { key: 'C', kbds: ['C'], description: 'Cut Line' },
  cutPolygon: { key: '⇧C', kbds: ['shift', 'C'], description: 'Cut Polygon' },
  cutRectangle: { key: '⌥C', kbds: ['alt', 'C'], description: 'Cut Rectangle' },

  defaultView: { key: '1', kbds: ['1'], description: 'Default view' },
  textlineView: { key: '2', kbds: ['2'], description: 'Textline view' },
  baselineView: { key: '3', kbds: ['3'], description: 'Baseline view' },

  clearSelection: { key: 'Esc', kbds: ['escape'], description: 'Clear selection' },
  selectAll: { key: '⌘A', kbds: ['meta', 'A'], description: 'Select all' },
  delete: { key: 'Del', kbds: ['delete'], description: 'Delete selected' },
  merge: { key: 'M', kbds: ['M'], description: 'Merge' },

  nextElement: { key: '↓', kbds: ['↓'], description: 'Next element' },
  prevElement: { key: '↑', kbds: ['↑'], description: 'Previous element' },
  tabNext: { key: 'Tab', kbds: ['tab'], description: 'Cycle next' },
  tabPrev: { key: '⇧Tab', kbds: ['shift', 'tab'], description: 'Cycle previous' },

  zoomIn: { key: '⌘+', kbds: ['meta', '+'], description: 'Zoom in' },
  zoomOut: { key: '⌘-', kbds: ['meta', '-'], description: 'Zoom out' },
  fitToContent: { key: '⌘0', kbds: ['meta', '0'], description: 'Fit to content' },
  centerOnSelection: { key: 'F', kbds: ['F'], description: 'Center on selection' },

  toggleLeftSidebar: { key: '⌘\\', kbds: ['meta', '\\'], description: 'Toggle left sidebar' },
  toggleRightSidebar: { key: '⌘⇧\\', kbds: ['meta', 'shift', '\\'], description: 'Toggle right sidebar' },

  layoutMode: { key: 'L', kbds: ['L'], description: 'Layout mode' },
  textMode: { key: '⇧L', kbds: ['shift', 'L'], description: 'Text mode' },

  toggleVirtualKeyboard: { key: 'K', kbds: ['K'], description: 'Virtual Keyboard' },

  history: { key: 'H', kbds: ['H'], description: 'History' },

  save: { key: '⌘S', kbds: ['meta', 'S'], description: 'Save' },

  nextImage: { key: '⌘↓', kbds: ['meta', '↓'], description: 'Next image' },
  prevImage: { key: '⌘↑', kbds: ['meta', '↑'], description: 'Previous image' },

  closeActiveTab: { key: '⌥W', kbds: ['alt', 'W'], description: 'Close active tab' },

  showHelp: { key: '?', kbds: ['shift', '/'], description: 'Keyboard shortcuts' }
} as const

export type ShortcutKey = keyof typeof SHORTCUT_DEFINITIONS

/**
 * Get tooltip props for a shortcut definition
 */
export function getTooltipProps(key: ShortcutKey): { text: string, kbds: string[] } {
  const def = SHORTCUT_DEFINITIONS[key]
  return { text: def.description, kbds: def.kbds as unknown as string[] }
}
