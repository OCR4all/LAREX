import { PcGts } from '@/models/editor'
import { Polygon } from '@/models/editor/geometry'
import type { Region, TextLine, TextRegion } from '@/models/editor'
import type { CommandContext } from './types'
import { invalidateMultiplePolygonGeometry } from '@/composables/editor/use-geometry-cache-integrations'
import type { EditorSession } from '@/session/editor/editor-session'
import { visibilityService } from '@/services/editor/visibility-service'
import { findRegionRecursive, rebuildSpatialIndexFromPcGts } from '@/utils/editor/pcgts-editor-primitives'
import type { GridPoint } from '@/utils/editor/table/table-geometry'
import { assignToShapes, centroid } from '@/utils/editor/table/table-builder'

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

export function invalidateRegionTrees(ctx: CommandContext | undefined, regions: Region[]): void {
  if (!ctx?.canvasId) return
  const ids: string[] = []
  const visit = (region: Region) => {
    ids.push(region.id)
    region.regions?.forEach(visit)
  }
  regions.forEach(visit)
  invalidateMultiplePolygonGeometry(ctx.canvasId, ids)
}

export function compareLinesTopToBottom(a: TextLine, b: TextLine): number {
  const [ax, ay] = centroid(shapePoints(a))
  const [bx, by] = centroid(shapePoints(b))
  return by - ay || ax - bx
}

export function resortLinesIntoCells(cells: TextRegion[]): number {
  const owned = cells.flatMap(cell => (cell.textLines ?? []).map(line => ({ line, owner: cell })))
  const assignment = assignToShapes(
    owned.map(({ line }) => ({ id: line.id, points: shapePoints(line) })),
    cells.map(cell => ({ id: cell.id, points: shapePoints(cell) })),
    false
  )
  const moving = owned.filter(({ line, owner }) => {
    const target = assignment.get(line.id)
    return target !== undefined && target !== owner.id
  })
  if (moving.length === 0) return 0
  const movingIds = new Set(moving.map(({ line }) => line.id))
  const touched = new Set<TextRegion>()
  for (const { owner } of moving) {
    owner.textLines = (owner.textLines ?? []).filter(line => !movingIds.has(line.id))
  }
  const cellsById = new Map(cells.map(cell => [cell.id, cell]))
  for (const { line } of moving) {
    const target = cellsById.get(assignment.get(line.id)!)!
    target.textLines = [...(target.textLines ?? []), line]
    touched.add(target)
  }
  for (const cell of touched) cell.textLines = [...cell.textLines!].sort(compareLinesTopToBottom)
  return moving.length
}
