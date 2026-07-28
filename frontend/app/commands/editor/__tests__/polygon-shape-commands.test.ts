import { beforeEach, describe, expect, it, vi } from 'vitest'
import { invalidatePolygonGeometry } from '@/composables/editor/use-geometry-cache-integrations'
import { ConvexHullCommand } from '../convex-hull-command'
import { FitToBoundingBoxCommand } from '../fit-to-bounding-box-command'
import {
  createMockSession,
  createTestContext,
  createTestDocument,
  createTestTextRegion,
  findRegionById
} from './test-utils'

vi.mock('@/composables/editor/use-geometry-cache-integrations', () => ({
  invalidatePolygonGeometry: vi.fn()
}))

vi.mock('@/services/editor/visibility-service', () => ({
  visibilityService: {
    clearCache: vi.fn()
  }
}))

const originalPoints = [
  { x: 100, y: 100 },
  { x: 500, y: 100 },
  { x: 300, y: 200 },
  { x: 500, y: 300 },
  { x: 100, y: 300 }
]

function setup() {
  const region = createTestTextRegion({ id: 'region-1', points: originalPoints })
  const { session, getDocument } = createMockSession(createTestDocument({ regions: [region] }))
  return {
    ctx: createTestContext(session),
    getPoints: () => findRegionById(getDocument()!.page.regions, region.id)!.coords.points
  }
}

describe('polygon shape commands', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('invalidates rendered geometry after fitting a polygon to its bounding box and undoing', () => {
    const { ctx, getPoints } = setup()
    const command = new FitToBoundingBoxCommand({
      elementId: 'region-1',
      elementType: 'region'
    })

    command.execute(ctx)

    expect(getPoints()).toEqual([
      [100, 100],
      [500, 100],
      [500, 300],
      [100, 300]
    ])
    expect(invalidatePolygonGeometry).toHaveBeenLastCalledWith(ctx.canvasId, 'region-1')

    command.undo(ctx)

    expect(getPoints()).toEqual(originalPoints.map(point => [point.x, point.y]))
    expect(invalidatePolygonGeometry).toHaveBeenCalledTimes(2)
  })

  it('invalidates rendered geometry after computing a convex hull and undoing', () => {
    const { ctx, getPoints } = setup()
    const command = new ConvexHullCommand({
      elementId: 'region-1',
      elementType: 'region'
    })

    command.execute(ctx)

    expect(getPoints()).toEqual([
      [100, 100],
      [500, 100],
      [500, 300],
      [100, 300]
    ])
    expect(invalidatePolygonGeometry).toHaveBeenLastCalledWith(ctx.canvasId, 'region-1')

    command.undo(ctx)

    expect(getPoints()).toEqual(originalPoints.map(point => [point.x, point.y]))
    expect(invalidatePolygonGeometry).toHaveBeenCalledTimes(2)
  })
})
