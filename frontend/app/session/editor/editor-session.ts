import { createSpatialIndex, type SpatialIndexService } from '@/services/editor/spatial-index-service'
import type { PcGts } from '@/models/editor'

export type PageVisibilityState = {
  hiddenPolygonIds: string[]
  hiddenPolylineIds: string[]
}

export interface EditorSession {
  canvasId: string
  document: ReturnType<typeof shallowRef<PcGts | null>>
  spatialIndex: SpatialIndexService
  controls: ReturnType<typeof shallowRef<unknown | null>>
  textViewSettings: ReturnType<typeof shallowRef<TextViewSettings>>
  destroy: () => void
}

export type TextViewSettings = {
  mode: 'textline' | 'region'
  gtIndex: number | undefined
  showDiff: boolean
  confidenceRange: [number, number]
  selectedIndices: number[]
  filterUnindexed: boolean
  showNonAssignedIndices: boolean
  onlyMissingGt: boolean
  padding: number
}

const sessions = new Map<string, EditorSession>()

const pageVisibilityByPageId = new Map<string, ReturnType<typeof shallowRef<PageVisibilityState>>>()

function assertClientOnly(action: string): void {
  if (import.meta.server) {
    throw new Error(`[EditorSession] ${action} is client-only`)
  }
}

export function getEditorSession(canvasId: string): EditorSession | undefined {
  if (import.meta.server) return undefined
  return sessions.get(canvasId)
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
  const controls = shallowRef<unknown | null>(null)
  const textViewSettings = shallowRef<TextViewSettings>({
    mode: 'textline',
    gtIndex: 0,
    showDiff: false,
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
  const controls = shallowRef<unknown | null>(null)
  const textViewSettings = shallowRef<TextViewSettings>({
    mode: 'textline',
    gtIndex: 0,
    showDiff: false,
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
