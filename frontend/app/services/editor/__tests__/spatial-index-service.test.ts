import { describe, it, expect, beforeEach } from 'vitest'
import type { SpatialIndexService } from '../spatial-index-service'
import { createSpatialIndex } from '../spatial-index-service'
import type { Point } from '@/models/editor'

function createTestPolygon(id: string, x: number, y: number, size: number = 100): { id: string, points: Point[] } {
  return {
    id,
    points: [
      { x: x, y: y },
      { x: x + size, y: y },
      { x: x + size, y: y + size },
      { x: x, y: y + size }
    ]
  }
}

function createTestPolyline(id: string, x1: number, y1: number, x2: number, y2: number): { id: string, points: Point[] } {
  return {
    id,
    points: [
      { x: x1, y: y1 },
      { x: x2, y: y2 }
    ]
  }
}

describe('SpatialIndexService', () => {
  let spatialIndex: SpatialIndexService

  beforeEach(() => {
    spatialIndex = createSpatialIndex()
  })

  describe('Polygon Index Operations', () => {
    describe('rebuildPolygonIndex', () => {
      it('should build index from empty array', () => {
        spatialIndex.rebuildPolygonIndex([])
        expect(spatialIndex.getStats().polygonCount).toBe(0)
        expect(spatialIndex.isEmpty()).toBe(true)
      })

      it('should build index from polygons array', () => {
        const polygons = [
          createTestPolygon('p1', 0, 0),
          createTestPolygon('p2', 200, 200),
          createTestPolygon('p3', 400, 400)
        ]

        spatialIndex.rebuildPolygonIndex(polygons)

        expect(spatialIndex.getStats().polygonCount).toBe(3)
        expect(spatialIndex.hasPolygon('p1')).toBe(true)
        expect(spatialIndex.hasPolygon('p2')).toBe(true)
        expect(spatialIndex.hasPolygon('p3')).toBe(true)
      })

      it('should clear previous index when rebuilding', () => {
        spatialIndex.rebuildPolygonIndex([createTestPolygon('old', 0, 0)])
        expect(spatialIndex.hasPolygon('old')).toBe(true)

        spatialIndex.rebuildPolygonIndex([createTestPolygon('new', 100, 100)])
        expect(spatialIndex.hasPolygon('old')).toBe(false)
        expect(spatialIndex.hasPolygon('new')).toBe(true)
      })
    })

    describe('insertPolygon', () => {
      it('should insert a polygon into the index', () => {
        const polygon = createTestPolygon('p1', 0, 0)
        spatialIndex.insertPolygon(polygon, 0)

        expect(spatialIndex.hasPolygon('p1')).toBe(true)
        expect(spatialIndex.getStats().polygonCount).toBe(1)
      })

      it('should allow inserting multiple polygons incrementally', () => {
        spatialIndex.insertPolygon(createTestPolygon('p1', 0, 0), 0)
        spatialIndex.insertPolygon(createTestPolygon('p2', 200, 200), 1)
        spatialIndex.insertPolygon(createTestPolygon('p3', 400, 400), 2)

        expect(spatialIndex.getStats().polygonCount).toBe(3)
      })
    })

    describe('removePolygon', () => {
      it('should remove a polygon from the index', () => {
        spatialIndex.rebuildPolygonIndex([
          createTestPolygon('p1', 0, 0),
          createTestPolygon('p2', 200, 200)
        ])

        const removed = spatialIndex.removePolygon('p1')

        expect(removed).toBe(true)
        expect(spatialIndex.hasPolygon('p1')).toBe(false)
        expect(spatialIndex.hasPolygon('p2')).toBe(true)
        expect(spatialIndex.getStats().polygonCount).toBe(1)
      })

      it('should return false when removing non-existent polygon', () => {
        const removed = spatialIndex.removePolygon('nonexistent')
        expect(removed).toBe(false)
      })
    })

    describe('updatePolygon', () => {
      it('should update a polygon in the index', () => {
        spatialIndex.rebuildPolygonIndex([createTestPolygon('p1', 0, 0, 100)])

        const updatedPolygon = createTestPolygon('p1', 500, 500, 200)
        spatialIndex.updatePolygon(updatedPolygon, 0)

        expect(spatialIndex.hasPolygon('p1')).toBe(true)
        expect(spatialIndex.getStats().polygonCount).toBe(1)

        const results = spatialIndex.queryPolygonsAtPoint({ x: 550, y: 550 })
        expect(results).toContain(0)
      })
    })

    describe('queryPolygonsAtPoint', () => {
      beforeEach(() => {
        spatialIndex.rebuildPolygonIndex([
          createTestPolygon('p1', 0, 0, 100),
          createTestPolygon('p2', 200, 200, 100),
          createTestPolygon('p3', 50, 50, 50) // Overlaps with p1
        ])
      })

      it('should return polygon indices at a point', () => {
        const results = spatialIndex.queryPolygonsAtPoint({ x: 60, y: 60 })
        expect(results.length).toBeGreaterThan(0)
      })

      it('should return empty array for points outside all polygons', () => {
        const results = spatialIndex.queryPolygonsAtPoint({ x: 1000, y: 1000 })
        expect(results).toEqual([])
      })

      it('should return indices in reverse order (newest first)', () => {
        const results = spatialIndex.queryPolygonsAtPoint({ x: 60, y: 60 })
        if (results.length > 1) {
          for (let i = 0; i < results.length - 1; i++) {
            expect(results[i]).toBeGreaterThan(results[i + 1]!)
          }
        }
      })
    })

    describe('queryPolygonsInBBox', () => {
      beforeEach(() => {
        spatialIndex.rebuildPolygonIndex([
          createTestPolygon('p1', 0, 0, 100),
          createTestPolygon('p2', 200, 200, 100),
          createTestPolygon('p3', 400, 400, 100)
        ])
      })

      it('should return polygons within bounding box', () => {
        const results = spatialIndex.queryPolygonsInBBox({
          minX: 0,
          minY: 0,
          maxX: 150,
          maxY: 150
        })
        expect(results).toContain(0)
        expect(results).not.toContain(1)
        expect(results).not.toContain(2)
      })

      it('should return multiple polygons when bbox covers them', () => {
        const results = spatialIndex.queryPolygonsInBBox({
          minX: 0,
          minY: 0,
          maxX: 500,
          maxY: 500
        })
        expect(results.length).toBe(3)
      })
    })
  })

  describe('Polyline Index Operations', () => {
    describe('rebuildPolylineIndex', () => {
      it('should build index from polylines array', () => {
        const polylines = [
          createTestPolyline('l1', 0, 0, 100, 100),
          createTestPolyline('l2', 200, 200, 300, 300)
        ]

        spatialIndex.rebuildPolylineIndex(polylines)

        expect(spatialIndex.getStats().polylineCount).toBe(2)
        expect(spatialIndex.hasPolyline('l1')).toBe(true)
        expect(spatialIndex.hasPolyline('l2')).toBe(true)
      })
    })

    describe('insertPolyline', () => {
      it('should insert a polyline into the index', () => {
        const polyline = createTestPolyline('l1', 0, 0, 100, 100)
        spatialIndex.insertPolyline(polyline, 0)

        expect(spatialIndex.hasPolyline('l1')).toBe(true)
        expect(spatialIndex.getStats().polylineCount).toBe(1)
      })
    })

    describe('removePolyline', () => {
      it('should remove a polyline from the index', () => {
        spatialIndex.rebuildPolylineIndex([
          createTestPolyline('l1', 0, 0, 100, 100),
          createTestPolyline('l2', 200, 200, 300, 300)
        ])

        const removed = spatialIndex.removePolyline('l1')

        expect(removed).toBe(true)
        expect(spatialIndex.hasPolyline('l1')).toBe(false)
        expect(spatialIndex.hasPolyline('l2')).toBe(true)
      })
    })

    describe('queryPolylinesAtPoint', () => {
      beforeEach(() => {
        spatialIndex.rebuildPolylineIndex([
          createTestPolyline('l1', 0, 0, 100, 100),
          createTestPolyline('l2', 200, 200, 300, 300)
        ])
      })

      it('should return polyline indices near a point', () => {
        const results = spatialIndex.queryPolylinesAtPoint({ x: 50, y: 50 })
        expect(results).toContain(0)
      })

      it('should return empty for points far from polylines', () => {
        const results = spatialIndex.queryPolylinesAtPoint({ x: 1000, y: 1000 })
        expect(results).toEqual([])
      })
    })
  })

  describe('Batch Operations', () => {
    describe('applyBatchUpdate', () => {
      beforeEach(() => {
        spatialIndex.rebuildPolygonIndex([
          createTestPolygon('p1', 0, 0),
          createTestPolygon('p2', 200, 200)
        ])
        spatialIndex.rebuildPolylineIndex([
          createTestPolyline('l1', 0, 0, 100, 100)
        ])
      })

      it('should add new polygons', () => {
        spatialIndex.applyBatchUpdate({
          polygonsToAdd: [createTestPolygon('p3', 400, 400)]
        })

        expect(spatialIndex.hasPolygon('p3')).toBe(true)
        expect(spatialIndex.getStats().polygonCount).toBe(3)
      })

      it('should remove polygons', () => {
        spatialIndex.applyBatchUpdate({
          polygonsToRemove: ['p1']
        })

        expect(spatialIndex.hasPolygon('p1')).toBe(false)
        expect(spatialIndex.getStats().polygonCount).toBe(1)
      })

      it('should update polygons', () => {
        spatialIndex.applyBatchUpdate({
          polygonsToUpdate: [createTestPolygon('p1', 500, 500, 200)]
        })

        expect(spatialIndex.hasPolygon('p1')).toBe(true)
        const results = spatialIndex.queryPolygonsAtPoint({ x: 550, y: 550 })
        expect(results.length).toBeGreaterThan(0)
      })

      it('should handle mixed operations', () => {
        spatialIndex.applyBatchUpdate({
          polygonsToRemove: ['p1'],
          polygonsToAdd: [createTestPolygon('p3', 400, 400)],
          polylinesToAdd: [createTestPolyline('l2', 500, 500, 600, 600)]
        })

        expect(spatialIndex.hasPolygon('p1')).toBe(false)
        expect(spatialIndex.hasPolygon('p3')).toBe(true)
        expect(spatialIndex.hasPolyline('l2')).toBe(true)
        expect(spatialIndex.getStats().polygonCount).toBe(2)
        expect(spatialIndex.getStats().polylineCount).toBe(2)
      })
    })
  })

  describe('Utility Methods', () => {
    describe('clear', () => {
      it('should clear all indices', () => {
        spatialIndex.rebuildPolygonIndex([createTestPolygon('p1', 0, 0)])
        spatialIndex.rebuildPolylineIndex([createTestPolyline('l1', 0, 0, 100, 100)])

        spatialIndex.clear()

        expect(spatialIndex.isEmpty()).toBe(true)
        expect(spatialIndex.getStats().polygonCount).toBe(0)
        expect(spatialIndex.getStats().polylineCount).toBe(0)
      })
    })

    describe('getPolygonIds / getPolylineIds', () => {
      it('should return all indexed IDs', () => {
        spatialIndex.rebuildPolygonIndex([
          createTestPolygon('p1', 0, 0),
          createTestPolygon('p2', 100, 100)
        ])
        spatialIndex.rebuildPolylineIndex([
          createTestPolyline('l1', 0, 0, 100, 100)
        ])

        const polygonIds = spatialIndex.getPolygonIds()
        const polylineIds = spatialIndex.getPolylineIds()

        expect(polygonIds).toContain('p1')
        expect(polygonIds).toContain('p2')
        expect(polylineIds).toContain('l1')
      })
    })

    describe('isEmpty', () => {
      it('should return true when empty', () => {
        expect(spatialIndex.isEmpty()).toBe(true)
      })

      it('should return false when has polygons', () => {
        spatialIndex.insertPolygon(createTestPolygon('p1', 0, 0), 0)
        expect(spatialIndex.isEmpty()).toBe(false)
      })

      it('should return false when has polylines', () => {
        spatialIndex.insertPolyline(createTestPolyline('l1', 0, 0, 100, 100), 0)
        expect(spatialIndex.isEmpty()).toBe(false)
      })
    })
  })

  describe('Performance characteristics', () => {
    it('should handle large number of polygons efficiently', () => {
      const polygons = Array.from({ length: 1000 }, (_, i) =>
        createTestPolygon(`p${i}`, i * 10, i * 10, 50)
      )

      const startRebuild = performance.now()
      spatialIndex.rebuildPolygonIndex(polygons)
      const rebuildTime = performance.now() - startRebuild

      expect(rebuildTime).toBeLessThan(100)

      const startQuery = performance.now()
      spatialIndex.queryPolygonsAtPoint({ x: 500, y: 500 })
      const queryTime = performance.now() - startQuery

      expect(queryTime).toBeLessThan(5)
    })

    it('should have O(1) lookup by ID', () => {
      const polygons = Array.from({ length: 1000 }, (_, i) =>
        createTestPolygon(`p${i}`, i * 10, i * 10, 50)
      )
      spatialIndex.rebuildPolygonIndex(polygons)

      const startLookup = performance.now()
      for (let i = 0; i < 100; i++) {
        spatialIndex.hasPolygon(`p${i * 10}`)
      }
      const lookupTime = performance.now() - startLookup

      expect(lookupTime).toBeLessThan(5)
    })
  })
})
