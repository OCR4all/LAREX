import { beforeEach, describe, expect, it, vi } from 'vitest'
import { invalidatePolygonGeometry } from '@/composables/editor/use-geometry-cache-integrations'
import { Polygon } from '@/models/editor/geometry'
import { bufferPolygonInPixels } from '@/utils/editor/buffer-polygon'
import { BufferPolygonCommand } from '../buffer-polygon-command'
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

  it('applies the pixel-distance preview and invalidates geometry on apply and undo', () => {
    const points = [{ x: -0.5, y: 0.5 }, { x: 0.5, y: 0.5 }, { x: 0.5, y: -0.5 }, { x: -0.5, y: -0.5 }]
    const region = createTestTextRegion({ id: 'region-1', points })
    const document = createTestDocument({ regions: [region] })
    const { session, getDocument } = createMockSession(document)
    const ctx = createTestContext(session)
    const original = new Polygon(region.coords.points.map(([x, y]) => [x, y]))
    const preview = bufferPolygonInPixels(original, 10, { width: 2000, height: 3000 })
    const command = new BufferPolygonCommand({ elementId: region.id, elementType: 'region', distance: 10 })

    command.execute(ctx)

    const applied = findRegionById(getDocument()!.page.regions, region.id)!.coords
    expect(applied.points).toEqual(preview.points)
    expect(applied.getArea()).toBeGreaterThan(original.getArea())
    expect(applied.points.every(([x, y]) => Math.abs(x) < 1 && Math.abs(y) < 1)).toBe(true)
    expect(invalidatePolygonGeometry).toHaveBeenLastCalledWith(ctx.canvasId, region.id)

    command.undo(ctx)

    expect(findRegionById(getDocument()!.page.regions, region.id)!.coords.points).toEqual(original.points)
    expect(invalidatePolygonGeometry).toHaveBeenCalledTimes(2)
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
