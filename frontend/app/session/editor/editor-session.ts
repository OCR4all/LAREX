import { createSpatialIndex, type SpatialIndexService } from '@/services/editor/spatial-index-service'
import type { PcGts } from '@/models/editor'
import type { EditorCanvasControls } from '@/types/editor/canvas-controls'

export type PageVisibilityState = {
  hiddenPolygonIds: string[]
  hiddenPolylineIds: string[]
}

export interface EditorSession {
  canvasId: string
  document: ReturnType<typeof shallowRef<PcGts | null>>
  spatialIndex: SpatialIndexService
  controls: ReturnType<typeof shallowRef<EditorCanvasControls | null>>
  textViewSettings: ReturnType<typeof shallowRef<TextViewSettings>>
  destroy: () => void
}

export interface EditorFloatingAnchor {
  anchorId: string
  element: HTMLElement
}

export const EDITOR_WORKSPACE_FLOATING_ANCHOR_ID = 'editor-workspace'

export type TextViewSettings = {
  mode: 'textline'
  gtIndex: number | undefined
  showDiff: boolean
  showComments: boolean
  focusMode: boolean
  confidenceRange: [number, number]
  selectedIndices: number[]
  filterUnindexed: boolean
  showNonAssignedIndices: boolean
  onlyMissingGt: boolean
  padding: number
}

const sessions = new Map<string, EditorSession>()

const pageVisibilityByPageId = new Map<string, ReturnType<typeof shallowRef<PageVisibilityState>>>()
const floatingAnchorsById = new Map<string, EditorFloatingAnchor>()
const floatingAnchorRegistryVersion = shallowRef(0)

function bumpFloatingAnchorRegistryVersion(): void {
  floatingAnchorRegistryVersion.value++
}

function assertClientOnly(action: string): void {
  if (import.meta.server) {
    throw new Error(`[EditorSession] ${action} is client-only`)
  }
}

export function getEditorSession(canvasId: string): EditorSession | undefined {
  if (import.meta.server) return undefined
  return sessions.get(canvasId)
}

export function useEditorFloatingAnchorRegistryVersion(): ReturnType<typeof shallowRef<number>> {
  if (import.meta.server) return shallowRef(0)
  return floatingAnchorRegistryVersion
}

export function registerEditorFloatingAnchor(anchorId: string, element: HTMLElement): void {
  if (import.meta.server) return

  const current = floatingAnchorsById.get(anchorId)
  if (current?.element === element) return

  floatingAnchorsById.set(anchorId, { anchorId, element })
  bumpFloatingAnchorRegistryVersion()
}

export function unregisterEditorFloatingAnchor(anchorId: string, element?: HTMLElement): void {
  if (import.meta.server) return

  const current = floatingAnchorsById.get(anchorId)
  if (!current) return
  if (element && current.element !== element) return

  floatingAnchorsById.delete(anchorId)
  bumpFloatingAnchorRegistryVersion()
}

export function getEditorFloatingAnchor(anchorId: string | null | undefined): EditorFloatingAnchor | null {
  if (import.meta.server || !anchorId) return null
  return floatingAnchorsById.get(anchorId) ?? null
}

export function getEditorFloatingAnchorElement(anchorId: string | null | undefined): HTMLElement | null {
  return getEditorFloatingAnchor(anchorId)?.element ?? null
}

export function getEditorFloatingAnchorRect(anchorId: string | null | undefined): DOMRect | null {
  const element = getEditorFloatingAnchorElement(anchorId)
  return element ? element.getBoundingClientRect() : null
}

function createServerPlaceholderPageVisibilityState(): ReturnType<typeof shallowRef<PageVisibilityState>> {
  return shallowRef<PageVisibilityState>({ hiddenPolygonIds: [], hiddenPolylineIds: [] })
}

/**
 * Runtime-only per-page visibility state.
 * Key by pageId ("one page per document" in this app).
 */
export function usePageVisibilityState(pageId: string | null | undefined): ReturnType<typeof shallowRef<PageVisibilityState>> {
  if (import.meta.server) return createServerPlaceholderPageVisibilityState()
  if (!pageId) return createServerPlaceholderPageVisibilityState()

  let state = pageVisibilityByPageId.get(pageId)
  if (!state) {
    state = shallowRef<PageVisibilityState>({ hiddenPolygonIds: [], hiddenPolylineIds: [] })
    pageVisibilityByPageId.set(pageId, state)
  }
  return state
}

export function clearPageVisibilityState(pageId: string): void {
  if (import.meta.server) return
  pageVisibilityByPageId.delete(pageId)
}

function createServerPlaceholderSession(canvasId: string): EditorSession {
  const spatialIndex = createSpatialIndex()
  const document = shallowRef<PcGts | null>(null)
  const controls = shallowRef<EditorCanvasControls | null>(null)
  const textViewSettings = shallowRef<TextViewSettings>({
    mode: 'textline',
    gtIndex: 0,
    showDiff: false,
    showComments: false,
    focusMode: false,
    confidenceRange: [0, 1],
    selectedIndices: [],
    filterUnindexed: false,
    showNonAssignedIndices: false,
    onlyMissingGt: false,
    padding: 10
  })

  return {
    canvasId,
    document,
    spatialIndex,
    controls,
    textViewSettings,
    destroy: () => {
      spatialIndex.clear()
      document.value = null
      controls.value = null
    }
  }
}

export function ensureEditorSession(canvasId: string, initial?: { document?: PcGts | null }): EditorSession {
  assertClientOnly(`ensureEditorSession(${canvasId})`)

  const existing = sessions.get(canvasId)
  if (existing) return existing

  const spatialIndex = createSpatialIndex()
  const document = shallowRef<PcGts | null>(initial?.document ?? null)
  const controls = shallowRef<EditorCanvasControls | null>(null)
  const textViewSettings = shallowRef<TextViewSettings>({
    mode: 'textline',
    gtIndex: 0,
    showDiff: false,
    showComments: false,
    focusMode: false,
    confidenceRange: [0, 1],
    selectedIndices: [],
    filterUnindexed: false,
    showNonAssignedIndices: false,
    onlyMissingGt: false,
    padding: 10
  })

  const session: EditorSession = {
    canvasId,
    document,
    spatialIndex,
    controls,
    textViewSettings,
    destroy: () => {
      spatialIndex.clear()
      document.value = null
      controls.value = null
      sessions.delete(canvasId)
    }
  }

  sessions.set(canvasId, session)
  return session
}

export function destroyEditorSession(canvasId: string): void {
  const session = import.meta.server ? undefined : sessions.get(canvasId)
  session?.destroy()
}

export function clearAllEditorSessions(): void {
  if (import.meta.server) return
  for (const id of sessions.keys()) {
    destroyEditorSession(id)
  }
}

export function useEditorSession(canvasId: string): EditorSession {
  if (import.meta.server) return createServerPlaceholderSession(canvasId)
  return ensureEditorSession(canvasId)
}
