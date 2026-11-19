import { PcGts } from '@/models/editor'
import { Polygon } from '@/models/editor/geometry'
import type { Region, TextRegion } from '@/models/editor'
import type { EditorSession } from '@/session/editor/editor-session'
import { visibilityService } from '@/services/editor/visibility-service'
import { findRegionRecursive, rebuildSpatialIndexFromPcGts } from '@/utils/editor/pcgts-editor-primitives'
import type { GridPoint } from '@/utils/editor/table/table-geometry'

export function newRegionId(prefix: string): string {
  return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 11)}`
}

export function regionSiblings(pcGts: PcGts, parentId: string | null): Region[] | null {
  if (!parentId) return pcGts.page.regions
  const parent = findRegionRecursive(pcGts.page.regions, parentId)?.region
  if (!parent) return null
  parent.regions = parent.regions ?? []
  return parent.regions
}

export function replaceContents<T>(target: T[], next: T[]): void {
  target.splice(0, target.length, ...next)
}

export function replaceRegion(pcGts: PcGts, regionId: string, replacement: Region): boolean {
  const hit = findRegionRecursive(pcGts.page.regions, regionId)
  if (!hit) return false
  const siblings = hit.parent ? hit.parent.regions! : pcGts.page.regions
  siblings.splice(hit.index, 1, replacement)
  return true
}

export function commitDocument(session: EditorSession, pcGts: PcGts): void {
  session.document.value = new PcGts(pcGts.metadata, pcGts.page, pcGts.pcGtsId)
  rebuildSpatialIndexFromPcGts(session)
  visibilityService.clearCache()
}

export function shapePoints(region: { coords: { points: [number, number][] } }): GridPoint[] {
  return region.coords.points.map(([x, y]) => [x, y])
}

export function createCellRegion(id: string): TextRegion {
  return {
    id,
    kind: 'TextRegion',
    coords: new Polygon([]),
    regions: [],
    textLines: [],
    textContentVariants: []
  }
}
