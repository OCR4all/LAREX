import { PolygonType, canContainTextLines } from '@/models/editor'
import type { Point } from '@/models/editor'
import type { RegionKind } from '@/models/editor/region'
import { isValidParentChildType } from '@/utils/editor/hierarchy-validation'
import { findBestContainingPolygon } from '@/utils/editor/polygon-clipping'

const CONTAINED_MIN_OVERLAP_PERCENTAGE = 90

const MOVABLE_TYPES: ReadonlySet<string> = new Set([
  PolygonType.REGION,
  PolygonType.TEXTLINE,
  PolygonType.WORD,
  PolygonType.GLYPH
])

export interface ReparentNode {
  id: string
  type?: string
  parentId?: string
  points?: Point[]
  regionKind?: string
  tableCell?: { tableId: string }
}

export type ReparentRules = ReturnType<typeof createReparentRules>

export function createReparentRules(nodes: readonly ReparentNode[]) {
  const byId = new Map(nodes.map(node => [node.id, node]))
  const tablesWithCells = new Set(nodes.flatMap(node => node.tableCell ? [node.tableCell.tableId] : []))

  function hasAncestorIn(id: string, ancestorIds: ReadonlySet<string>): boolean {
    let parentId = byId.get(id)?.parentId
    while (parentId) {
      if (ancestorIds.has(parentId)) return true
      parentId = byId.get(parentId)?.parentId
    }
    return false
  }

  function isMovable(id: string): boolean {
    const node = byId.get(id)
    return !!node && MOVABLE_TYPES.has(node.type ?? '') && !node.tableCell
  }

  function canMove(id: string, newParentId: string | null): boolean {
    const node = byId.get(id)
    if (!node || !isMovable(id) || (node.parentId ?? null) === newParentId) return false
    if (newParentId === null) return node.type === PolygonType.REGION

    const parent = byId.get(newParentId)
    if (!parent || !isValidParentChildType(parent.type ?? '', node.type ?? '')) return false
    if (parent.id === id || hasAncestorIn(newParentId, new Set([id]))) return false
    if (node.type === PolygonType.TEXTLINE) return canContainTextLines(parent.regionKind as RegionKind)
    if (node.type === PolygonType.REGION) return !tablesWithCells.has(parent.id)
    return true
  }

  function withoutDescendants(ids: readonly string[]): string[] {
    const idSet = new Set(ids)
    return [...idSet].filter(id => !hasAncestorIn(id, idSet))
  }

  function movableInto(ids: readonly string[], newParentId: string | null): string[] {
    if (newParentId !== null && ids.includes(newParentId)) return []
    return withoutDescendants(ids).filter(id => canMove(id, newParentId))
  }

  function targetIdsFor(ids: readonly string[]): Set<string> {
    const moving = new Set(ids)
    const topmostIds = withoutDescendants(ids)
    return new Set(nodes
      .filter(node => !moving.has(node.id) && topmostIds.some(id => canMove(id, node.id)))
      .map(node => node.id))
  }

  function parentIdOneLevelUp(id: string): string | null | undefined {
    const parentId = byId.get(id)?.parentId
    if (!parentId) return undefined
    const grandparentId = byId.get(parentId)?.parentId ?? null
    return canMove(id, grandparentId) ? grandparentId : undefined
  }

  function childRegionIdsMovableOut(regionId: string): string[] {
    const region = byId.get(regionId)
    if (!region) return []
    const childIds = nodes
      .filter(node => node.type === PolygonType.REGION && node.parentId === regionId)
      .map(node => node.id)
    return movableInto(childIds, region.parentId ?? null)
  }

  function containedSiblingRegionIds(regionId: string): string[] {
    const region = byId.get(regionId)
    const outline = region?.points
    if (!region || !outline) return []
    const siblingIds = nodes
      .filter(node =>
        node.id !== regionId
        && node.type === PolygonType.REGION
        && (node.parentId ?? null) === (region.parentId ?? null)
        && node.points
        && findBestContainingPolygon(node.points, [{ id: regionId, polygon: outline }], CONTAINED_MIN_OVERLAP_PERCENTAGE)
      )
      .map(node => node.id)
    return movableInto(siblingIds, regionId)
  }

  return {
    isMovable,
    withoutDescendants,
    movableInto,
    targetIdsFor,
    parentIdOneLevelUp,
    childRegionIdsMovableOut,
    containedSiblingRegionIds
  }
}
