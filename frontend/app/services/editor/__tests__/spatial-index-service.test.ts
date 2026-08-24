import { beforeEach, describe, expect, it } from 'vitest'
import type { Point } from '@/models/editor'
import type { SpatialIndexService } from '../spatial-index-service'
import { createSpatialIndex } from '../spatial-index-service'

function polygon(id: string, x: number, y: number, size = 100): { id: string, points: Point[] } {
  return {
    id,
    points: [
      { x, y },
      { x: x + size, y },
      { x: x + size, y: y + size },
      { x, y: y + size }
    ]
  }
}

function polyline(id: string, x1: number, y1: number, x2: number, y2: number): { id: string, points: Point[] } {
  return { id, points: [{ x: x1, y: y1 }, { x: x2, y: y2 }] }
}

describe('SpatialIndexService', () => {
  let index: SpatialIndexService

  beforeEach(() => {
    index = createSpatialIndex()
  })

  it('starts empty and supports an empty rebuild', () => {
    expect(index.isEmpty()).toBe(true)

    index.rebuildPolygonIndex([])

    expect(index.getStats()).toEqual({ polygonCount: 0, polylineCount: 0 })
    expect(index.isEmpty()).toBe(true)
  })

  it('rebuilds the polygon index and replaces prior contents', () => {
    index.rebuildPolygonIndex([polygon('old', 0, 0)])
    index.rebuildPolygonIndex([
      polygon('p1', 0, 0),
      polygon('p2', 200, 200),
      polygon('p3', 400, 400)
    ])

    expect(index.getPolygonIds().sort()).toEqual(['p1', 'p2', 'p3'])
    expect(index.hasPolygon('old')).toBe(false)
    expect(index.getStats().polygonCount).toBe(3)
  })

  it('inserts polygons incrementally', () => {
    index.insertPolygon(polygon('p1', 0, 0), 0)
    index.insertPolygon(polygon('p2', 200, 200), 1)
    index.insertPolygon(polygon('p3', 400, 400), 2)

    expect(index.getPolygonIds().sort()).toEqual(['p1', 'p2', 'p3'])
    expect(index.getStats().polygonCount).toBe(3)
    expect(index.isEmpty()).toBe(false)
  })

  it('removes an indexed polygon and rejects a missing ID', () => {
    index.rebuildPolygonIndex([polygon('p1', 0, 0), polygon('p2', 200, 200)])

    expect(index.removePolygon('p1')).toBe(true)
    expect(index.removePolygon('missing')).toBe(false)
    expect(index.getPolygonIds()).toEqual(['p2'])
    expect(index.getStats().polygonCount).toBe(1)
  })

  it('updates polygon geometry without duplicating the entry', () => {
    index.rebuildPolygonIndex([polygon('p1', 0, 0)])

    index.updatePolygon(polygon('p1', 500, 500, 200), 0)

    expect(index.queryPolygonsAtPoint({ x: 50, y: 50 })).toEqual([])
    expect(index.queryPolygonsAtPoint({ x: 550, y: 550 })).toEqual([0])
    expect(index.getStats().polygonCount).toBe(1)
  })

  it('queries overlapping polygons in newest-first order and excludes outside points', () => {
    index.rebuildPolygonIndex([
      polygon('p1', 0, 0),
      polygon('p2', 200, 200),
      polygon('p3', 50, 50, 50)
    ])

    expect(index.queryPolygonsAtPoint({ x: 60, y: 60 })).toEqual([2, 0])
    expect(index.queryPolygonsAtPoint({ x: 1000, y: 1000 })).toEqual([])
  })

  it('queries exact polygon sets within bounding boxes', () => {
    index.rebuildPolygonIndex([
      polygon('p1', 0, 0),
      polygon('p2', 200, 200),
      polygon('p3', 400, 400)
    ])

    expect(index.queryPolygonsInBBox({ minX: 0, minY: 0, maxX: 150, maxY: 150 }).sort()).toEqual([0])
    expect(index.queryPolygonsInBBox({ minX: 0, minY: 0, maxX: 500, maxY: 500 }).sort()).toEqual([0, 1, 2])
  })

  it('rebuilds, inserts, and removes polylines', () => {
    index.rebuildPolylineIndex([
      polyline('l1', 0, 0, 100, 100),
      polyline('l2', 200, 200, 300, 300)
    ])
    index.insertPolyline(polyline('l3', 400, 400, 500, 500), 2)

    expect(index.getPolylineIds().sort()).toEqual(['l1', 'l2', 'l3'])
    expect(index.removePolyline('l2')).toBe(true)
    expect(index.getPolylineIds().sort()).toEqual(['l1', 'l3'])
    expect(index.getStats().polylineCount).toBe(2)
  })

  it('queries nearby polylines and excludes distant points', () => {
    index.rebuildPolylineIndex([
      polyline('l1', 0, 0, 100, 100),
      polyline('l2', 200, 200, 300, 300)
    ])

    expect(index.queryPolylinesAtPoint({ x: 50, y: 50 })).toEqual([0])
    expect(index.queryPolylinesAtPoint({ x: 1000, y: 1000 })).toEqual([])
  })

  it('applies mixed polygon and polyline updates atomically', () => {
    index.rebuildPolygonIndex([polygon('p1', 0, 0), polygon('p2', 200, 200)])
    index.rebuildPolylineIndex([polyline('l1', 0, 0, 100, 100)])

    index.applyBatchUpdate({
      polygonsToRemove: ['p1'],
      polygonsToAdd: [polygon('p3', 400, 400)],
      polygonsToUpdate: [polygon('p2', 500, 500)],
      polylinesToAdd: [polyline('l2', 500, 500, 600, 600)]
    })

    expect(index.getPolygonIds().sort()).toEqual(['p2', 'p3'])
    expect(index.getPolylineIds().sort()).toEqual(['l1', 'l2'])
    expect(index.queryPolygonsAtPoint({ x: 250, y: 250 })).toEqual([])
    expect(index.queryPolygonsAtPoint({ x: 550, y: 550 })).toEqual([1])
    expect(index.getStats()).toEqual({ polygonCount: 2, polylineCount: 2 })
  })

  it('clears polygon and polyline IDs together', () => {
    index.rebuildPolygonIndex([polygon('p1', 0, 0)])
    index.rebuildPolylineIndex([polyline('l1', 0, 0, 100, 100)])
    expect(index.isEmpty()).toBe(false)

    index.clear()

    expect(index.getPolygonIds()).toEqual([])
    expect(index.getPolylineIds()).toEqual([])
    expect(index.getStats()).toEqual({ polygonCount: 0, polylineCount: 0 })
    expect(index.isEmpty()).toBe(true)
  })
})
