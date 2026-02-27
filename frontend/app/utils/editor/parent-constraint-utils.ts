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

  return isPointInPolygon(point, parentPolygon.points)
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

  return points.every(point => isPointInPolygon(point, parentPolygon.points))
}
