/**
 * Cache for triangulated polygon geometry.
 * Entries are invalidated when polygon points change.
 */

import { WEBGL_CACHE, WEBGL_GEOMETRY } from '@/webgl/editor/webgl-constants'

export interface CachedGeometry {
  /** Triangulated indices */
  triangleIndices: number[]
  /** Version/timestamp when this geometry was cached */
  version: number
  /** Points hash used for cache validation */
  pointsHash: number
}

export interface PolygonIdentifier {
  /** Polygon ID (stable identifier) */
  id: string
  /** Index in the polygons array (can change) */
  index?: number
}

export class GeometryCache {
  private cache: Map<string, CachedGeometry>
  private triangulationFunction: (points: any[]) => number[]
  private globalVersion: number

  constructor(triangulationFunction: (points: any[]) => number[]) {
    this.cache = new Map()
    this.triangulationFunction = triangulationFunction
    this.globalVersion = 0
  }

  /**
     * Get or compute triangulated geometry for a polygon.
     * @param polygonId - Unique identifier for the polygon
     * @param points - Polygon points
     * @returns Cached or newly computed triangle indices
     */
  getTriangulation(polygonId: string, points: any[]): number[] {
    if (points.length < WEBGL_GEOMETRY.MIN_POLYGON_POINTS) {
      return []
    }

    const pointsHash = this.hashPoints(points)

    const cached = this.cache.get(polygonId)
    if (cached && cached.pointsHash === pointsHash) {
      return cached.triangleIndices
    }

    const triangleIndices = this.triangulationFunction(points)

    this.cache.set(polygonId, {
      triangleIndices,
      version: this.globalVersion,
      pointsHash
    })

    return triangleIndices
  }

  /**
     * Invalidate cached geometry for one polygon.
     * @param polygonId - ID of the polygon to invalidate
     */
  invalidate(polygonId: string): void {
    this.cache.delete(polygonId)
  }

  /**
     * Invalidate cached geometry for multiple polygons.
     * @param polygonIds - Array of polygon IDs to invalidate
     */
  invalidateMultiple(polygonIds: string[]): void {
    for (const id of polygonIds) {
      this.cache.delete(id)
    }
  }

  /**
     * Mark a polygon as dirty and increment the version counter.
     * @param polygonId - ID of the modified polygon
     */
  markDirty(polygonId: string): void {
    this.invalidate(polygonId)
    this.globalVersion++
  }

  /**
     * Clear all cached geometry
     */
  clear(): void {
    this.cache.clear()
    this.globalVersion = 0
  }

  /**
     * Return cache metrics.
     */
  getStats(): { size: number, version: number, hitRate?: number } {
    return {
      size: this.cache.size,
      version: this.globalVersion
    }
  }

  /**
     * Precompute geometry for multiple polygons.
     * @param polygons - Array of polygons to pre-cache
     */
  preCachePolygons(polygons: any[]): void {
    for (const polygon of polygons) {
      if (polygon.id && polygon.points && polygon.points.length >= WEBGL_GEOMETRY.MIN_POLYGON_POINTS) {
        this.getTriangulation(polygon.id, polygon.points)
      }
    }
  }

  /**
     * Remove stale entries (polygons that no longer exist)
     * @param activePolygonIds - Set of currently active polygon IDs
     */
  pruneStaleEntries(activePolygonIds: Set<string>): void {
    const keysToDelete: string[] = []

    for (const [key] of this.cache) {
      if (!activePolygonIds.has(key)) {
        keysToDelete.push(key)
      }
    }

    for (const key of keysToDelete) {
      this.cache.delete(key)
    }
  }

  /**
     * Hash polygon points for cache comparison.
     * @param points - Array of points to hash
     * @returns Hash string
     */
  private hashPoints(points: any[]): number {
    let hash = points.length
    for (const p of points) {
      hash ^= Math.floor(p.x * WEBGL_CACHE.HASH_COORD_SCALE)
      hash *= WEBGL_CACHE.FNV_PRIME
      hash ^= Math.floor(p.y * WEBGL_CACHE.HASH_COORD_SCALE)
      hash *= WEBGL_CACHE.FNV_PRIME
    }
    return hash >>> WEBGL_CACHE.UNSIGNED_SHIFT // Convert to unsigned 32-bit
  }

  /**
     * Check whether valid cached geometry exists for a polygon.
     * @param polygonId - ID of the polygon
     * @param points - Current points (for validation)
     * @returns True if valid cached geometry exists
     */
  isCached(polygonId: string, points: any[]): boolean {
    const cached = this.cache.get(polygonId)
    if (!cached) return false

    const currentHash = this.hashPoints(points)
    return cached.pointsHash === currentHash
  }
}

/** Shared geometry cache instance. */
let globalGeometryCache: GeometryCache | null = null

export function createGeometryCache(triangulationFunction: (points: any[]) => number[]): GeometryCache {
  globalGeometryCache = new GeometryCache(triangulationFunction)
  return globalGeometryCache
}

export function getGeometryCache(): GeometryCache | null {
  return globalGeometryCache
}
