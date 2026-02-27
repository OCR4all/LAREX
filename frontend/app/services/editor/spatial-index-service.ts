import RBush from 'rbush'
import type { Point } from '@/models/editor'

type IndexablePolygon = { id: string, points: Point[] }
type IndexablePolyline = { id: string, points: Point[] }

/**
 * Bounding box for spatial indexing
 */
interface BBox {
  minX: number
  minY: number
  maxX: number
  maxY: number
}

/**
 * Indexed polygon item with bounding box
 */
interface IndexedPolygon extends BBox {
  index: number // Index in the polygons array
  id: string // Polygon ID
}

/**
 * Indexed polyline item with bounding box
 */
interface IndexedPolyline extends BBox {
  index: number // Index in the polylines array
  id: string // Polyline ID
}

/**
 * Batch operation for incremental updates
 */
export interface SpatialIndexBatchUpdate {
  polygonsToAdd?: IndexablePolygon[]
  polygonsToRemove?: string[]
  polygonsToUpdate?: IndexablePolygon[]
  polylinesToAdd?: IndexablePolyline[]
  polylinesToRemove?: string[]
  polylinesToUpdate?: IndexablePolyline[]
}

/**
 * Spatial index for polygons and polylines based on RBush.
 * Supports full rebuilds and incremental updates.
 */
export class SpatialIndexService {
  private polygonIndex: RBush<IndexedPolygon>
  private polylineIndex: RBush<IndexedPolyline>

  private polygonById: Map<string, IndexedPolygon> = new Map()
  private polylineById: Map<string, IndexedPolyline> = new Map()

  private _isDirty = false

  constructor() {
    this.polygonIndex = new RBush<IndexedPolygon>()
    this.polylineIndex = new RBush<IndexedPolyline>()
  }

  /**
   * Check if the index needs rebuilding
   */
  get isDirty(): boolean {
    return this._isDirty
  }

  /**
   * Calculate bounding box for a polygon
   */
  private getPolygonBBox(points: Point[]): BBox {
    const first = points[0]
    if (!first) {
      return { minX: 0, minY: 0, maxX: 0, maxY: 0 }
    }

    let minX = first.x
    let minY = first.y
    let maxX = first.x
    let maxY = first.y

    for (let i = 1; i < points.length; i++) {
      const p = points[i]
      if (!p) continue
      if (p.x < minX) minX = p.x
      if (p.y < minY) minY = p.y
      if (p.x > maxX) maxX = p.x
      if (p.y > maxY) maxY = p.y
    }

    return { minX, minY, maxX, maxY }
  }

  /**
   * Calculate bounding box for a polyline (with optional padding for hit detection)
   */
  private getPolylineBBox(points: Point[], padding: number = 0.02): BBox {
    const first = points[0]
    if (!first) {
      return { minX: 0, minY: 0, maxX: 0, maxY: 0 }
    }

    let minX = first.x
    let minY = first.y
    let maxX = first.x
    let maxY = first.y

    for (let i = 1; i < points.length; i++) {
      const p = points[i]
      if (!p) continue
      if (p.x < minX) minX = p.x
      if (p.y < minY) minY = p.y
      if (p.x > maxX) maxX = p.x
      if (p.y > maxY) maxY = p.y
    }

    return {
      minX: minX - padding,
      minY: minY - padding,
      maxX: maxX + padding,
      maxY: maxY + padding
    }
  }

  /**
   * Create a point bounding box for spatial queries
   */
  private getPointBBox(point: Point, radius: number = 0): BBox {
    return {
      minX: point.x - radius,
      minY: point.y - radius,
      maxX: point.x + radius,
      maxY: point.y + radius
    }
  }

  /**
   * Rebuild the entire polygon index from a polygons array
   */
  rebuildPolygonIndex(polygons: IndexablePolygon[]): void {
    this.polygonIndex.clear()
    this.polygonById.clear()

    const items: IndexedPolygon[] = polygons.map((polygon, index) => {
      const bbox = this.getPolygonBBox(polygon.points)
      const item: IndexedPolygon = {
        ...bbox,
        index,
        id: polygon.id
      }
      this.polygonById.set(polygon.id, item)
      return item
    })

    this.polygonIndex.load(items)
    this._isDirty = false
  }

  /**
   * Insert a single polygon into the index
   */
  insertPolygon(polygon: IndexablePolygon, index: number): void {
    const bbox = this.getPolygonBBox(polygon.points)
    const item: IndexedPolygon = {
      ...bbox,
      index,
      id: polygon.id
    }
    this.polygonIndex.insert(item)
    this.polygonById.set(polygon.id, item)
  }

  /**
   * Remove a polygon from the index by ID
   */
  removePolygon(polygonId: string): boolean {
    const item = this.polygonById.get(polygonId)
    if (item) {
      this.polygonIndex.remove(item)
      this.polygonById.delete(polygonId)
      return true
    }
    return false
  }

  /**
   * Update a polygon in the index
   */
  updatePolygon(polygon: IndexablePolygon, index: number): void {
    this.removePolygon(polygon.id)
    this.insertPolygon(polygon, index)
  }

  /**
   * Check if a polygon exists in the index
   */
  hasPolygon(polygonId: string): boolean {
    return this.polygonById.has(polygonId)
  }

  /**
   * Query polygons at a point.
   * Returns array of polygon indices that might contain the point.
   * Caller should still perform exact point-in-polygon tests.
   */
  queryPolygonsAtPoint(point: Point): number[] {
    const bbox = this.getPointBBox(point)
    const results = this.polygonIndex.search(bbox)

    return results
      .map(item => item.index)
      .sort((a, b) => b - a)
  }

  /**
   * Query polygons within a bounding box
   */
  queryPolygonsInBBox(bbox: BBox): number[] {
    const results = this.polygonIndex.search(bbox)
    return results
      .map(item => item.index)
      .sort((a, b) => b - a)
  }

  /**
   * Rebuild the entire polyline index from a polylines array
   */
  rebuildPolylineIndex(polylines: IndexablePolyline[], threshold: number = 0.02): void {
    this.polylineIndex.clear()
    this.polylineById.clear()

    const items: IndexedPolyline[] = polylines.map((polyline, index) => {
      const bbox = this.getPolylineBBox(polyline.points, threshold)
      const item: IndexedPolyline = {
        ...bbox,
        index,
        id: polyline.id
      }
      this.polylineById.set(polyline.id, item)
      return item
    })

    this.polylineIndex.load(items)
    this._isDirty = false
  }

  /**
   * Insert a single polyline into the index
   */
  insertPolyline(polyline: IndexablePolyline, index: number, threshold: number = 0.02): void {
    const bbox = this.getPolylineBBox(polyline.points, threshold)
    const item: IndexedPolyline = {
      ...bbox,
      index,
      id: polyline.id
    }
    this.polylineIndex.insert(item)
    this.polylineById.set(polyline.id, item)
  }

  /**
   * Remove a polyline from the index by ID
   */
  removePolyline(polylineId: string): boolean {
    const item = this.polylineById.get(polylineId)
    if (item) {
      this.polylineIndex.remove(item)
      this.polylineById.delete(polylineId)
      return true
    }
    return false
  }

  /**
   * Update a polyline in the index
   */
  updatePolyline(polyline: IndexablePolyline, index: number, threshold: number = 0.02): void {
    this.removePolyline(polyline.id)
    this.insertPolyline(polyline, index, threshold)
  }

  /**
   * Check if a polyline exists in the index
   */
  hasPolyline(polylineId: string): boolean {
    return this.polylineById.has(polylineId)
  }

  /**
   * Query polylines near a point.
   * Returns array of polyline indices that might be near the point.
   * Caller should still perform exact distance tests.
   */
  queryPolylinesAtPoint(point: Point, threshold: number = 0.02): number[] {
    const bbox = this.getPointBBox(point, threshold)
    const results = this.polylineIndex.search(bbox)

    return results
      .map(item => item.index)
      .sort((a, b) => b - a)
  }

  /**
   * Query polylines within a bounding box
   */
  queryPolylinesInBBox(bbox: BBox, _threshold: number = 0.02): number[] {
    const results = this.polylineIndex.search(bbox)
    return results
      .map(item => item.index)
      .sort((a, b) => b - a)
  }

  /**
   * Apply a batch of updates incrementally.
   * More efficient than multiple individual operations.
   */
  applyBatchUpdate(batch: SpatialIndexBatchUpdate): void {
    if (batch.polygonsToRemove) {
      for (const id of batch.polygonsToRemove) {
        this.removePolygon(id)
      }
    }

    if (batch.polylinesToRemove) {
      for (const id of batch.polylinesToRemove) {
        this.removePolyline(id)
      }
    }

    if (batch.polygonsToUpdate) {
      for (const polygon of batch.polygonsToUpdate) {
        const existing = this.polygonById.get(polygon.id)
        const index = existing?.index ?? this.polygonById.size
        this.updatePolygon(polygon, index)
      }
    }

    if (batch.polylinesToUpdate) {
      for (const polyline of batch.polylinesToUpdate) {
        const existing = this.polylineById.get(polyline.id)
        const index = existing?.index ?? this.polylineById.size
        this.updatePolyline(polyline, index)
      }
    }

    if (batch.polygonsToAdd) {
      for (const polygon of batch.polygonsToAdd) {
        const index = this.polygonById.size
        this.insertPolygon(polygon, index)
      }
    }

    if (batch.polylinesToAdd) {
      for (const polyline of batch.polylinesToAdd) {
        const index = this.polylineById.size
        this.insertPolyline(polyline, index)
      }
    }
  }

  /**
   * Clear all indices
   */
  clear(): void {
    this.polygonIndex.clear()
    this.polylineIndex.clear()
    this.polygonById.clear()
    this.polylineById.clear()
    this._isDirty = false
  }

  /**
   * Get statistics about the indices
   */
  getStats(): {
    polygonCount: number
    polylineCount: number
  } {
    return {
      polygonCount: this.polygonById.size,
      polylineCount: this.polylineById.size
    }
  }

  /**
   * Check if indices are empty
   */
  isEmpty(): boolean {
    return this.polygonById.size === 0 && this.polylineById.size === 0
  }

  /**
   * Get all polygon IDs currently indexed
   */
  getPolygonIds(): string[] {
    return Array.from(this.polygonById.keys())
  }

  /**
   * Get all polyline IDs currently indexed
   */
  getPolylineIds(): string[] {
    return Array.from(this.polylineById.keys())
  }
}

/**
 * Create a new spatial index service instance
 */
export function createSpatialIndex(): SpatialIndexService {
  return new SpatialIndexService()
}
