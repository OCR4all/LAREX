import { computed, type ComputedRef } from 'vue'
import { Commander } from '@/commands/editor/commander'
import type { CommandContext } from '@/commands/editor/types'
import type { TextContentVariantData } from '@/models/editor'
import type { RenderablePolygon } from '@/types/editor/rendering'
import { ensureEditorSession, getEditorSession } from '@/session/editor/editor-session'
import type { SelectionFocusOptions } from '@/types/editor/canvas-controls'

export type TextViewEditorStoreLike = {
  regionsByCanvasId: (canvasId: string) => RenderablePolygon[]
}

export type TextRuntimeControls = {
  polygons: RenderablePolygon[]
  commander?: Commander
  selectedPolygonId?: ComputedRef<string | null>
  selectPolygonById?: (id: string | null, options?: SelectionFocusOptions) => void
  selectPolylineById?: (id: string | null, options?: SelectionFocusOptions) => void
}

const fallbackCommanders = new Map<string, Commander>()

function getFallbackCommander(canvasId: string): Commander {
  const existing = fallbackCommanders.get(canvasId)
  if (existing) return existing

  const created = new Commander()
  fallbackCommanders.set(canvasId, created)
  return created
}

export function getTextViewRuntimeControls(
  canvasId: string | null | undefined,
  editorStore: TextViewEditorStoreLike
): TextRuntimeControls | null {
  if (!canvasId) return null

  const session = import.meta.client ? ensureEditorSession(canvasId) : getEditorSession(canvasId)
  const controls = session?.controls.value

  if (!controls) {
    return {
      polygons: editorStore.regionsByCanvasId(canvasId),
      commander: getFallbackCommander(canvasId)
    }
  }

  if (!controls.commander) {
    controls.commander = new Commander()
  }

  return {
    ...controls,
    polygons: controls.polygons ?? editorStore.regionsByCanvasId(canvasId),
    selectedPolygonId: computed(() => controls.selectedPolygonIds?.value?.[0] ?? null)
  }
}

export function createTextViewCommandContext(canvasId: string | null | undefined): CommandContext | undefined {
  if (!canvasId) return undefined
  const session = getEditorSession(canvasId)
  return session ? { canvasId, session } : undefined
}

export function sortByIndex(a: Pick<TextContentVariantData, 'index'>, b: Pick<TextContentVariantData, 'index'>): number {
  const ai = typeof a.index === 'number' && Number.isFinite(a.index) ? a.index : -1
  const bi = typeof b.index === 'number' && Number.isFinite(b.index) ? b.index : -1
  return ai - bi
}

export function normalizeTextContentVariants(textContentVariants: TextContentVariantData[] | undefined): TextContentVariantData[] {
  const current = (textContentVariants ?? []).map(te => ({ ...te }))
  current.sort(sortByIndex)
  return current
}

export function lowestFreeIndex(existing: TextContentVariantData[]): number {
  const used = new Set(existing
    .map(te => te.index)
    .filter((value): value is number => typeof value === 'number' && Number.isFinite(value) && value >= 0))

  let idx = 0
  while (used.has(idx)) idx++
  return idx
}

export function getRequestErrorMessage(error: unknown): string {
  if (typeof error === 'object' && error) {
    const data = 'data' in error ? error.data : undefined
    if (typeof data === 'object' && data && 'message' in data && typeof data.message === 'string') {
      return data.message
    }
    if ('message' in error && typeof error.message === 'string') {
      return error.message
    }
  }

  return 'Request failed'
}
