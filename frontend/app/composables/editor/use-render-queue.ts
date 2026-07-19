import { createScopedLogger } from '@/services/editor/logger-service'
import { onBeforeUnmount, ref } from 'vue'

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
  /** Most recent reason supplied by the render caller */
  lastRenderReason: string | null
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
    enableMonitoring = false
  } = config

  let rafId: number | null = null
  let pendingRequest: RenderRequest | null = null
  let debounceTimeout: number | null = null
  const isRendering = ref(false)
  let consecutiveRenders = 0
  let lastRenderTimestamp = 0
  let lastFrameTime = 0

  const stats = ref<RenderStats>({
    totalRenders: 0,
    rendersPerSecond: 0,
    averageFrameTime: 0,
    maxFrameTime: 0,
    skippedFrames: 0,
    batchedRenders: 0,
    lastRenderReason: null
  })

  const frameTimes: number[] = []
  const renderTimestamps: number[] = []
  const maxFrameHistory = 60
  let frameTimeTotal = 0

  /**
   * Update performance statistics
   */
  function updateStats(frameTime: number, reason?: string): void {
    if (!enableMonitoring) return

    frameTimes.push(frameTime)
    frameTimeTotal += frameTime
    if (frameTimes.length > maxFrameHistory) {
      frameTimeTotal -= frameTimes.shift() ?? 0
    }

    const now = performance.now()
    renderTimestamps.push(now)
    const oneSecondAgo = now - 1000
    while (renderTimestamps[0] !== undefined && renderTimestamps[0] < oneSecondAgo) {
      renderTimestamps.shift()
    }

    const previousStats = stats.value
    stats.value = {
      ...previousStats,
      totalRenders: previousStats.totalRenders + 1,
      rendersPerSecond: renderTimestamps.length,
      averageFrameTime: frameTimeTotal / frameTimes.length,
      maxFrameTime: Math.max(previousStats.maxFrameTime, frameTime),
      lastRenderReason: reason ?? null
    }
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
      lastFrameTime = frameTime
      updateStats(frameTime, reason)

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
    rafId = null

    if (!pendingRequest) {
      consecutiveRenders = 0
      return
    }

    const request = pendingRequest
    pendingRequest = null

    executeRender(request.reason)
    const now = performance.now()
    consecutiveRenders = now - lastRenderTimestamp > 100 ? 1 : consecutiveRenders + 1
    lastRenderTimestamp = now
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
      if (rafId !== null) {
        cancelAnimationFrame(rafId)
        rafId = null
      }

      if (debounceTimeout !== null) {
        clearTimeout(debounceTimeout)
        debounceTimeout = null
      }

      pendingRequest = null
      executeRender(reason)
      return
    }

    if (priority >= RenderPriority.LOW) {
      if (debounceTimeout !== null) {
        clearTimeout(debounceTimeout)
      }

      debounceTimeout = window.setTimeout(() => {
        debounceTimeout = null
        scheduleRender(RenderPriority.NORMAL, reason)
      }, debounceDelay)
      return
    }

    const existingRequest = pendingRequest

    if (existingRequest && enableMonitoring) {
      stats.value = {
        ...stats.value,
        batchedRenders: stats.value.batchedRenders + 1
      }
    }

    if (!existingRequest || priority < existingRequest.priority) {
      pendingRequest = { priority, timestamp: now, reason }
    } else if (reason && !existingRequest.reason) {
      existingRequest.reason = reason
    }

    if (rafId === null) {
      rafId = requestAnimationFrame(processRenderQueue)
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
    if (rafId !== null) {
      cancelAnimationFrame(rafId)
      rafId = null
    }

    if (debounceTimeout !== null) {
      clearTimeout(debounceTimeout)
      debounceTimeout = null
    }

    pendingRequest = null
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
      batchedRenders: 0,
      lastRenderReason: null
    }
    frameTimes.length = 0
    renderTimestamps.length = 0
    frameTimeTotal = 0
  }

  /**
   * Get current queue status for debugging
   */
  function getQueueStatus() {
    const recentConsecutiveRenders = performance.now() - lastRenderTimestamp > 100
      ? 0
      : consecutiveRenders
    return {
      hasPending: pendingRequest !== null || rafId !== null,
      isRendering: isRendering.value,
      pendingPriority: pendingRequest?.priority,
      consecutiveRenders: recentConsecutiveRenders,
      lastFrameTime
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
