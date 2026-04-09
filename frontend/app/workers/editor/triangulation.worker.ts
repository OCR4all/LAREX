/**
 * WebWorker for offloading expensive polygon triangulation from the main thread.
 * This prevents UI jank when processing large or complex polygons.
 */

import { triangulatePolygon } from '@/utils/editor/hit-detection'

export interface TriangulationRequest {
  id: string
  type: 'triangulate'
  polygonId: string
  points: Array<{ x: number, y: number }>
}

export interface TriangulationResponse {
  id: string
  polygonId: string
  indices: number[]
  error?: string
}

export interface BatchTriangulationRequest {
  id: string
  type: 'triangulate-batch'
  polygons: Array<{
    polygonId: string
    points: Array<{ x: number, y: number }>
  }>
}

export interface BatchTriangulationResponse {
  id: string
  results: Array<{
    polygonId: string
    indices: number[]
    error?: string
  }>
}

export type WorkerRequest = TriangulationRequest | BatchTriangulationRequest
export type WorkerResponse = TriangulationResponse | BatchTriangulationResponse

/**
 * Type guard for batch requests
 */
function isBatchRequest(request: WorkerRequest): request is BatchTriangulationRequest {
  return request.type === 'triangulate-batch'
}

/**
 * Process a single triangulation request
 */
function processTriangulation(
  points: Array<{ x: number, y: number }>
): { indices: number[], error?: string } {
  try {
    if (points.length < 3) {
      return { indices: [], error: 'Polygon must have at least 3 points' }
    }

    const indices = triangulatePolygon(points)
    return { indices }
  } catch (err) {
    return {
      indices: [],
      error: err instanceof Error ? err.message : 'Triangulation failed'
    }
  }
}

/**
 * Handle incoming messages from the main thread
 */
self.onmessage = (event: MessageEvent<WorkerRequest>) => {
  const request = event.data

  if (isBatchRequest(request)) {
    const results = request.polygons.map(({ polygonId, points }) => {
      const result = processTriangulation(points)
      return {
        polygonId,
        ...result
      }
    })

    const response: BatchTriangulationResponse = {
      id: request.id,
      results
    }

    self.postMessage(response)
  } else {
    const result = processTriangulation(request.points)

    const response: TriangulationResponse = {
      id: request.id,
      polygonId: request.polygonId,
      ...result
    }

    self.postMessage(response)
  }
}

self.postMessage({ type: 'ready' })
