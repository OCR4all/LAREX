import { createScopedLogger } from '@/services/editor/logger-service'

const log = createScopedLogger('RenderQueue')

/**
 * Priority levels for render requests
 */
export const RenderPriority = {
  /** Immediate rendering (e.g., user interaction feedback) */
  IMMEDIATE: 0,
  /** High priority (e.g., mouse move during drag) */
  HIGH: 1,
  /** Normal priority (e.g., hover state changes) */
  NORMAL: 2,
  /** Low priority (e.g., background updates) */
  LOW: 3,
  /** Deferred rendering (e.g., batch operations) */
  DEFERRED: 4
} as const

export type RenderPriority = typeof RenderPriority[keyof typeof RenderPriority]

/**
 * Configuration for render queue behavior
 */
export interface RenderQueueConfig {
  /** Maximum frame time in ms (default: 16ms for 60 FPS) */
  maxFrameTime?: number
  /** Debounce delay for low-priority renders in ms (default: 32ms) */
  debounceDelay?: number
  /** Enable performance monitoring (default: false) */
  enableMonitoring?: boolean
  /** Maximum number of consecutive renders before forcing a break (default: 3) */
  maxConsecutiveRenders?: number
}

/**
 * Performance statistics for monitoring
 */
export interface RenderStats {
  /** Total number of renders */
  totalRenders: number
  /** Number of renders in the last second */
  rendersPerSecond: number
  /** Average frame time in ms */
  averageFrameTime: number
  /** Maximum frame time in ms */
  maxFrameTime: number
  /** Number of skipped frames due to budget */
  skippedFrames: number
  /** Number of batched renders */
  batchedRenders: number
}

/**
 * Render request information
 */
interface RenderRequest {
  priority: RenderPriority
  timestamp: number
  reason?: string
}

/**
 * Composable for managing render queue with requestAnimationFrame batching.
 *
 * Features:
 * - Priority-based rendering (immediate vs deferred)
 * - Automatic batching of multiple render requests
 * - Frame time budget management
 * - Debouncing for low-priority renders
 * - Performance monitoring and statistics
 *
 * @param renderFn - The function to call for rendering
 * @param config - Optional configuration
 * @returns Render queue controls and statistics
 *
 * @example
 * ```ts
 * const { scheduleRender, stats } = useRenderQueue(
 *   () => webglRenderer.renderFrame(...),
 *   { enableMonitoring: true }
 * );
 *
 * // Schedule immediate render for user interaction
 * scheduleRender(RenderPriority.IMMEDIATE, 'mouse_down');
 *
 * // Schedule deferred render for state update
 * scheduleRender(RenderPriority.DEFERRED, 'polygon_added');
 * ```
 */
export function useRenderQueue(
  renderFn: () => void,
  config: RenderQueueConfig = {}
) {
  const {
    maxFrameTime = 16,
    debounceDelay = 32,
    enableMonitoring = false,
    maxConsecutiveRenders = 3
  } = config

  const rafId = ref<number | null>(null)
  const pendingRequest = ref<RenderRequest | null>(null)
  const debounceTimeout = ref<number | null>(null)
  const isRendering = ref(false)
  const consecutiveRenders = ref(0)
  const lastFrameTime = ref(0)

  const stats = ref<RenderStats>({
    totalRenders: 0,
    rendersPerSecond: 0,
    averageFrameTime: 0,
    maxFrameTime: 0,
    skippedFrames: 0,
    batchedRenders: 0
  })

  const frameTimes: number[] = []
  const renderTimestamps: number[] = []
  const maxFrameHistory = 60

  /**
   * Update performance statistics
   */
  function updateStats(frameTime: number): void {
    if (!enableMonitoring) return

    stats.value.totalRenders++

    frameTimes.push(frameTime)
    if (frameTimes.length > maxFrameHistory) {
      frameTimes.shift()
    }

    const now = performance.now()
    renderTimestamps.push(now)
    if (renderTimestamps.length > maxFrameHistory) {
      renderTimestamps.shift()
    }

    const sum = frameTimes.reduce((acc, val) => acc + val, 0)
    stats.value.averageFrameTime = sum / frameTimes.length

    if (frameTime > stats.value.maxFrameTime) {
      stats.value.maxFrameTime = frameTime
    }

    const oneSecondAgo = now - 1000
    const recentRenders = renderTimestamps.filter(ts => ts >= oneSecondAgo)
    stats.value.rendersPerSecond = recentRenders.length
  }

  /**
   * Execute the render function with performance monitoring
   */
  function executeRender(reason?: string): void {
    if (isRendering.value) {
      return
    }

    isRendering.value = true
    const startTime = performance.now()

    try {
      renderFn()

      const frameTime = performance.now() - startTime
      lastFrameTime.value = frameTime
      updateStats(frameTime)

      if (enableMonitoring && reason) {
        if (frameTime > maxFrameTime) {
          log.warn(`Slow render (${frameTime.toFixed(2)}ms): ${reason}`)
        }
      }
    } catch (error) {
      log.error('Render error:', error)
    } finally {
      isRendering.value = false
    }
  }

  /**
   * Process pending render request in RAF callback
   */
  function processRenderQueue(): void {
    rafId.value = null

    if (!pendingRequest.value) {
      consecutiveRenders.value = 0
      return
    }

    const request = pendingRequest.value
    pendingRequest.value = null

    if (consecutiveRenders.value >= maxConsecutiveRenders) {
      consecutiveRenders.value = 0
      rafId.value = requestAnimationFrame(() => {
        executeRender(request.reason)
        consecutiveRenders.value = 1
      })
      return
    }

    executeRender(request.reason)
    consecutiveRenders.value++

    setTimeout(() => {
      consecutiveRenders.value = 0
    }, 100)
  }

  /**
   * Schedule a render with the specified priority.
   * Multiple calls within the same frame are automatically batched.
   *
   * @param priority - Priority level for this render
   * @param reason - Optional reason for debugging/monitoring
   */
  function scheduleRender(priority: RenderPriority = RenderPriority.NORMAL, reason?: string): void {
    const now = performance.now()

    if (priority === RenderPriority.IMMEDIATE) {
      if (rafId.value !== null) {
        cancelAnimationFrame(rafId.value)
        rafId.value = null
      }

      if (debounceTimeout.value !== null) {
        clearTimeout(debounceTimeout.value)
        debounceTimeout.value = null
      }

      pendingRequest.value = null
      executeRender(reason)
      return
    }

    if (priority >= RenderPriority.LOW) {
      if (debounceTimeout.value !== null) {
        clearTimeout(debounceTimeout.value)
      }

      debounceTimeout.value = window.setTimeout(() => {
        debounceTimeout.value = null
        scheduleRender(RenderPriority.NORMAL, reason)
      }, debounceDelay)
      return
    }

    const existingRequest = pendingRequest.value

    if (!existingRequest || priority < existingRequest.priority) {
      pendingRequest.value = { priority, timestamp: now, reason }

      if (enableMonitoring && existingRequest) {
        stats.value.batchedRenders++
      }
    }

    if (rafId.value === null) {
      rafId.value = requestAnimationFrame(processRenderQueue)
    }
  }

  /**
   * Force an immediate render, bypassing all queuing
   */
  function forceRender(reason?: string): void {
    scheduleRender(RenderPriority.IMMEDIATE, reason)
  }

  /**
   * Cancel all pending renders
   */
  function cancelPending(): void {
    if (rafId.value !== null) {
      cancelAnimationFrame(rafId.value)
      rafId.value = null
    }

    if (debounceTimeout.value !== null) {
      clearTimeout(debounceTimeout.value)
      debounceTimeout.value = null
    }

    pendingRequest.value = null
  }

  /**
   * Reset statistics
   */
  function resetStats(): void {
    stats.value = {
      totalRenders: 0,
      rendersPerSecond: 0,
      averageFrameTime: 0,
      maxFrameTime: 0,
      skippedFrames: 0,
      batchedRenders: 0
    }
    frameTimes.length = 0
    renderTimestamps.length = 0
  }

  /**
   * Get current queue status for debugging
   */
  function getQueueStatus() {
    return {
      hasPending: pendingRequest.value !== null || rafId.value !== null,
      isRendering: isRendering.value,
      pendingPriority: pendingRequest.value?.priority,
      consecutiveRenders: consecutiveRenders.value,
      lastFrameTime: lastFrameTime.value
    }
  }

  onBeforeUnmount(() => {
    cancelPending()
  })

  return {
    /** Schedule a render with priority */
    scheduleRender,
    /** Force immediate render */
    forceRender,
    /** Cancel all pending renders */
    cancelPending,
    /** Performance statistics (reactive) */
    stats,
    /** Reset statistics */
    resetStats,
    /** Get current queue status */
    getQueueStatus,
    /** Check if currently rendering */
    isRendering
  }
}
