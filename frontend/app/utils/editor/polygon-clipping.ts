/**
 * Polygon Clipping and Intersection Utilities
 *
 * This library provides geometric operations for polygon clipping,
 * intersection testing, shape reduction operations, and cut operations
 * for splitting/subtracting polygons.
 */

import type { Point as PointType } from '@/models/editor'
import { intersection as martinezIntersection, diff as martinezDiff, union as martinezUnion } from 'martinez-polygon-clipping'

type MartinezPosition = [number, number]
type MartinezRing = MartinezPosition[]
type MartinezPolygon = MartinezRing[]
type MartinezMultiPolygon = MartinezPolygon[]

/**
 * Point representation
 */
type Point = PointType

/**
 * Polygon representation (array of points in order)
 */
type Polygon = Point[]

/**
 * Line segment representation
 */
export interface LineSegment {
  start: Point
  end: Point
}

/**
 * Polygon relationship analysis result
 */
export interface PolygonRelationship {
  type: 'contained' | 'outside' | 'partial' | 'invalid'
  data: {
    clippedPolygon?: Polygon
    originalArea?: number
    clippedArea?: number
  } | null
}

/**
 * Propagation result
 */
export interface PropagationResult {
  operation: 'none' | 'delete' | 'clip' | 'error'
  polygon: Polygon | null
  reason: string
  metadata?: {
    originalArea: number
    clippedArea: number
    areaReduction: number
  }
}

/**
 * Calculate the intersection point of two line segments
 * @param seg1 - First line segment
 * @param seg2 - Second line segment
 * @returns Intersection point or null if no intersection
 */
function getLineIntersection(seg1: LineSegment, seg2: LineSegment): Point | null {
  const x1 = seg1.start.x, y1 = seg1.start.y
  const x2 = seg1.end.x, y2 = seg1.end.y
  const x3 = seg2.start.x, y3 = seg2.start.y
  const x4 = seg2.end.x, y4 = seg2.end.y

  const denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
  if (Math.abs(denom) < 1e-10) return null // Lines are parallel

  const t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / denom
  const u = -((x1 - x2) * (y1 - y3) - (y1 - y2) * (x1 - x3)) / denom

  if (t >= 0 && t <= 1 && u >= 0 && u <= 1) {
    return {
      x: x1 + t * (x2 - x1),
      y: y1 + t * (y2 - y1)
    }
  }

  return null
}

/**
 * Check if a point is inside a polygon using ray casting algorithm
 * @param point - Point to test
 * @param polygon - Polygon to test against
 * @returns True if point is inside polygon
 */
function isPointInPolygon(point: Point, polygon: Polygon): boolean {
  if (!polygon || polygon.length < 3) return false

  let inside = false
  const x = point.x, y = point.y

  for (let i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
    const pi = polygon[i]
    const pj = polygon[j]
    if (!pi || !pj) continue

    const xi = pi.x, yi = pi.y
    const xj = pj.x, yj = pj.y

    const intersect = ((yi > y) !== (yj > y))
      && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)
    if (intersect) inside = !inside
  }

  return inside
}

/**
 * Check if a polygon is completely inside another polygon
 * @param innerPolygon - Polygon to test
 * @param outerPolygon - Container polygon
 * @returns True if inner polygon is completely inside outer polygon
 */
function isPolygonCompletelyInside(innerPolygon: Polygon, outerPolygon: Polygon): boolean {
  if (!innerPolygon || !outerPolygon || innerPolygon.length < 3 || outerPolygon.length < 3) {
    return false
  }

  for (const vertex of innerPolygon) {
    if (!isPointInPolygon(vertex, outerPolygon)) {
      return false
    }
  }

  return true
}

/**
 * Check if a polygon is completely outside another polygon
 * @param polygon1 - First polygon
 * @param polygon2 - Second polygon
 * @returns True if polygons don't overlap at all
 */
function isPolygonCompletelyOutside(polygon1: Polygon, polygon2: Polygon): boolean {
  if (!polygon1 || !polygon2 || polygon1.length < 3 || polygon2.length < 3) {
    return false
  }

  for (const vertex of polygon1) {
    if (isPointInPolygon(vertex, polygon2)) {
      return false
    }
  }

  for (const vertex of polygon2) {
    if (isPointInPolygon(vertex, polygon1)) {
      return false
    }
  }

  for (let i = 0; i < polygon1.length; i++) {
    const p1Start = polygon1[i]
    const p1End = polygon1[(i + 1) % polygon1.length]
    if (!p1Start || !p1End) continue

    const seg1: LineSegment = {
      start: p1Start,
      end: p1End
    }

    for (let j = 0; j < polygon2.length; j++) {
      const p2Start = polygon2[j]
      const p2End = polygon2[(j + 1) % polygon2.length]
      if (!p2Start || !p2End) continue

      const seg2: LineSegment = {
        start: p2Start,
        end: p2End
      }

      if (getLineIntersection(seg1, seg2)) {
        return false // Polygons intersect
      }
    }
  }

  return true
}

/**
 * Clip a polygon against a clipping polygon using Sutherland-Hodgman algorithm
 * @param subjectPolygon - Polygon to be clipped
 * @param clippingPolygon - Clipping boundary polygon
 * @returns Clipped polygon or null if completely outside
 */
function clipPolygon(subjectPolygon: Polygon, clippingPolygon: Polygon): Polygon | null {
  if (!subjectPolygon || !clippingPolygon || subjectPolygon.length < 3 || clippingPolygon.length < 3) {
    return null
  }

  let outputList = [...subjectPolygon]

  for (let i = 0; i < clippingPolygon.length; i++) {
    if (outputList.length === 0) break

    const inputList = outputList
    outputList = []

    const clipEdgeStart = clippingPolygon[i]
    const clipEdgeEnd = clippingPolygon[(i + 1) % clippingPolygon.length]
    if (!clipEdgeStart || !clipEdgeEnd) continue

    for (let j = 0; j < inputList.length; j++) {
      const currentVertex = inputList[j]
      const nextVertex = inputList[(j + 1) % inputList.length]
      if (!currentVertex || !nextVertex) continue

      const currentInside = isPointOnRightSideOfEdge(currentVertex, clipEdgeStart, clipEdgeEnd)
      const nextInside = isPointOnRightSideOfEdge(nextVertex, clipEdgeStart, clipEdgeEnd)

      if (currentInside && nextInside) {
        outputList.push(nextVertex)
      } else if (currentInside && !nextInside) {
        const intersection = getEdgeIntersection(
          { start: currentVertex, end: nextVertex },
          { start: clipEdgeStart, end: clipEdgeEnd }
        )
        if (intersection) {
          outputList.push(intersection)
        }
      } else if (!currentInside && nextInside) {
        const intersection = getEdgeIntersection(
          { start: currentVertex, end: nextVertex },
          { start: clipEdgeStart, end: clipEdgeEnd }
        )
        if (intersection) {
          outputList.push(intersection)
        }
        outputList.push(nextVertex)
      }
    }
  }

  return outputList.length >= 3 ? outputList : null
}

/**
 * Check if a point is on the right side of a directed edge
 * (Assuming clockwise winding for clipping polygon)
 * @param point - Point to test
 * @param edgeStart - Start point of edge
 * @param edgeEnd - End point of edge
 * @returns True if point is on the right side or on the edge
 */
function isPointOnRightSideOfEdge(point: Point, edgeStart: Point, edgeEnd: Point): boolean {
  const cross = (edgeEnd.x - edgeStart.x) * (point.y - edgeStart.y)
    - (edgeEnd.y - edgeStart.y) * (point.x - edgeStart.x)
  return cross >= -1e-10 // Allow for floating point precision
}

/**
 * Get intersection of two line segments (edge intersection helper)
 * @param seg1 - First line segment
 * @param seg2 - Second line segment
 * @returns Intersection point
 */
function getEdgeIntersection(seg1: LineSegment, seg2: LineSegment): Point | null {
  return getLineIntersection(seg1, seg2)
}

/**
 * Analyze the relationship between two polygons
 * @param childPolygon - The child polygon
 * @param parentPolygon - The parent polygon
 * @returns Analysis result with relationship type and data
 */
function analyzePolygonRelationship(childPolygon: Polygon, parentPolygon: Polygon): PolygonRelationship {
  if (!childPolygon || !parentPolygon) {
    return { type: 'invalid', data: null }
  }

  if (childPolygon.length < 3 || parentPolygon.length < 3) {
    return { type: 'invalid', data: null }
  }

  const completelyInside = isPolygonCompletelyInside(childPolygon, parentPolygon)
  const completelyOutside = isPolygonCompletelyOutside(childPolygon, parentPolygon)

  if (completelyInside) {
    return { type: 'contained', data: null }
  } else if (completelyOutside) {
    return { type: 'outside', data: null }
  } else {
    const clippedPolygon = clipPolygon(childPolygon, parentPolygon)
    if (clippedPolygon && clippedPolygon.length >= 3) {
      return {
        type: 'partial',
        data: {
          clippedPolygon,
          originalArea: calculatePolygonArea(childPolygon),
          clippedArea: calculatePolygonArea(clippedPolygon)
        }
      }
    } else {
      return { type: 'outside', data: null }
    }
  }
}

/**
 * Calculate the area of a polygon using the shoelace formula
 * @param polygon - Polygon to calculate area for
 * @returns Area of the polygon
 */
function calculatePolygonArea(polygon: Polygon): number {
  if (!polygon || polygon.length < 3) return 0

  let area = 0
  for (let i = 0; i < polygon.length; i++) {
    const j = (i + 1) % polygon.length
    const pi = polygon[i]
    const pj = polygon[j]
    if (!pi || !pj) continue

    area += pi.x * pj.y
    area -= pj.x * pi.y
  }

  return Math.abs(area / 2)
}

/**
 * Determine if a clipped polygon is too small to be meaningful
 * @param polygon - Polygon to check
 * @param minArea - Minimum area threshold (default: 0.001)
 * @returns True if polygon is too small
 */
function isPolygonTooSmall(polygon: Polygon | null, minArea = 0.001): boolean {
  if (!polygon || polygon.length < 3) return true
  return calculatePolygonArea(polygon) < minArea
}

/**
 * Main function to handle polygon propagation
 * @param childPolygon - The child polygon to be modified
 * @param parentPolygon - The new parent boundary
 * @returns Result with operation type and modified polygon
 */
function propagatePolygonToParent(childPolygon: Polygon, parentPolygon: Polygon): PropagationResult {
  const relationship = analyzePolygonRelationship(childPolygon, parentPolygon)

  switch (relationship.type) {
    case 'contained':
      return {
        operation: 'none',
        polygon: childPolygon,
        reason: 'child already contained within parent'
      }

    case 'outside':
      return {
        operation: 'delete',
        polygon: null,
        reason: 'child completely outside parent'
      }

    case 'partial':
      if (relationship.data && relationship.data.clippedPolygon) {
        if (isPolygonTooSmall(relationship.data.clippedPolygon)) {
          return {
            operation: 'delete',
            polygon: null,
            reason: 'clipped polygon too small'
          }
        }

        return {
          operation: 'clip',
          polygon: relationship.data.clippedPolygon,
          reason: 'child clipped to parent boundary',
          metadata: {
            originalArea: relationship.data.originalArea!,
            clippedArea: relationship.data.clippedArea!,
            areaReduction: 1 - (relationship.data.clippedArea! / relationship.data.originalArea!)
          }
        }
      }
      return {
        operation: 'error',
        polygon: null,
        reason: 'invalid partial relationship data'
      }

    case 'invalid':
      return {
        operation: 'error',
        polygon: null,
        reason: 'invalid polygon geometry'
      }

    default:
      return {
        operation: 'error',
        polygon: null,
        reason: 'unknown relationship type'
      }
  }
}

export {
  clipPolygon,
  propagatePolygonToParent,
  analyzePolygonRelationship,
  isPolygonCompletelyInside,
  isPolygonCompletelyOutside,
  getLineIntersection,
  calculatePolygonArea,
  isPolygonTooSmall,
  calculatePolygonOverlapPercentage,
  findBestContainingPolygon,
  createBoundingRectangleFromPoints,
  splitPolygonByLine,
  subtractPolygon,
  isPointInPolygon,
  doPolygonsIntersect,
  clipPolylineToPolygon,
  type CutResult,
  type SubtractResult
}

/**
 * Calculate the overlap percentage of a child polygon with a potential parent polygon.
 * Returns the percentage (0-100) of the child's area that is inside the parent.
 *
 * Uses bounding box pre-filter for performance, then Sutherland-Hodgman clipping
 * for precise calculation.
 *
 * @param childPolygon - The polygon to check overlap for
 * @param parentPolygon - The potential parent polygon
 * @returns Overlap percentage (0-100)
 */
function calculatePolygonOverlapPercentage(childPolygon: Polygon, parentPolygon: Polygon): number {
  if (!childPolygon || !parentPolygon || childPolygon.length < 3 || parentPolygon.length < 3) {
    return 0
  }

  const childBbox = getBoundingBoxFromPoints(childPolygon)
  const parentBbox = getBoundingBoxFromPoints(parentPolygon)

  if (!doBoundingBoxesOverlap(childBbox, parentBbox)) {
    return 0
  }

  if (isPolygonCompletelyInside(childPolygon, parentPolygon)) {
    return 100
  }

  if (isPolygonCompletelyOutside(childPolygon, parentPolygon)) {
    return 0
  }

  const clippedPolygon = clipPolygon(childPolygon, parentPolygon)
  if (!clippedPolygon || clippedPolygon.length < 3) {
    return 0
  }

  const childArea = calculatePolygonArea(childPolygon)
  const clippedArea = calculatePolygonArea(clippedPolygon)

  if (childArea <= 0) return 0

  return (clippedArea / childArea) * 100
}

/**
 * Bounding box type for internal use
 */
interface BBox {
  minX: number
  minY: number
  maxX: number
  maxY: number
}

/**
 * Get bounding box from array of points
 */
function getBoundingBoxFromPoints(points: Point[]): BBox {
  if (!points || points.length === 0) {
    return { minX: 0, minY: 0, maxX: 0, maxY: 0 }
  }

  let minX = Infinity, minY = Infinity
  let maxX = -Infinity, maxY = -Infinity

  for (const p of points) {
    if (p.x < minX) minX = p.x
    if (p.y < minY) minY = p.y
    if (p.x > maxX) maxX = p.x
    if (p.y > maxY) maxY = p.y
  }

  return { minX, minY, maxX, maxY }
}

/**
 * Check if two bounding boxes overlap
 */
function doBoundingBoxesOverlap(a: BBox, b: BBox): boolean {
  return !(a.maxX < b.minX || a.minX > b.maxX || a.maxY < b.minY || a.minY > b.maxY)
}

/**
 * Result of parent polygon search
 */
export interface ParentCandidate {
  polygon: Polygon
  id: string
  overlapPercentage: number
  area: number
}

/**
 * Find the best containing polygon from a list of candidates.
 *
 * Selection criteria:
 * 1. Must have overlap >= minOverlapPercentage (default 50%)
 * 2. Among qualifying polygons, pick the one with highest overlap %
 * 3. If tie, pick the smaller one (more specific to the annotation)
 *
 * @param childPoints - Points of the polygon/polyline being created
 * @param candidates - Array of potential parent polygons with their IDs
 * @param minOverlapPercentage - Minimum overlap required (default 50%)
 * @returns Best parent candidate or null if none qualifies
 */
function findBestContainingPolygon(
  childPoints: Point[],
  candidates: Array<{ polygon: Polygon, id: string }>,
  minOverlapPercentage: number = 50
): ParentCandidate | null {
  if (!childPoints || childPoints.length < 2 || !candidates || candidates.length === 0) {
    return null
  }

  const childPolygon = childPoints.length >= 3
    ? childPoints
    : createBoundingRectangleFromPoints(childPoints, 0)

  const qualifyingCandidates: ParentCandidate[] = []

  for (const candidate of candidates) {
    const overlapPercentage = calculatePolygonOverlapPercentage(childPolygon, candidate.polygon)

    if (overlapPercentage >= minOverlapPercentage) {
      qualifyingCandidates.push({
        polygon: candidate.polygon,
        id: candidate.id,
        overlapPercentage,
        area: calculatePolygonArea(candidate.polygon)
      })
    }
  }

  if (qualifyingCandidates.length === 0) {
    return null
  }

  qualifyingCandidates.sort((a, b) => {
    if (Math.abs(a.overlapPercentage - b.overlapPercentage) > 0.1) {
      return b.overlapPercentage - a.overlapPercentage
    }
    return a.area - b.area // Smaller area wins on tie
  })

  return qualifyingCandidates[0] ?? null
}

/**
 * Create an axis-aligned bounding rectangle from a set of points.
 * Useful for generating helper parent shapes.
 *
 * @param points - Points to create bounding rectangle around
 * @param padding - Padding to add around the bounding box (in coordinate units)
 * @returns Array of 4 points forming a rectangle (clockwise from top-left)
 */
function createBoundingRectangleFromPoints(points: Point[], padding: number = 0.01): Point[] {
  if (!points || points.length === 0) {
    return []
  }

  const bbox = getBoundingBoxFromPoints(points)

  return [
    { x: bbox.minX - padding, y: bbox.minY - padding }, // Top-left
    { x: bbox.maxX + padding, y: bbox.minY - padding }, // Top-right
    { x: bbox.maxX + padding, y: bbox.maxY + padding }, // Bottom-right
    { x: bbox.minX - padding, y: bbox.maxY + padding } // Bottom-left
  ]
}

/**
 * Result of a polygon cut/split operation
 */
export interface CutResult {
  /** Whether the cut operation produced any result */
  success: boolean
  /** Resulting polygons after the cut. Empty if no intersection. */
  resultPolygons: Polygon[]
  /**
   * Index of the largest polygon (to inherit original ID).
   * -1 if no valid polygons resulted.
   */
  largestPolygonIndex: number
  /** Error message if operation failed */
  error?: string
}

/**
 * Result of a polygon subtraction operation
 */
export interface SubtractResult {
  /** Whether the subtraction produced any result */
  success: boolean
  /** Resulting polygons after subtraction. May be 0 (fully enclosed), 1 (partial cut), or multiple */
  resultPolygons: Polygon[]
  /**
   * Index of the largest polygon (to inherit original ID).
   * -1 if fully deleted or no valid result.
   */
  largestPolygonIndex: number
  /** Whether the original shape was fully enclosed and should be deleted */
  fullyEnclosed: boolean
  /** Error message if operation failed */
  error?: string
}

/**
 * Convert our Point[] format to martinez-polygon-clipping format.
 * Martinez expects [[[x, y], [x, y], ...]] (array of rings, each ring is array of positions)
 */
function toMartinezPolygon(polygon: Polygon): MartinezPolygon {
  if (!polygon || polygon.length < 3) return []
  const ring: MartinezRing = polygon.map(p => [p.x, p.y] as MartinezPosition)
  if (ring.length > 0 && ring[0] && ring[ring.length - 1]) {
    const first = ring[0]
    const last = ring[ring.length - 1]
    if (first[0] !== last[0] || first[1] !== last[1]) {
      ring.push([...first] as MartinezPosition)
    }
  }
  return [ring]
}

/**
 * Convert martinez-polygon-clipping result back to our Point[] format.
 * Martinez returns MultiPolygon format: [[ring1], [ring2], ...]
 */
function fromMartinezResult(result: MartinezMultiPolygon): Polygon[] {
  if (!result || result.length === 0) return []

  const polygons: Polygon[] = []

  for (const polygon of result) {
    if (!polygon || polygon.length === 0) continue

    const outerRing = polygon[0]
    if (!outerRing || outerRing.length < 3) continue

    let points = outerRing.map(coord => ({ x: coord[0], y: coord[1] }))

    const first = points[0]
    const last = points[points.length - 1]
    if (first && last && Math.abs(first.x - last.x) < 1e-10 && Math.abs(first.y - last.y) < 1e-10) {
      points = points.slice(0, -1)
    }

    if (points.length >= 3) {
      polygons.push(points)
    }
  }

  return polygons
}

/**
 * Find index of the largest polygon by area
 */
function findLargestPolygonIndex(polygons: Polygon[]): number {
  if (polygons.length === 0) return -1
  if (polygons.length === 1) return 0

  let maxArea = -1
  let maxIndex = 0

  for (let i = 0; i < polygons.length; i++) {
    const poly = polygons[i]
    if (!poly) continue
    const area = calculatePolygonArea(poly)
    if (area > maxArea) {
      maxArea = area
      maxIndex = i
    }
  }

  return maxIndex
}

/**
 * Filter out polygons that are too small (below area threshold).
 * @param polygons - Array of polygons to filter
 * @param minArea - Minimum area threshold (default: 0.0001 in world coordinates, ~0.01% of normalized space)
 * @returns Filtered array of polygons
 */
function filterSmallPolygons(polygons: Polygon[], minArea: number = 0.0001): Polygon[] {
  return polygons.filter(p => p && calculatePolygonArea(p) >= minArea)
}

/**
 * Create a cutting polygon from a line by extending it and creating a thin rectangle.
 * This is used for line-based cutting where we need to convert the line to a shape
 * that can be used with boolean operations.
 *
 * @param linePoints - Points defining the cut line (at least 2 points)
 * @param thickness - Thickness of the cutting shape (default: very thin)
 * @returns A polygon representing the cutting line
 */
function createCuttingPolygonFromLine(linePoints: Point[], thickness: number = 0.0001): Polygon {
  if (linePoints.length < 2) return []

  const extendedPoints: Point[] = []
  const offsetPoints: Point[] = []

  for (let i = 0; i < linePoints.length; i++) {
    const current = linePoints[i]
    if (!current) continue

    let normal = { x: 0, y: 1 } // Default normal

    if (i < linePoints.length - 1) {
      const next = linePoints[i + 1]
      if (next) {
        const dx = next.x - current.x
        const dy = next.y - current.y
        const len = Math.sqrt(dx * dx + dy * dy)
        if (len > 1e-10) {
          normal = { x: -dy / len, y: dx / len }
        }
      }
    } else if (i > 0) {
      const prev = linePoints[i - 1]
      if (prev) {
        const dx = current.x - prev.x
        const dy = current.y - prev.y
        const len = Math.sqrt(dx * dx + dy * dy)
        if (len > 1e-10) {
          normal = { x: -dy / len, y: dx / len }
        }
      }
    }

    extendedPoints.push({
      x: current.x + normal.x * thickness,
      y: current.y + normal.y * thickness
    })
    offsetPoints.push({
      x: current.x - normal.x * thickness,
      y: current.y - normal.y * thickness
    })
  }

  return [...extendedPoints, ...offsetPoints.reverse()]
}

/**
 * Split a polygon by a cut line.
 * The cut line divides the polygon into multiple pieces if it intersects the polygon.
 *
 * @param subjectPolygon - The polygon to split
 * @param cutLine - Points defining the cut line (at least 2 points)
 * @param minArea - Minimum area threshold for resulting polygons
 * @returns CutResult with resulting polygons
 */
function splitPolygonByLine(
  subjectPolygon: Polygon,
  cutLine: Point[],
  minArea: number = 0.0001
): CutResult {
  if (!subjectPolygon || subjectPolygon.length < 3) {
    return { success: false, resultPolygons: [], largestPolygonIndex: -1, error: 'Invalid subject polygon' }
  }

  if (!cutLine || cutLine.length < 2) {
    return { success: false, resultPolygons: [], largestPolygonIndex: -1, error: 'Invalid cut line' }
  }

  if (!doesLineIntersectPolygon(cutLine, subjectPolygon)) {
    return {
      success: true,
      resultPolygons: [subjectPolygon],
      largestPolygonIndex: 0
    }
  }

  const result = splitPolygonWithLine(subjectPolygon, cutLine)

  if (result.length === 0) {
    return { success: false, resultPolygons: [], largestPolygonIndex: -1, error: 'Split operation failed' }
  }

  const filteredPolygons = filterSmallPolygons(result, minArea)

  if (filteredPolygons.length === 0) {
    return { success: false, resultPolygons: [], largestPolygonIndex: -1, error: 'All resulting polygons below threshold' }
  }

  return {
    success: true,
    resultPolygons: filteredPolygons,
    largestPolygonIndex: findLargestPolygonIndex(filteredPolygons)
  }
}

/**
 * Check if a line (polyline) intersects a polygon
 */
function doesLineIntersectPolygon(line: Point[], polygon: Polygon): boolean {
  if (!line || line.length < 2 || !polygon || polygon.length < 3) return false

  for (const p of line) {
    if (isPointInPolygon(p, polygon)) return true
  }

  for (let i = 0; i < line.length - 1; i++) {
    const lineStart = line[i]
    const lineEnd = line[i + 1]
    if (!lineStart || !lineEnd) continue

    for (let j = 0; j < polygon.length; j++) {
      const polyStart = polygon[j]
      const polyEnd = polygon[(j + 1) % polygon.length]
      if (!polyStart || !polyEnd) continue

      if (getLineIntersection(
        { start: lineStart, end: lineEnd },
        { start: polyStart, end: polyEnd }
      )) {
        return true
      }
    }
  }

  return false
}

/**
 * Split a polygon using a line as a divider.
 * Uses half-plane clipping on both sides of the line.
 */
function splitPolygonWithLine(polygon: Polygon, line: Point[]): Polygon[] {
  if (line.length < 2) return [polygon]

  const lineStart = line[0]
  const lineEnd = line[line.length - 1]
  if (!lineStart || !lineEnd) return [polygon]

  const bbox = getBoundingBoxFromPoints(polygon)
  const maxDim = Math.max(bbox.maxX - bbox.minX, bbox.maxY - bbox.minY) * 10

  const dx = lineEnd.x - lineStart.x
  const dy = lineEnd.y - lineStart.y
  const len = Math.sqrt(dx * dx + dy * dy)
  if (len < 1e-10) return [polygon]

  const dirX = dx / len
  const dirY = dy / len

  const extStart = {
    x: lineStart.x - dirX * maxDim,
    y: lineStart.y - dirY * maxDim
  }
  const extEnd = {
    x: lineEnd.x + dirX * maxDim,
    y: lineEnd.y + dirY * maxDim
  }

  const normX = -dirY
  const normY = dirX

  const halfPlane1: Polygon = [
    extStart,
    extEnd,
    { x: extEnd.x + normX * maxDim, y: extEnd.y + normY * maxDim },
    { x: extStart.x + normX * maxDim, y: extStart.y + normY * maxDim }
  ]

  const halfPlane2: Polygon = [
    extEnd,
    extStart,
    { x: extStart.x - normX * maxDim, y: extStart.y - normY * maxDim },
    { x: extEnd.x - normX * maxDim, y: extEnd.y - normY * maxDim }
  ]

  const martinezSubject = toMartinezPolygon(polygon)
  const martinezHalf1 = toMartinezPolygon(halfPlane1)
  const martinezHalf2 = toMartinezPolygon(halfPlane2)

  const results: Polygon[] = []

  try {
    const result1 = martinezIntersection(martinezSubject, martinezHalf1)
    if (result1) {
      results.push(...fromMartinezResult(result1))
    }

    const result2 = martinezIntersection(martinezSubject, martinezHalf2)
    if (result2) {
      results.push(...fromMartinezResult(result2))
    }
  } catch {
    return [polygon]
  }

  return results.length > 0 ? results : [polygon]
}

/**
 * Subtract a polygon/rectangle from a subject polygon.
 * Used for cut polygon and cut rectangle operations.
 *
 * @param subjectPolygon - The polygon to cut from
 * @param cutterPolygon - The polygon to subtract (area to remove)
 * @param minArea - Minimum area threshold for resulting polygons
 * @returns SubtractResult with resulting polygons
 */
function subtractPolygon(
  subjectPolygon: Polygon,
  cutterPolygon: Polygon,
  minArea: number = 0.0001
): SubtractResult {
  if (!subjectPolygon || subjectPolygon.length < 3) {
    return {
      success: false,
      resultPolygons: [],
      largestPolygonIndex: -1,
      fullyEnclosed: false,
      error: 'Invalid subject polygon'
    }
  }

  if (!cutterPolygon || cutterPolygon.length < 3) {
    return {
      success: false,
      resultPolygons: [],
      largestPolygonIndex: -1,
      fullyEnclosed: false,
      error: 'Invalid cutter polygon'
    }
  }

  if (isPolygonCompletelyInside(subjectPolygon, cutterPolygon)) {
    return {
      success: true,
      resultPolygons: [],
      largestPolygonIndex: -1,
      fullyEnclosed: true
    }
  }

  if (isPolygonCompletelyOutside(subjectPolygon, cutterPolygon)) {
    return {
      success: true,
      resultPolygons: [subjectPolygon],
      largestPolygonIndex: 0,
      fullyEnclosed: false
    }
  }

  const martinezSubject = toMartinezPolygon(subjectPolygon)
  const martinezCutter = toMartinezPolygon(cutterPolygon)

  try {
    const difference = martinezDiff(martinezSubject, martinezCutter)

    if (!difference || difference.length === 0) {
      return {
        success: true,
        resultPolygons: [],
        largestPolygonIndex: -1,
        fullyEnclosed: true
      }
    }

    const resultPolygons = fromMartinezResult(difference)

    const filteredPolygons = filterSmallPolygons(resultPolygons, minArea)

    if (filteredPolygons.length === 0) {
      return {
        success: true,
        resultPolygons: [],
        largestPolygonIndex: -1,
        fullyEnclosed: true
      }
    }

    return {
      success: true,
      resultPolygons: filteredPolygons,
      largestPolygonIndex: findLargestPolygonIndex(filteredPolygons),
      fullyEnclosed: false
    }
  } catch {
    return {
      success: false,
      resultPolygons: [],
      largestPolygonIndex: -1,
      fullyEnclosed: false,
      error: 'Boolean difference operation failed'
    }
  }
}

/**
 * Check if two polygons intersect (share any area).
 */
function doPolygonsIntersect(polygon1: Polygon, polygon2: Polygon): boolean {
  if (!polygon1 || polygon1.length < 3 || !polygon2 || polygon2.length < 3) {
    return false
  }

  if (isPolygonCompletelyOutside(polygon1, polygon2)) {
    return false
  }

  for (const p of polygon1) {
    if (isPointInPolygon(p, polygon2)) return true
  }
  for (const p of polygon2) {
    if (isPointInPolygon(p, polygon1)) return true
  }

  for (let i = 0; i < polygon1.length; i++) {
    const start1 = polygon1[i]
    const end1 = polygon1[(i + 1) % polygon1.length]
    if (!start1 || !end1) continue

    for (let j = 0; j < polygon2.length; j++) {
      const start2 = polygon2[j]
      const end2 = polygon2[(j + 1) % polygon2.length]
      if (!start2 || !end2) continue

      if (getLineIntersection({ start: start1, end: end1 }, { start: start2, end: end2 })) {
        return true
      }
    }
  }

  return false
}

/**
 * Clip a polyline (baseline) to stay within a polygon boundary.
 * Returns the portion of the polyline that is inside the polygon.
 *
 * @param polyline - The polyline to clip (array of points)
 * @param polygon - The polygon boundary to clip against
 * @returns The clipped polyline points, or empty array if completely outside
 */
function clipPolylineToPolygon(polyline: Point[], polygon: Polygon): Point[] {
  if (!polyline || polyline.length < 2 || !polygon || polygon.length < 3) {
    return []
  }

  const result: Point[] = []

  for (let i = 0; i < polyline.length - 1; i++) {
    const p1 = polyline[i]
    const p2 = polyline[i + 1]
    if (!p1 || !p2) continue

    const p1Inside = isPointInPolygon(p1, polygon)
    const p2Inside = isPointInPolygon(p2, polygon)

    if (p1Inside && p2Inside) {
      if (result.length === 0 || result[result.length - 1]?.x !== p1.x || result[result.length - 1]?.y !== p1.y) {
        result.push({ x: p1.x, y: p1.y })
      }
      if (i === polyline.length - 2) {
        result.push({ x: p2.x, y: p2.y })
      }
    } else if (p1Inside && !p2Inside) {
      if (result.length === 0 || result[result.length - 1]?.x !== p1.x || result[result.length - 1]?.y !== p1.y) {
        result.push({ x: p1.x, y: p1.y })
      }
      const intersection = findPolylinePolygonIntersection(p1, p2, polygon)
      if (intersection) {
        result.push(intersection)
      }
    } else if (!p1Inside && p2Inside) {
      const intersection = findPolylinePolygonIntersection(p1, p2, polygon)
      if (intersection) {
        result.push(intersection)
      }
      if (i === polyline.length - 2) {
        result.push({ x: p2.x, y: p2.y })
      }
    } else {
      const intersections = findAllPolylinePolygonIntersections(p1, p2, polygon)
      if (intersections.length >= 2) {
        intersections.sort((a, b) => {
          const distA = (a.x - p1.x) ** 2 + (a.y - p1.y) ** 2
          const distB = (b.x - p1.x) ** 2 + (b.y - p1.y) ** 2
          return distA - distB
        })
        result.push(intersections[0]!)
        result.push(intersections[1]!)
      }
    }
  }

  return result
}

/**
 * Find the first intersection point between a line segment and a polygon boundary.
 */
function findPolylinePolygonIntersection(p1: Point, p2: Point, polygon: Polygon): Point | null {
  let closestIntersection: Point | null = null
  let minDistance = Infinity

  for (let i = 0; i < polygon.length; i++) {
    const polyStart = polygon[i]
    const polyEnd = polygon[(i + 1) % polygon.length]
    if (!polyStart || !polyEnd) continue

    const intersection = getLineIntersection(
      { start: p1, end: p2 },
      { start: polyStart, end: polyEnd }
    )

    if (intersection) {
      const distance = (intersection.x - p1.x) ** 2 + (intersection.y - p1.y) ** 2
      if (distance < minDistance) {
        minDistance = distance
        closestIntersection = intersection
      }
    }
  }

  return closestIntersection
}

/**
 * Find all intersection points between a line segment and a polygon boundary.
 */
function findAllPolylinePolygonIntersections(p1: Point, p2: Point, polygon: Polygon): Point[] {
  const intersections: Point[] = []

  for (let i = 0; i < polygon.length; i++) {
    const polyStart = polygon[i]
    const polyEnd = polygon[(i + 1) % polygon.length]
    if (!polyStart || !polyEnd) continue

    const intersection = getLineIntersection(
      { start: p1, end: p2 },
      { start: polyStart, end: polyEnd }
    )

    if (intersection) {
      intersections.push(intersection)
    }
  }

  return intersections
}

/**
 * Union multiple polygons into a single polygon using martinez-polygon-clipping.
 * If polygons don't overlap, creates a bounding box containing all points.
 *
 * @param polygons - Array of polygons to union
 * @returns The unioned polygon, or null if operation fails
 */
export function unionPolygons(polygons: Polygon[]): Polygon | null {
  if (!polygons || polygons.length === 0) return null
  if (polygons.length === 1) return polygons[0] ?? null

  const validPolygons = polygons.filter(p => p && p.length >= 3)
  if (validPolygons.length === 0) return null
  if (validPolygons.length === 1) return validPolygons[0] ?? null

  try {
    let currentResult = toMartinezPolygon(validPolygons[0]!)
    for (let i = 1; i < validPolygons.length; i++) {
      const next = toMartinezPolygon(validPolygons[i]!)
      const unionResult = martinezUnion(currentResult, next)
      if (unionResult && unionResult.length > 0) {
        if (unionResult.length === 1) {
          currentResult = unionResult[0]!
        } else {
          return createBoundingBoxFromAllPolygons(validPolygons)
        }
      }
    }

    const resultPolygons = fromMartinezResult([currentResult])
    return resultPolygons[0] ?? null
  } catch (e) {
    console.error('unionPolygons failed:', e)
    return null
  }
}

/**
 * Create a bounding box polygon from multiple polygons.
 */
function createBoundingBoxFromAllPolygons(polygons: Polygon[]): Polygon {
  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity

  for (const poly of polygons) {
    for (const p of poly) {
      if (p.x < minX) minX = p.x
      if (p.y < minY) minY = p.y
      if (p.x > maxX) maxX = p.x
      if (p.y > maxY) maxY = p.y
    }
  }

  return [
    { x: minX, y: minY },
    { x: maxX, y: minY },
    { x: maxX, y: maxY },
    { x: minX, y: maxY }
  ]
}
