import type { Point } from '@/models/editor'
import type { RenderablePolygon, RenderablePolyline } from '@/types/editor/rendering'

/**
 * Custom equality checker for points
 */
export function arePointsEqual(a: Point | null, b: Point | null): boolean {
  if (a === b) return true
  if (!a || !b) return false
  return a.x === b.x && a.y === b.y
}

/**
 * Custom equality checker for point arrays
 */
export function arePointArraysEqual(a: Point[], b: Point[]): boolean {
  if (a === b) return true
  if (a.length !== b.length) return false

  for (let i = 0; i < a.length; i++) {
    if (!arePointsEqual(a[i] || null, b[i] || null)) {
      return false
    }
  }

  return true
}

/**
 * Custom equality checker for polygons (shallow comparison)
 * Only checks properties that affect rendering
 */
export function arePolygonsShallowEqual(a: RenderablePolygon, b: RenderablePolygon): boolean {
  if (a === b) return true

  return (
    a.id === b.id
    && a.type === b.type
    && a.parentId === b.parentId
    && arePointArraysEqual(a.points, b.points)
  )
}

/**
 * Custom equality checker for polygon arrays
 * Performs shallow comparison on each polygon
 */
export function arePolygonArraysEqual(a: RenderablePolygon[], b: RenderablePolygon[]): boolean {
  if (a === b) return true
  if (a.length !== b.length) return false

  for (let i = 0; i < a.length; i++) {
    const polygonA = a[i]
    const polygonB = b[i]

    if (!polygonA || !polygonB) return false
    if (!arePolygonsShallowEqual(polygonA, polygonB)) {
      return false
    }
  }

  return true
}

/**
 * Custom equality checker for polylines (shallow comparison)
 */
export function arePolylinesShallowEqual(a: RenderablePolyline, b: RenderablePolyline): boolean {
  if (a === b) return true

  return (
    a.id === b.id
    && a.parentId === b.parentId
    && arePointArraysEqual(a.points, b.points)
  )
}

/**
 * Custom equality checker for polyline arrays
 */
export function arePolylineArraysEqual(a: RenderablePolyline[], b: RenderablePolyline[]): boolean {
  if (a === b) return true
  if (a.length !== b.length) return false

  for (let i = 0; i < a.length; i++) {
    const polylineA = a[i]
    const polylineB = b[i]

    if (!polylineA || !polylineB) return false
    if (!arePolylinesShallowEqual(polylineA, polylineB)) {
      return false
    }
  }

  return true
}

/**
 * Create a shallow copy of a polygon for change detection
 */
export function shallowCopyPolygon(polygon: RenderablePolygon): RenderablePolygon {
  return {
    ...polygon,
    points: [...polygon.points]
  }
}

/**
 * Create a shallow copy of polygon array
 */
export function shallowCopyPolygonArray(polygons: RenderablePolygon[]): RenderablePolygon[] {
  return polygons.map(shallowCopyPolygon)
}

/**
 * Create a shallow copy of a polyline for change detection
 */
export function shallowCopyPolyline(polyline: RenderablePolyline): RenderablePolyline {
  return {
    ...polyline,
    points: [...polyline.points]
  }
}

/**
 * Create a shallow copy of polyline array
 */
export function shallowCopyPolylineArray(polylines: RenderablePolyline[]): RenderablePolyline[] {
  return polylines.map(shallowCopyPolyline)
}

/**
 * Optimized watch for polygon arrays that uses custom equality checking
 * to prevent unnecessary re-renders from deep watchers.
 *
 * @param source - Watch source (polygon array)
 * @param callback - Callback to execute on change
 * @param options - Watch options (deep is handled internally)
 *
 * @example
 * ```ts
 * watchPolygonArray(
 *   () => polygons,
 *   (newVal, oldVal) => {
 *     scheduleRender(RenderPriority.NORMAL, 'polygons_changed');
 *   }
 * );
 * ```
 */
export function watchPolygonArray(
  source: WatchSource<RenderablePolygon[]>,
  callback: WatchCallback<RenderablePolygon[], RenderablePolygon[]>,
  options?: Omit<WatchOptions, 'deep'>
) {
  let previousCopy: RenderablePolygon[] | null = null

  return watch(
    source,
    (newVal, oldVal) => {
      const newCopy = shallowCopyPolygonArray(newVal)

      if (previousCopy && arePolygonArraysEqual(previousCopy, newCopy)) {
        return
      }

      const previousVal = previousCopy || (oldVal ?? [])
      previousCopy = newCopy
      callback(newVal, previousVal, () => {})
    },
    { ...options, deep: true }
  )
}

/**
 * Optimized watch for polyline arrays with custom equality checking
 */
export function watchPolylineArray(
  source: WatchSource<RenderablePolyline[]>,
  callback: WatchCallback<RenderablePolyline[], RenderablePolyline[]>,
  options?: Omit<WatchOptions, 'deep'>
) {
  let previousCopy: RenderablePolyline[] | null = null

  return watch(
    source,
    (newVal, oldVal) => {
      const newCopy = shallowCopyPolylineArray(newVal)

      if (previousCopy && arePolylineArraysEqual(previousCopy, newCopy)) {
        return
      }

      const previousVal = previousCopy || (oldVal ?? [])
      previousCopy = newCopy
      callback(newVal, previousVal, () => {})
    },
    { ...options, deep: true }
  )
}

/**
 * Optimized watch for reactive objects with custom equality checking.
 * Useful for hover states, drag info, etc.
 *
 * @param source - Watch source
 * @param callback - Callback to execute on change
 * @param equalityFn - Custom equality function
 * @param options - Watch options
 *
 * @example
 * ```ts
 * watchWithEquality(
 *   () => draggedNodeInfo,
 *   () => scheduleRender(RenderPriority.HIGH, 'drag_state_changed'),
 *   (a, b) => a.isDragging === b.isDragging && a.nodeIndex === b.nodeIndex,
 *   { deep: true }
 * );
 * ```
 */
export function watchWithEquality<T>(
  source: WatchSource<T>,
  callback: WatchCallback<T, T>,
  equalityFn: (a: T, b: T) => boolean,
  options?: WatchOptions
) {
  let previousValue: T | null = null

  return watch(
    source,
    (newVal, oldVal) => {
      if (previousValue !== null && equalityFn(previousValue, newVal)) {
        return
      }

      const previousVal = (previousValue ?? oldVal) as T
      previousValue = newVal
      callback(newVal, previousVal, () => {})
    },
    options
  )
}

/**
 * Batch multiple watch sources into a single render trigger.
 * Useful for reducing render calls when multiple related states change.
 *
 * @param sources - Array of watch sources
 * @param callback - Callback to execute when any source changes
 * @param debounceMs - Optional debounce delay (0 = use nextTick)
 *
 * @example
 * ```ts
 * batchWatch(
 *   [
 *     () => hoveredPolygonIndex.value,
 *     () => hoveredNodeIndex.value,
 *     () => hoveredEdgeInfo.polygonIndex
 *   ],
 *   () => scheduleRender(RenderPriority.NORMAL, 'hover_state_changed')
 * );
 * ```
 */
export function batchWatch<T extends readonly unknown[]>(
  sources: [...T],
  callback: () => void,
  debounceMs = 0
): () => void {
  let timeoutId: number | null = null
  let pendingChanges = false

  const triggerCallback = () => {
    if (pendingChanges) {
      callback()
      pendingChanges = false
    }
  }

  const scheduleCallback = () => {
    pendingChanges = true

    if (timeoutId !== null) {
      clearTimeout(timeoutId)
    }

    if (debounceMs > 0) {
      timeoutId = window.setTimeout(triggerCallback, debounceMs)
    } else {
      Promise.resolve().then(triggerCallback)
    }
  }

  const stopHandles = sources.map(source =>
    watch(source as WatchSource, scheduleCallback, { deep: true })
  )

  return () => {
    stopHandles.forEach(stop => stop())
    if (timeoutId !== null) {
      clearTimeout(timeoutId)
    }
  }
}

/**
 * Throttled watch that limits callback execution frequency.
 * Useful for expensive operations like rendering during mouse move.
 *
 * @param source - Watch source
 * @param callback - Callback to execute
 * @param throttleMs - Minimum time between callback executions
 * @param options - Watch options
 *
 * @example
 * ```ts
 * watchThrottled(
 *   () => mousePosition.value,
 *   () => scheduleRender(RenderPriority.HIGH, 'mouse_move'),
 *   16 // Max 60 FPS
 * );
 * ```
 */
export function watchThrottled<T>(
  source: WatchSource<T>,
  callback: WatchCallback<T, T>,
  throttleMs: number,
  options?: WatchOptions
) {
  let lastCallTime = 0
  let timeoutId: number | null = null
  let pendingArgs: { newVal: T, oldVal: T } | null = null

  const executeCallback = () => {
    if (pendingArgs) {
      lastCallTime = Date.now()
      callback(pendingArgs.newVal, pendingArgs.oldVal, () => {})
      pendingArgs = null
      timeoutId = null
    }
  }

  return watch(
    source,
    (newVal, oldVal) => {
      const now = Date.now()
      const timeSinceLastCall = now - lastCallTime

      pendingArgs = { newVal, oldVal: oldVal as T }

      if (timeSinceLastCall >= throttleMs) {
        executeCallback()
      } else {
        if (timeoutId !== null) {
          clearTimeout(timeoutId)
        }
        timeoutId = window.setTimeout(executeCallback, throttleMs - timeSinceLastCall)
      }
    },
    options
  )
}
