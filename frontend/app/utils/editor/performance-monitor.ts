import { createScopedLogger } from '@/services/editor/logger-service'

const log = createScopedLogger('PerformanceMonitor')

/**
 * Performance metrics for a single frame
 */
export interface FrameMetrics {
  /** Frame timestamp */
  timestamp: number
  /** Frame duration in ms */
  duration: number
  /** Frame number (sequential) */
  frameNumber: number
  /** Render reason/trigger */
  reason?: string
}

/**
 * Aggregated performance statistics
 */
export interface PerformanceMetrics {
  /** Current FPS (rolling average) */
  fps: number
  /** Average frame time in ms */
  averageFrameTime: number
  /** Minimum frame time in ms */
  minFrameTime: number
  /** Maximum frame time in ms */
  maxFrameTime: number
  /** Number of frames that exceeded budget (>16ms) */
  droppedFrames: number
  /** Total frames rendered */
  totalFrames: number
  /** Percentage of frames under 16ms budget */
  framesBudgetPercent: number
  /** Last 60 frame times for visualization */
  recentFrameTimes: number[]
}

/**
 * Configuration for performance monitor
 */
export interface PerformanceMonitorConfig {
  /** Target frame time in ms (default: 16.67ms for 60 FPS) */
  targetFrameTime?: number
  /** Number of frames to keep in history (default: 60) */
  historySize?: number
  /** Enable console warnings for slow frames (default: false) */
  warnOnSlowFrames?: boolean
  /** Threshold for slow frame warnings in ms (default: 33ms) */
  slowFrameThreshold?: number
  /** Log detailed metrics to console (default: false) */
  enableLogging?: boolean
}

/**
 * Composable for monitoring rendering performance.
 * Tracks frame times, FPS, and provides detailed statistics.
 *
 * @param config - Optional configuration
 * @returns Performance monitoring controls and metrics
 *
 * @example
 * ```ts
 * const { startFrame, endFrame, metrics, reset } = usePerformanceMonitor({
 *   warnOnSlowFrames: true,
 *   enableLogging: true
 * });
 *
 * function render() {
 *   const frameId = startFrame('user_interaction');
 *
 *   // ... rendering code ...
 *
 *   endFrame(frameId);
 * }
 *
 * // Display stats
 * console.log(`FPS: ${metrics.value.fps.toFixed(1)}`);
 * console.log(`Avg Frame Time: ${metrics.value.averageFrameTime.toFixed(2)}ms`);
 * ```
 */
export function usePerformanceMonitor(config: PerformanceMonitorConfig = {}) {
  const {
    targetFrameTime = 16.67, // 60 FPS
    historySize = 60,
    warnOnSlowFrames = false,
    slowFrameThreshold = 33,
    enableLogging = false
  } = config

  const frames = ref<FrameMetrics[]>([])
  const activeFrames = new Map<number, { startTime: number, reason?: string }>()
  let nextFrameId = 0
  let totalFrameCount = 0

  const metrics = computed<PerformanceMetrics>(() => {
    if (frames.value.length === 0) {
      return {
        fps: 0,
        averageFrameTime: 0,
        minFrameTime: 0,
        maxFrameTime: 0,
        droppedFrames: 0,
        totalFrames: 0,
        framesBudgetPercent: 100,
        recentFrameTimes: []
      }
    }

    const recentFrames = frames.value.slice(-historySize)
    const frameTimes = recentFrames.map(f => f.duration)

    const now = performance.now()
    const oneSecondAgo = now - 1000
    const framesInLastSecond = recentFrames.filter(f => f.timestamp >= oneSecondAgo)
    const fps = framesInLastSecond.length

    const sum = frameTimes.reduce((acc, val) => acc + val, 0)
    const averageFrameTime = sum / frameTimes.length
    const minFrameTime = Math.min(...frameTimes)
    const maxFrameTime = Math.max(...frameTimes)

    const droppedFrames = frameTimes.filter(t => t > targetFrameTime).length
    const framesBudgetPercent = ((frameTimes.length - droppedFrames) / frameTimes.length) * 100

    return {
      fps,
      averageFrameTime,
      minFrameTime,
      maxFrameTime,
      droppedFrames,
      totalFrames: totalFrameCount,
      framesBudgetPercent,
      recentFrameTimes: frameTimes.slice(-60)
    }
  })

  /**
   * Start tracking a new frame
   * @param reason - Optional reason for this frame (for debugging)
   * @returns Frame ID to pass to endFrame
   */
  function startFrame(reason?: string): number {
    const frameId = nextFrameId++
    activeFrames.set(frameId, {
      startTime: performance.now(),
      reason
    })
    return frameId
  }

  /**
   * End tracking for a frame
   * @param frameId - Frame ID returned from startFrame
   */
  function endFrame(frameId: number): void {
    const frameInfo = activeFrames.get(frameId)
    if (!frameInfo) {
      log.warn(`Frame ${frameId} was not started`)
      return
    }

    const endTime = performance.now()
    const duration = endTime - frameInfo.startTime

    const frame: FrameMetrics = {
      timestamp: endTime,
      duration,
      frameNumber: totalFrameCount++,
      reason: frameInfo.reason
    }

    frames.value.push(frame)
    activeFrames.delete(frameId)

    if (frames.value.length > historySize * 2) {
      frames.value = frames.value.slice(-historySize)
    }

    if (warnOnSlowFrames && duration > slowFrameThreshold) {
      log.warn(
        `Slow frame detected: ${duration.toFixed(2)}ms`
        + (frameInfo.reason ? ` (${frameInfo.reason})` : '')
      )
    }

    if (enableLogging && totalFrameCount % 60 === 0) {
      logMetrics()
    }
  }

  /**
   * Measure a synchronous function execution
   * @param fn - Function to measure
   * @param reason - Optional reason for debugging
   * @returns Result of the function
   */
  function measure<T>(fn: () => T, reason?: string): T {
    const frameId = startFrame(reason)
    try {
      return fn()
    } finally {
      endFrame(frameId)
    }
  }

  /**
   * Measure an async function execution
   * @param fn - Async function to measure
   * @param reason - Optional reason for debugging
   * @returns Promise with the result
   */
  async function measureAsync<T>(fn: () => Promise<T>, reason?: string): Promise<T> {
    const frameId = startFrame(reason)
    try {
      return await fn()
    } finally {
      endFrame(frameId)
    }
  }

  /**
   * Reset all statistics
   */
  function reset(): void {
    frames.value = []
    activeFrames.clear()
    totalFrameCount = 0
    nextFrameId = 0
  }

  /**
   * Log current metrics to console
   */
  function logMetrics(): void {
    const m = metrics.value
    log.info(
      `FPS: ${m.fps.toFixed(1)} | `
      + `Avg: ${m.averageFrameTime.toFixed(2)}ms | `
      + `Min: ${m.minFrameTime.toFixed(2)}ms | `
      + `Max: ${m.maxFrameTime.toFixed(2)}ms | `
      + `Budget: ${m.framesBudgetPercent.toFixed(1)}% | `
      + `Dropped: ${m.droppedFrames}/${m.recentFrameTimes.length}`
    )
  }

  /**
   * Get a summary report as a string
   */
  function getReport(): string {
    const m = metrics.value
    return `
Performance Report:
------------------
FPS:                ${m.fps.toFixed(1)}
Average Frame Time: ${m.averageFrameTime.toFixed(2)}ms
Min Frame Time:     ${m.minFrameTime.toFixed(2)}ms
Max Frame Time:     ${m.maxFrameTime.toFixed(2)}ms
Target Frame Time:  ${targetFrameTime.toFixed(2)}ms
Dropped Frames:     ${m.droppedFrames} / ${m.recentFrameTimes.length}
Budget Compliance:  ${m.framesBudgetPercent.toFixed(1)}%
Total Frames:       ${m.totalFrames}
    `.trim()
  }

  /**
   * Check if performance is within acceptable limits
   */
  const isPerformanceGood = computed(() => {
    const m = metrics.value
    return (
      m.fps >= 55 // At least 55 FPS (allowing some variance from 60)
      && m.averageFrameTime <= targetFrameTime
      && m.framesBudgetPercent >= 90 // At least 90% of frames within budget
    )
  })

  /**
   * Get performance status with recommendations
   */
  function getStatus(): {
    status: 'excellent' | 'good' | 'fair' | 'poor'
    message: string
  } {
    const m = metrics.value

    if (m.fps >= 58 && m.framesBudgetPercent >= 95) {
      return {
        status: 'excellent',
        message: 'Performance is excellent! Maintaining target frame rate.'
      }
    } else if (m.fps >= 55 && m.framesBudgetPercent >= 90) {
      return {
        status: 'good',
        message: 'Performance is good. Minor frame drops detected.'
      }
    } else if (m.fps >= 45 && m.framesBudgetPercent >= 75) {
      return {
        status: 'fair',
        message: 'Performance is fair. Consider optimizing render operations.'
      }
    } else {
      return {
        status: 'poor',
        message: 'Performance is poor. Significant optimization needed.'
      }
    }
  }

  /**
   * Export frame data for visualization
   */
  function exportFrameData(): FrameMetrics[] {
    return [...frames.value]
  }

  onBeforeUnmount(() => {
    activeFrames.clear()
  })

  return {
    /** Start tracking a frame */
    startFrame,
    /** End tracking for a frame */
    endFrame,
    /** Measure a synchronous function */
    measure,
    /** Measure an async function */
    measureAsync,
    /** Current performance metrics (reactive) */
    metrics,
    /** Whether performance is within acceptable limits */
    isPerformanceGood,
    /** Reset all statistics */
    reset,
    /** Log metrics to console */
    logMetrics,
    /** Get detailed report */
    getReport,
    /** Get performance status with message */
    getStatus,
    /** Export raw frame data */
    exportFrameData
  }
}

/**
 * Create a wrapper function that automatically measures performance
 *
 * @param fn - Function to wrap
 * @param monitor - Performance monitor instance
 * @param reason - Reason for debugging
 * @returns Wrapped function
 *
 * @example
 * ```ts
 * const monitor = usePerformanceMonitor();
 * const measuredRender = createMeasuredFunction(render, monitor, 'render');
 *
 * // Now just call it normally - measurement is automatic
 * measuredRender();
 * ```
 */
export function createMeasuredFunction<T extends (...args: any[]) => any>(
  fn: T,
  monitor: ReturnType<typeof usePerformanceMonitor>,
  reason?: string
): T {
  return ((...args: any[]) => {
    return monitor.measure(() => fn(...args), reason)
  }) as T
}
