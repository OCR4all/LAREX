/**
 * Service for managing the triangulation WebWorker.
 * Provides async triangulation with request queuing and caching.
 */

import type {
  TriangulationRequest,
  TriangulationResponse,
  BatchTriangulationRequest,
  BatchTriangulationResponse,
  WorkerResponse
} from '@/workers/editor/triangulation.worker'
import { createScopedLogger } from '@/services/editor/logger-service'

const log = createScopedLogger('TriangulationWorker')

export interface PendingRequest {
  resolve: (indices: number[]) => void
  reject: (error: Error) => void
}

export interface BatchPendingRequest {
  resolve: (results: Array<{ polygonId: string, indices: number[], error?: string }>) => void
  reject: (error: Error) => void
}

/**
 * Service for offloading triangulation to a WebWorker.
 * Falls back to synchronous triangulation if workers aren't available.
 */
export class TriangulationWorkerService {
  private worker: Worker | null = null
  private pendingRequests: Map<string, PendingRequest> = new Map()
  private batchPendingRequests: Map<string, BatchPendingRequest> = new Map()
  private requestCounter = 0
  private isReady = false
  private readyPromise: Promise<void>
  private readyResolve: (() => void) | null = null

  constructor() {
    this.readyPromise = new Promise((resolve) => {
      this.readyResolve = resolve
    })
    this.initWorker()
  }

  /**
   * Initialize the WebWorker
   */
  private initWorker(): void {
    if (typeof Worker === 'undefined') {
      this.isReady = true
      this.readyResolve?.()
      return
    }

    try {
      this.worker = new Worker(
        new URL('@/workers/triangulation.worker.ts', import.meta.url),
        { type: 'module' }
      )

      this.worker.onmessage = this.handleMessage.bind(this)
      this.worker.onerror = this.handleError.bind(this)
    } catch (err) {
      log.warn('Failed to create worker, using sync fallback:', err)
      this.isReady = true
      this.readyResolve?.()
    }
  }

  /**
   * Handle messages from the worker
   */
  private handleMessage(event: MessageEvent<WorkerResponse | { type: string }>): void {
    const data = event.data

    if ('type' in data && data.type === 'ready') {
      this.isReady = true
      this.readyResolve?.()
      return
    }

    const response = data as WorkerResponse

    if ('results' in response) {
      const batchResponse = response as BatchTriangulationResponse
      const pending = this.batchPendingRequests.get(batchResponse.id)
      if (pending) {
        this.batchPendingRequests.delete(batchResponse.id)
        pending.resolve(batchResponse.results)
      }
      return
    }

    const singleResponse = response as TriangulationResponse
    const pending = this.pendingRequests.get(singleResponse.id)
    if (pending) {
      this.pendingRequests.delete(singleResponse.id)
      if (singleResponse.error) {
        pending.reject(new Error(singleResponse.error))
      } else {
        pending.resolve(singleResponse.indices)
      }
    }
  }

  /**
   * Handle worker errors
   */
  private handleError(event: ErrorEvent): void {
    log.error('Worker error:', event.message)

    for (const [id, pending] of this.pendingRequests) {
      pending.reject(new Error('Worker error: ' + event.message))
      this.pendingRequests.delete(id)
    }
    for (const [id, pending] of this.batchPendingRequests) {
      pending.reject(new Error('Worker error: ' + event.message))
      this.batchPendingRequests.delete(id)
    }
  }

  /**
   * Generate a unique request ID
   */
  private generateId(): string {
    return `req_${++this.requestCounter}_${Date.now()}`
  }

  /**
   * Wait for the worker to be ready
   */
  async waitForReady(): Promise<void> {
    return this.readyPromise
  }

  /**
   * Triangulate a single polygon asynchronously
   */
  async triangulate(
    polygonId: string,
    points: Array<{ x: number, y: number }>
  ): Promise<number[]> {
    await this.readyPromise

    if (!this.worker) {
      const { triangulatePolygonPoints } = await import('@/utils/editor/hit-detection')
      return triangulatePolygonPoints(points)
    }

    const id = this.generateId()
    const request: TriangulationRequest = {
      id,
      type: 'triangulate',
      polygonId,
      points
    }

    return new Promise((resolve, reject) => {
      this.pendingRequests.set(id, { resolve, reject })
      this.worker!.postMessage(request)
    })
  }

  /**
   * Triangulate multiple polygons in a batch
   */
  async triangulateBatch(
    polygons: Array<{
      polygonId: string
      points: Array<{ x: number, y: number }>
    }>
  ): Promise<Array<{ polygonId: string, indices: number[], error?: string }>> {
    await this.readyPromise

    if (!this.worker) {
      const { triangulatePolygonPoints } = await import('@/utils/editor/hit-detection')
      return polygons.map(({ polygonId, points }) => {
        try {
          const indices = triangulatePolygonPoints(points)
          return { polygonId, indices }
        } catch (err) {
          return {
            polygonId,
            indices: [],
            error: err instanceof Error ? err.message : 'Triangulation failed'
          }
        }
      })
    }

    const id = this.generateId()
    const request: BatchTriangulationRequest = {
      id,
      type: 'triangulate-batch',
      polygons
    }

    return new Promise((resolve, reject) => {
      this.batchPendingRequests.set(id, { resolve, reject })
      this.worker!.postMessage(request)
    })
  }

  /**
   * Terminate the worker
   */
  terminate(): void {
    if (this.worker) {
      this.worker.terminate()
      this.worker = null
    }
    this.pendingRequests.clear()
    this.batchPendingRequests.clear()
  }

  /**
   * Check if worker is available
   */
  get isAvailable(): boolean {
    return this.worker !== null
  }
}

let triangulationWorkerInstance: TriangulationWorkerService | null = null

/**
 * Get or create the triangulation worker service
 */
export function getTriangulationWorker(): TriangulationWorkerService {
  if (!triangulationWorkerInstance) {
    triangulationWorkerInstance = new TriangulationWorkerService()
  }
  return triangulationWorkerInstance
}

/**
 * Triangulate a polygon using the worker (convenience function)
 */
export async function triangulateAsync(
  polygonId: string,
  points: Array<{ x: number, y: number }>
): Promise<number[]> {
  return getTriangulationWorker().triangulate(polygonId, points)
}

/**
 * Batch triangulate polygons using the worker (convenience function)
 */
export async function triangulateBatchAsync(
  polygons: Array<{
    polygonId: string
    points: Array<{ x: number, y: number }>
  }>
): Promise<Array<{ polygonId: string, indices: number[], error?: string }>> {
  return getTriangulationWorker().triangulateBatch(polygons)
}
