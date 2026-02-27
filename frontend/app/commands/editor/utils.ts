import type { Point, Vertex } from '@/models/editor'
import { Polygon, Polyline } from '@/models/editor/geometry'

/**
 * Deep clone an object manually to avoid Vue reactivity proxy issues.
 * Do NOT use structuredClone as it fails on reactive proxies and class instances.
 *
 * @param obj - The object to clone
 * @returns A deep clone of the object
 */
export function deepClone<T>(obj: T): T {
  if (obj === null || typeof obj !== 'object') {
    return obj
  }

  if (Array.isArray(obj)) {
    return obj.map(item => deepClone(item)) as unknown as T
  }

  const cloned: any = {}
  for (const key in obj) {
    if (Object.prototype.hasOwnProperty.call(obj, key)) {
      cloned[key] = deepClone((obj as any)[key])
    }
  }
  return cloned as T
}

/**
 * Convert a Point {x, y} to Vertex [x, y] tuple
 */
export function pointToVertex(point: Point): Vertex {
  return [point.x, point.y]
}

/**
 * Convert a Vertex [x, y] tuple to Point {x, y}
 */
export function vertexToPoint(vertex: Vertex): Point {
  return { x: vertex[0], y: vertex[1] }
}

/**
 * Convert an array of Points to Vertices
 */
export function pointsToVertices(points: Point[]): Vertex[] {
  return points.map(pointToVertex)
}

/**
 * Convert an array of Vertices to Points
 */
export function verticesToPoints(vertices: Vertex[]): Point[] {
  return vertices.map(vertexToPoint)
}

/**
 * Convert a point to a plain object.
 *
 * @param point - The point to convert
 * @returns A plain point object
 */
export function toPlainPoint(point: Point): Point {
  return {
    x: point.x,
    y: point.y
  }
}

/**
 * Convert an array of points to plain objects.
 *
 * @param points - The points to convert
 * @returns An array of plain point objects
 */
export function toPlainPoints(points: Point[]): Point[] {
  return points.map(p => toPlainPoint(p))
}

/**
 * Create a Polygon from an array of Points
 */
export function createPolygonFromPoints(points: Point[]): Polygon {
  const vertices = pointsToVertices(points)
  return new Polygon(vertices)
}

/**
 * Create a Polyline from an array of Points
 */
export function createPolylineFromPoints(points: Point[]): Polyline {
  const vertices = pointsToVertices(points)
  return new Polyline(vertices)
}

/**
 * Get Points array from a Polygon's vertices
 */
export function getPointsFromPolygon(polygon: Polygon): Point[] {
  return verticesToPoints(polygon.points)
}

/**
 * Get Points array from a Polyline's vertices
 */
export function getPointsFromPolyline(polyline: Polyline): Point[] {
  return verticesToPoints(polyline.points)
}
