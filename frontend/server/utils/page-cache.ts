import { websocketUtils } from './websocket'

interface CacheEntry<T> {
  data: T
  timestamp: number
}

/**
 * Simple LRU Cache implementation for server-side use.
 * Automatically evicts oldest entries when maxSize is exceeded.
 */
class SimpleLRUCache<K, V> {
  private cache: Map<K, V>
  private readonly maxSize: number
  private readonly ttl: number

  constructor(maxSize: number, ttlMs: number) {
    this.cache = new Map()
    this.maxSize = maxSize
    this.ttl = ttlMs
  }

  get(key: K): V | undefined {
    const value = this.cache.get(key)
    if (value === undefined) return undefined

    const entry = value as any
    if (entry.timestamp && Date.now() - entry.timestamp > this.ttl) {
      this.cache.delete(key)
      return undefined
    }

    this.cache.delete(key)
    this.cache.set(key, value)
    return value
  }

  set(key: K, value: V): void {
    if (this.cache.has(key)) {
      this.cache.delete(key)
    }

    while (this.cache.size >= this.maxSize) {
      const oldest = this.cache.keys().next().value
      if (oldest !== undefined) {
        this.cache.delete(oldest)
      }
    }

    this.cache.set(key, value)
  }

  delete(key: K): boolean {
    return this.cache.delete(key)
  }

  keys(): IterableIterator<K> {
    return this.cache.keys()
  }

  clear(): void {
    this.cache.clear()
  }

  get size(): number {
    return this.cache.size
  }
}

const pageListCache = new SimpleLRUCache<string, CacheEntry<any>>(100, 5 * 60 * 1000)

const pageMetadataCache = new SimpleLRUCache<string, CacheEntry<any>>(500, 5 * 60 * 1000)

export const pageCacheUtils = {
  /**
   * Get cached page list for a project
   */
  getPageList(projectId: string): any | undefined {
    const entry = pageListCache.get(projectId)
    return entry?.data
  },

  /**
   * Set cached page list for a project
   */
  setPageList(projectId: string, data: any): void {
    pageListCache.set(projectId, {
      data,
      timestamp: Date.now()
    })
  },

  /**
   * Get cached page metadata
   */
  getPageMetadata(projectId: string, pageId: string): any | undefined {
    const key = `${projectId}:${pageId}`
    const entry = pageMetadataCache.get(key)
    return entry?.data
  },

  /**
   * Set cached page metadata
   */
  setPageMetadata(projectId: string, pageId: string, data: any): void {
    const key = `${projectId}:${pageId}`
    pageMetadataCache.set(key, {
      data,
      timestamp: Date.now()
    })
  },

  /**
   * Invalidate page list cache for a project and notify clients via WebSocket
   */
  invalidatePageList(projectId: string): void {
    pageListCache.delete(projectId)
    console.log(`[PageCache] Invalidated page list cache for project ${projectId}`)

    websocketUtils.broadcast({
      type: 'CACHE_INVALIDATION',
      payload: {
        cacheType: 'PAGE_LIST',
        projectId,
        timestamp: new Date().toISOString()
      }
    })
  },

  /**
   * Invalidate page metadata cache and notify clients
   */
  invalidatePageMetadata(projectId: string, pageId: string): void {
    const key = `${projectId}:${pageId}`
    pageMetadataCache.delete(key)
    console.log(`[PageCache] Invalidated page metadata cache for page ${pageId}`)

    websocketUtils.broadcast({
      type: 'CACHE_INVALIDATION',
      payload: {
        cacheType: 'PAGE_METADATA',
        projectId,
        pageId,
        timestamp: new Date().toISOString()
      }
    })
  },

  /**
   * Invalidate all cache entries for a project
   */
  invalidateProject(projectId: string): void {
    pageListCache.delete(projectId)

    for (const key of pageMetadataCache.keys()) {
      if (key.startsWith(`${projectId}:`)) {
        pageMetadataCache.delete(key)
      }
    }

    console.log(`[PageCache] Invalidated all cache for project ${projectId}`)

    websocketUtils.broadcast({
      type: 'CACHE_INVALIDATION',
      payload: {
        cacheType: 'PROJECT',
        projectId,
        timestamp: new Date().toISOString()
      }
    })
  },

  /**
   * Clear all caches
   */
  clearAll(): void {
    pageListCache.clear()
    pageMetadataCache.clear()
    console.log('[PageCache] Cleared all caches')
  },

  /**
   * Get cache statistics
   */
  getStats() {
    return {
      pageListCacheSize: pageListCache.size,
      pageMetadataCacheSize: pageMetadataCache.size
    }
  }
}
