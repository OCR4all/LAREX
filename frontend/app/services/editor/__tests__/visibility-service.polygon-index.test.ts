import { describe, expect, it } from 'vitest'
import { VisibilityService, indexPolygonsById, type Polygon, type Polyline, type VisibilityContext } from '../visibility-service'

const polygons: Polygon[] = [
  { id: 'table', type: 'region', points: [] },
  { id: 'cell', type: 'region', parentId: 'table', points: [] },
  { id: 'region', type: 'region', points: [] }
]
const polylines: Polyline[] = [
  { id: 'cell-line', parentId: 'cell', points: [] },
  { id: 'region-line', parentId: 'region', points: [] },
  { id: 'orphan', points: [] },
  { id: 'missing-parent', parentId: 'gone', points: [] }
]

describe('polyline visibility with a polygon index', () => {
  const service = new VisibilityService()
  const base: VisibilityContext = { selectedPolygonIndex: -1, selectedPolylineIndex: -1, allPolygons: polygons, allPolylines: polylines }
  const indexed: VisibilityContext = { ...base, polygonsById: indexPolygonsById(polygons) }

  it('gives the same answers as scanning the polygon list', () => {
    for (const check of ['shouldShowPolyline', 'shouldShowPolylineHover'] as const) {
      const scanned = polylines.map(line => service[check](line, base))
      expect(polylines.map(line => service[check](line, indexed))).toEqual(scanned)
      expect(scanned).toEqual([false, true, true, false])
    }
  })
})
