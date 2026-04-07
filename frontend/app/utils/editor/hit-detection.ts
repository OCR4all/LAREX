import earcut from 'earcut'
import { visibilityService } from '@/services/editor/visibility-service'
import type { Polygon, Polyline } from '@/services/editor/visibility-service'
import type { SpatialIndexService } from '@/services/editor/spatial-index-service'
import type { Point, View } from '@/models/editor'
import type { ViewMode } from '@/types/editor/rendering'
import { createScopedLogger } from '@/services/editor/logger-service'

const log = createScopedLogger('HitDetection')

const SNAP_DISTANCE = 0.015

export function isPointInPolygon(point: Point, polygon: Point[]): boolean {
  if (polygon.length < 3) return false

  let inside = false
  for (let i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
    const pi = polygon[i]
    const pj = polygon[j]
    if (!pi || !pj) continue

    const xi = pi.x, yi = pi.y
    const xj = pj.x, yj = pj.y

    const intersect = ((yi > point.y) !== (yj > point.y))
      && (point.x < (xj - xi) * (point.y - yi) / (yj - yi) + xi)
    if (intersect) inside = !inside
  }

  return inside
}

/**
 * Get polygon at point that respects parent-child visibility rules.
 * Only returns polygons that should be visible/selectable based on current selection.
 *
 * @param polygons - Array of all polygons
 * @param point - Point to check
 * @param selectedPolygonIndex - Index of currently selected polygon (-1 if none)
 * @param spatialIndex - Optional spatial index for performance optimization
 * @param viewMode
 * @param hiddenPolygonIds
 * @returns Index of visible polygon at point, or -1 if none
 */
export function getVisiblePolygonAtPoint(
  polygons: Polygon[],
  point: Point,
  selectedPolygonIndex: number = -1,
  spatialIndex?: SpatialIndexService,
  viewMode?: ViewMode,
  hiddenPolygonIds?: Set<string>
): number {
  const candidates = spatialIndex
    ? spatialIndex.queryPolygonsAtPoint(point)
    : Array.from({ length: polygons.length }, (_, i) => i).reverse()

  for (const polygonIndex of candidates) {
    const polygon = polygons[polygonIndex]
    if (!polygon) continue

    if (hiddenPolygonIds?.has(polygon.id)) {
      continue
    }

    if (!visibilityService.shouldBeSelectablePolygon(polygon, {
      selectedPolygonIndex,
      allPolygons: polygons,
      viewMode: viewMode as 'default' | 'textline' | 'baseline' | undefined,
      hiddenPolygonIds
    })) {
      continue
    }

    if (isPointInPolygon(point, polygon.points)) {
      return polygonIndex
    }
  }
  return -1 // No visible polygon found
}

/**
 * Get polygon at point that should show hover feedback.
 * Uses "downwards-only" hover logic - only children of selected element show hover.
 *
 * @param polygons - Array of all polygons
 * @param point - Point to check
 * @param selectedPolygonIndex - Index of currently selected polygon (-1 if none)
 * @param selectedPolylineIndex
 * @param polylines
 * @param spatialIndex - Optional spatial index for performance optimization
 * @param viewMode
 * @param hiddenPolygonIds
 * @param hiddenPolylineIds
 * @returns Index of hoverable polygon at point, or -1 if none
 */
export function getHoverablePolygonAtPoint(
  polygons: Polygon[],
  point: Point,
  selectedPolygonIndex: number = -1,
  selectedPolylineIndex: number = -1,
  polylines: Polyline[] = [],
  spatialIndex?: SpatialIndexService,
  viewMode?: ViewMode,
  hiddenPolygonIds?: Set<string>,
  hiddenPolylineIds?: Set<string>
): number {
  const candidates = spatialIndex
    ? spatialIndex.queryPolygonsAtPoint(point)
    : Array.from({ length: polygons.length }, (_, i) => i).reverse()

  for (const polygonIndex of candidates) {
    const polygon = polygons[polygonIndex]
    if (!polygon) continue

    if (hiddenPolygonIds?.has(polygon.id)) {
      continue
    }

    const shouldShow = visibilityService.shouldShowPolygonHover(polygon, {
      selectedPolygonIndex,
      selectedPolylineIndex,
      allPolygons: polygons,
      allPolylines: polylines,
      viewMode: viewMode as 'default' | 'textline' | 'baseline' | undefined,
      hiddenPolygonIds,
      hiddenPolylineIds
    })

    if (!shouldShow) {
      continue
    }

    const isInside = isPointInPolygon(point, polygon.points)

    if (isInside) {
      return polygonIndex
    }
  }
  return -1 // No hoverable polygon found
}

/**
 * Get polyline at point that should show hover feedback.
 * Uses "downwards-only" hover logic - only children of selected element show hover.
 *
 * @param polylines - Array of all polylines
 * @param polygons - Array of all polygons (for parent lookup)
 * @param point - Point to check
 * @param selectedPolygonIndex - Index of currently selected polygon (-1 if none)
 * @param threshold - Distance threshold for polyline detection
 * @param spatialIndex - Optional spatial index for performance optimization
 * @returns Index of hoverable polyline at point, or -1 if none
 */
export function getHoverablePolylineAtPoint(
  polylines: Polyline[],
  polygons: Polygon[],
  point: { x: number, y: number },
  selectedPolygonIndex: number = -1,
  selectedPolylineIndex: number = -1,
  threshold: number = 0.02,
  spatialIndex?: SpatialIndexService,
  viewMode?: string,
  hiddenPolygonIds?: Set<string>,
  hiddenPolylineIds?: Set<string>
) {
  const candidates = spatialIndex
    ? spatialIndex.queryPolylinesAtPoint(point, threshold)
    : Array.from({ length: polylines.length }, (_, i) => i).reverse()

  for (const polylineIndex of candidates) {
    const polyline = polylines[polylineIndex]
    if (!polyline) continue

    if (hiddenPolylineIds?.has(polyline.id)) {
      continue
    }

    if (!visibilityService.shouldShowPolylineHover(polyline, {
      selectedPolygonIndex,
      selectedPolylineIndex,
      allPolygons: polygons,
      allPolylines: polylines,
      viewMode: viewMode as 'default' | 'textline' | 'baseline' | undefined,
      hiddenPolygonIds,
      hiddenPolylineIds
    })) {
      continue
    }

    if (isPointNearPolyline(point, polyline.points, threshold)) {
      return polylineIndex
    }
  }
  return -1 // No hoverable polyline found
}

export function getDistance(p1: Point, p2: Point): number {
  const dx = p1.x - p2.x
  const dy = p1.y - p2.y
  return Math.sqrt(dx * dx + dy * dy)
}

export function getNodeAtPoint(polygons: Polygon[], point: Point, polygonIndex: number, view: View): number {
  if (polygonIndex < 0 || polygonIndex >= polygons.length) return -1

  const polygon = polygons[polygonIndex]?.points
  if (!polygon) return -1
  const snapThreshold = SNAP_DISTANCE / view.zoom // Adjust for zoom level

  for (let i = 0; i < polygon.length; i++) {
    const vertex = polygon[i]
    if (!vertex) continue
    if (getDistance(point, vertex) <= snapThreshold) {
      return i
    }
  }
  return -1
}

export function getClosestPointOnLine(p: Point, a: Point, b: Point): { x: number, y: number, t: number } {
  const ab = { x: b.x - a.x, y: b.y - a.y }
  const ap = { x: p.x - a.x, y: p.y - a.y }
  const ab2 = ab.x * ab.x + ab.y * ab.y
  const ap_ab = ap.x * ab.x + ap.y * ab.y
  let t = ap_ab / ab2
  t = Math.max(0, Math.min(1, t)) // Clamp to line segment
  return {
    x: a.x + t * ab.x,
    y: a.y + t * ab.y,
    t: t
  }
}

export function getClosestEdge(
  polygons: Polygon[],
  point: Point,
  polygonIndex: number,
  view: View
): {
  polygonIndex: number
  edgeStartIndex: number
  closestPoint: { x: number, y: number, t: number }
  distance: number
} | null {
  if (polygonIndex < 0 || polygonIndex >= polygons.length) return null

  const polygon = polygons[polygonIndex]?.points
  if (!polygon) return null
  if (polygon.length < 2) return null

  let closestEdge = null
  let minDistance = Infinity
  const snapThreshold = SNAP_DISTANCE / view.zoom

  for (let i = 0; i < polygon.length; i++) {
    const nextIndex = (i + 1) % polygon.length
    const a = polygon[i]
    const b = polygon[nextIndex]
    if (!a || !b) continue
    const closestPoint = getClosestPointOnLine(point, a, b)
    const distance = getDistance(point, closestPoint)

    if (distance < minDistance && distance <= snapThreshold) {
      minDistance = distance
      closestEdge = {
        polygonIndex: polygonIndex,
        edgeStartIndex: i,
        closestPoint: closestPoint,
        distance: distance
      }
    }
  }

  return closestEdge
}

export function triangulatePolygon(polygon: Point[]): number[] {
  if (polygon.length < 3) return []
  if (polygon.length === 3) return [0, 1, 2]

  const coords: number[] = []
  for (const p of polygon) {
    coords.push(p.x, p.y)
  }

  return earcut(coords)
}

export function isPointInTriangle(p: Point, a: Point, b: Point, c: Point): boolean {
  const denom = (b.y - c.y) * (a.x - c.x) + (c.x - b.x) * (a.y - c.y)
  if (Math.abs(denom) < 1e-10) return false

  const alpha = ((b.y - c.y) * (p.x - c.x) + (c.x - b.x) * (p.y - c.y)) / denom
  const beta = ((c.y - a.y) * (p.x - c.x) + (a.x - c.x) * (p.y - c.y)) / denom
  const gamma = 1.0 - alpha - beta

  const epsilon = 1e-10
  return alpha > epsilon && beta > epsilon && gamma > epsilon
}

export function wouldSelfIntersect(polygon: Point[], vertexIndex: number, newPosition: Point): boolean {
  const testPolygon = [...polygon]
  testPolygon[vertexIndex] = newPosition

  for (let i = 0; i < testPolygon.length; i++) {
    const p1 = testPolygon[i]
    const p2 = testPolygon[(i + 1) % testPolygon.length]
    if (!p1 || !p2) continue

    for (let j = i + 2; j < testPolygon.length; j++) {
      if (j === (i + testPolygon.length - 1) % testPolygon.length) continue
      if (i === 0 && j === testPolygon.length - 1) continue

      const p3 = testPolygon[j]
      const p4 = testPolygon[(j + 1) % testPolygon.length]

      if (!p3 || !p4) continue

      if (segmentsIntersect(p1, p2, p3, p4)) {
        return true
      }
    }
  }

  return false
}

export function wouldNewVertexSelfIntersect(existingVertices: Point[], newVertex: Point, _closingThreshold: number): boolean {
  if (existingVertices.length < 2) return false

  const lastVertex = existingVertices[existingVertices.length - 1]
  if (!lastVertex) return false

  for (let i = 0; i < existingVertices.length - 2; i++) {
    const p1 = existingVertices[i]
    const p2 = existingVertices[i + 1]
    if (!p1 || !p2) continue

    if (segmentsIntersect(lastVertex, newVertex, p1, p2)) {
      return true
    }
  }

  return false
}

export function isClosedPolygonSelfIntersecting(vertices: Point[]): boolean {
  if (vertices.length < 3) return false

  for (let i = 0; i < vertices.length; i++) {
    const p1 = vertices[i]
    const p2 = vertices[(i + 1) % vertices.length]
    if (!p1 || !p2) continue

    for (let j = i + 2; j < vertices.length; j++) {
      if (i === 0 && j === vertices.length - 1) continue // Skip adjacent first/last edge
      const p3 = vertices[j]
      const p4 = vertices[(j + 1) % vertices.length]
      if (!p3 || !p4) continue

      if (segmentsIntersect(p1, p2, p3, p4)) {
        return true
      }
    }
  }

  return false
}

export function direction(p1: Point, p2: Point, p3: Point): number {
  return (p3.x - p1.x) * (p2.y - p1.y) - (p2.x - p1.x) * (p3.y - p1.y)
}

export function segmentsIntersect(p1: Point, p2: Point, p3: Point, p4: Point): boolean {
  const d1 = direction(p3, p4, p1)
  const d2 = direction(p3, p4, p2)
  const d3 = direction(p1, p2, p3)
  const d4 = direction(p1, p2, p4)

  if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0))
    && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
    return true
  }

  return false
}

export function getDistanceToLineSegment(
  point: Point,
  lineStart: Point,
  lineEnd: Point
): { distance: number, closestPoint: Point } {
  const A = point.x - lineStart.x
  const B = point.y - lineStart.y
  const C = lineEnd.x - lineStart.x
  const D = lineEnd.y - lineStart.y

  const dot = A * C + B * D
  const lenSq = C * C + D * D
  let param = -1

  if (lenSq !== 0) {
    param = dot / lenSq
  }

  let xx, yy

  if (param < 0) {
    xx = lineStart.x
    yy = lineStart.y
  } else if (param > 1) {
    xx = lineEnd.x
    yy = lineEnd.y
  } else {
    xx = lineStart.x + param * C
    yy = lineStart.y + param * D
  }

  const dx = point.x - xx
  const dy = point.y - yy

  return {
    distance: Math.sqrt(dx * dx + dy * dy),
    closestPoint: { x: xx, y: yy }
  }
}

export function isPointNearPolyline(point: Point, polyline: Point[], threshold = 0.02): boolean {
  if (polyline.length < 2) return false

  for (let i = 0; i < polyline.length - 1; i++) {
    const a = polyline[i]
    const b = polyline[i + 1]
    if (!a || !b) continue
    const result = getDistanceToLineSegment(point, a, b)
    if (result.distance <= threshold) {
      return true
    }
  }

  return false
}
