import type { PcGts } from '@/models/editor'
import type { RenderablePolygon, RenderablePolyline } from '@/types/editor/rendering'
import {
  baselineIdForTextLineId,
  collectRenderablePolygonsFromPcGts,
  collectRenderablePolylinesFromPcGts
} from '@/utils/editor/pcgts-editor-primitives'

export function baselineIdForTextLine(textLine: { id: string }): string {
  return baselineIdForTextLineId(textLine.id)
}

export function collectPolygonsFromPcGts(pcGts: PcGts): RenderablePolygon[] {
  return collectRenderablePolygonsFromPcGts(pcGts)
}

export function collectPolylinesFromPcGts(pcGts: PcGts): RenderablePolyline[] {
  return collectRenderablePolylinesFromPcGts(pcGts)
}
