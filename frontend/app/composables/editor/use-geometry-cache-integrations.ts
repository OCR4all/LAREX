/**
 * Composable for integrating geometry cache invalidation with the command system
 */

export interface GeometryCacheManager {
  invalidate: (polygonId: string) => void
  invalidateMultiple: (polygonIds: string[]) => void
  clear: () => void
  prune: (activePolygonIds: Set<string>) => void
  getStats: () => any
}

const cacheManagers = new Map<string, GeometryCacheManager>()

/**
 * Register the geometry cache manager globally
 * This should be called once when the WebGL renderer is initialized
 */
export function registerGeometryCacheManager(canvasId: string, manager: GeometryCacheManager) {
  cacheManagers.set(canvasId, manager)
}

export function unregisterGeometryCacheManager(canvasId: string) {
  cacheManagers.delete(canvasId)
}

/**
 * Get the global cache manager (if available)
 */
export function getGeometryCacheManager(canvasId: string): GeometryCacheManager | null {
  return cacheManagers.get(canvasId) ?? null
}

/**
 * Invalidate geometry for a single polygon
 * Safe to call even if cache is not initialized
 */
export function invalidatePolygonGeometry(canvasId: string, polygonId: string) {
  cacheManagers.get(canvasId)?.invalidate(polygonId)
}

/**
 * Invalidate geometry for multiple polygons
 * Safe to call even if cache is not initialized
 */
export function invalidateMultiplePolygonGeometry(canvasId: string, polygonIds: string[]) {
  cacheManagers.get(canvasId)?.invalidateMultiple(polygonIds)
}

/**
 * Clear all cached geometry
 * Safe to call even if cache is not initialized
 */
export function clearGeometryCache(canvasId: string) {
  cacheManagers.get(canvasId)?.clear()
}

/**
 * Prune stale entries from the cache
 * Safe to call even if cache is not initialized
 */
export function pruneGeometryCache(canvasId: string, activePolygonIds: Set<string>) {
  cacheManagers.get(canvasId)?.prune(activePolygonIds)
}

/**
 * Get cache statistics
 * Returns null if cache is not initialized
 */
export function getGeometryCacheStats(canvasId: string) {
  return cacheManagers.get(canvasId)?.getStats() ?? null
}
