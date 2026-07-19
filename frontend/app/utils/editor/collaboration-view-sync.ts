import type {
  CollaborationCanvasViewMode,
  CollaborationPresence,
  CollaborationViewport
} from '@/types/collaboration'
import { ZOOM } from '@/utils/editor/editor-constants'

export type CollaborationSelectionResolution
  = | { status: 'root' }
    | { status: 'valid', kind: 'region' | 'baseline', id: string }
    | { status: 'missing', kind: 'region' | 'baseline', id: string }

export function isCollaborationCanvasViewMode(value: unknown): value is CollaborationCanvasViewMode {
  return value === 'default' || value === 'textline' || value === 'baseline'
}

export function normalizeCollaborationViewport(value: unknown): CollaborationViewport | null {
  if (!value || typeof value !== 'object') return null

  const candidate = value as Partial<CollaborationViewport>
  const { zoom, offsetX, offsetY } = candidate
  if (typeof zoom !== 'number' || typeof offsetX !== 'number' || typeof offsetY !== 'number') {
    return null
  }
  if (!Number.isFinite(zoom) || !Number.isFinite(offsetX) || !Number.isFinite(offsetY)) {
    return null
  }
  if (zoom < ZOOM.MIN || zoom > ZOOM.MAX) return null

  return { zoom, offsetX, offsetY }
}

export function sameCollaborationViewport(
  left: CollaborationViewport,
  right: CollaborationViewport
): boolean {
  return left.zoom === right.zoom
    && left.offsetX === right.offsetX
    && left.offsetY === right.offsetY
}

export function resolveCollaborationSelection(
  presence: Pick<CollaborationPresence, 'selectionId' | 'selectionKind'>,
  regionIds: ReadonlySet<string>,
  baselineIds: ReadonlySet<string>
): CollaborationSelectionResolution {
  const id = presence.selectionId
  const kind = presence.selectionKind
  if (!id || (kind !== 'region' && kind !== 'baseline')) return { status: 'root' }

  const ids = kind === 'region' ? regionIds : baselineIds
  return ids.has(id)
    ? { status: 'valid', kind, id }
    : { status: 'missing', kind, id }
}
