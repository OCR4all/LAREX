/**
 * A simple LRU (Least Recently Used) cache implementation.
 * Automatically evicts the least recently accessed items when the cache exceeds maxSize.
 *
 * @template K - Type of cache keys
 * @template V - Type of cache values
 */
export class LRUCache<K, V> {
  private cache: Map<K, V>
  private readonly maxSize: number

  constructor(maxSize: number = 50) {
    this.cache = new Map()
    this.maxSize = maxSize
  }

  /**
   * Get a value from the cache. Moves the item to most-recently-used position.
   * @param key - The key to look up
   * @returns The value if found, undefined otherwise
   */
  get(key: K): V | undefined {
    if (!this.cache.has(key)) {
      return undefined
    }

    const value = this.cache.get(key)!
    this.cache.delete(key)
    this.cache.set(key, value)
    return value
  }

  /**
   * Check if a key exists in the cache without affecting LRU order.
   * @param key - The key to check
   * @returns True if the key exists
   */
  has(key: K): boolean {
    return this.cache.has(key)
  }

  /**
   * Set a value in the cache. Evicts LRU items if cache is full.
   * @param key - The key to set
   * @param value - The value to store
   */
  set(key: K, value: V): void {
    if (this.cache.has(key)) {
      this.cache.delete(key)
    }

    while (this.cache.size >= this.maxSize) {
      const oldestKey = this.cache.keys().next().value
      if (oldestKey !== undefined) {
        this.cache.delete(oldestKey)
      }
    }

    this.cache.set(key, value)
  }

  /**
   * Delete a specific key from the cache.
   * @param key - The key to delete
   * @returns True if the key was found and deleted
   */
  delete(key: K): boolean {
    return this.cache.delete(key)
  }

  /**
   * Delete all keys matching a predicate.
   * @param predicate - Function that returns true for keys to delete
   * @returns Number of entries deleted
   */
  deleteWhere(predicate: (key: K) => boolean): number {
    let deleted = 0
    for (const key of this.cache.keys()) {
      if (predicate(key)) {
        this.cache.delete(key)
        deleted++
      }
    }
    return deleted
  }

  /**
   * Clear all entries from the cache.
   */
  clear(): void {
    this.cache.clear()
  }

  /**
   * Get the current number of entries in the cache.
   */
  get size(): number {
    return this.cache.size
  }

  /**
   * Get all keys in the cache (oldest to newest).
   */
  keys(): IterableIterator<K> {
    return this.cache.keys()
  }

  /**
   * Get all values in the cache (oldest to newest).
   */
  values(): IterableIterator<V> {
    return this.cache.values()
  }

  /**
   * Get all entries in the cache (oldest to newest).
   */
  entries(): IterableIterator<[K, V]> {
    return this.cache.entries()
  }
}
