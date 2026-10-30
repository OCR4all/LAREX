import { isPointInPolygon } from './hit-detection'
import { PolygonType } from '@/models/editor'
import type { PolygonType as PolygonTypeType } from '@/models/editor'
import type { RenderablePolygon } from '@/types/editor/rendering'

/**
 * Find the parent polygon for a given region type and current context.
 * @param polygons - Array of all polygons
 * @param selectedPolygonIndex - Index of currently selected polygon (-1 if none)
 * @param regionType - Type of region being created/edited
 * @returns Parent polygon object or null if no parent should be used
 */
export function findParentPolygon(polygons: RenderablePolygon[], selectedPolygonIndex: number, regionType: PolygonTypeType): RenderablePolygon | null {
  if (selectedPolygonIndex < 0 || selectedPolygonIndex >= polygons.length) {
    return null
  }

  const selectedPolygon = polygons[selectedPolygonIndex]
  if (!selectedPolygon) {
    return null
  }

  switch (regionType) {
    case PolygonType.TEXTLINE:
      if (selectedPolygon.type === PolygonType.REGION) {
        return selectedPolygon
      }
      break

    case PolygonType.BASELINE:
      if (selectedPolygon.type === PolygonType.TEXTLINE) {
        return selectedPolygon
      }
      break

    case PolygonType.REGION:
      if (selectedPolygon.type === PolygonType.REGION) {
        return selectedPolygon
      }
      break
  }

  return null
}

/**
 * Check if a point is within parent polygon boundaries.
 * @param point - Point to check
 * @param parentPolygon - Parent polygon to constrain to
 * @returns True if point is within parent boundaries, false otherwise
 */
export function isPointWithinParentBounds(point: { x: number, y: number }, parentPolygon: RenderablePolygon): boolean {
  if (!parentPolygon || !parentPolygon.points || parentPolygon.points.length < 3) {
    return true // No parent constraint if no valid parent
  }

  return isPointWithinOrOnPolygon(point, parentPolygon.points)
}

/**
 * Check if all polygon points are within parent boundaries.
 * @param points - Array of points to check
 * @param parentPolygon - Parent polygon to constrain to
 * @returns True if all points are within parent boundaries, false otherwise
 */
export function areAllPointsWithinParentBounds(points: { x: number, y: number }[], parentPolygon: RenderablePolygon): boolean {
  if (!parentPolygon || !parentPolygon.points || parentPolygon.points.length < 3) {
    return true // No parent constraint if no valid parent
  }

  return points.every(point => isPointWithinOrOnPolygon(point, parentPolygon.points))
}

/** Parent boundaries are inclusive, including vertices introduced by clipping. */
function isPointWithinOrOnPolygon(point: { x: number, y: number }, points: { x: number, y: number }[]): boolean {
  if (isPointInPolygon(point, points)) return true
  return points.some((start, index) => {
    const end = points[(index + 1) % points.length]!
    const dx = end.x - start.x
    const dy = end.y - start.y
    const length = Math.hypot(dx, dy)
    if (length === 0) return Math.hypot(point.x - start.x, point.y - start.y) <= 1e-9
    const cross = dx * (point.y - start.y) - dy * (point.x - start.x)
    const dot = (point.x - start.x) * dx + (point.y - start.y) * dy
    return Math.abs(cross) <= 1e-9 * length && dot >= -1e-9 * length && dot <= length * length + 1e-9 * length
  })
}
