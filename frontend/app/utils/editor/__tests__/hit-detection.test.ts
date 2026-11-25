import { describe, expect, it } from 'vitest'
import type { Polygon } from '@/services/editor/visibility-service'
import { createSpatialIndex } from '@/services/editor/spatial-index-service'
import { getHoverablePolygonAtPoint, getVisiblePolygonAtPoint } from '../hit-detection'

function polygon(id: string, [minX, minY, maxX, maxY]: [number, number, number, number], overrides: Partial<Polygon> = {}): Polygon {
  return {
    id,
    type: 'region',
    points: [{ x: minX, y: minY }, { x: maxX, y: minY }, { x: maxX, y: maxY }, { x: minX, y: maxY }],
    ...overrides
  }
}

function hits(polygons: Polygon[], point: { x: number, y: number }, selectedId?: string, viewMode?: 'textline') {
  const selectedIndex = polygons.findIndex(p => p.id === selectedId)
  const spatialIndex = createSpatialIndex()
  spatialIndex.rebuildPolygonIndex(polygons)
  const ids = [
    getVisiblePolygonAtPoint(polygons, point, selectedIndex, spatialIndex, viewMode),
    getVisiblePolygonAtPoint(polygons, point, selectedIndex, undefined, viewMode),
    getHoverablePolygonAtPoint(polygons, point, selectedIndex, -1, [], spatialIndex, viewMode)
  ].map(index => polygons[index]?.id)
  expect(new Set(ids.slice(0, 2)).size).toBe(1)
  return { click: ids[0], hover: ids[2] }
}

describe('polygon hit testing', () => {
  it.each([
    ['above', ['small', 'large']],
    ['below', ['large', 'small']]
  ])('reaches a small region when the covering region is %s it', (_label, order) => {
    const byId = { small: polygon('small', [10, 10, 20, 20]), large: polygon('large', [0, 0, 100, 100]) }
    const polygons = order.map(id => byId[id as keyof typeof byId])

    expect(hits(polygons, { x: 15, y: 15 })).toEqual({ click: 'small', hover: 'small' })
    expect(hits(polygons, { x: 50, y: 50 })).toEqual({ click: 'large', hover: 'large' })
  })

  it('keeps the topmost region among regions of equal size', () => {
    const polygons = [polygon('lower', [0, 0, 10, 10]), polygon('upper', [5, 0, 15 + 1e-12, 10])]
    expect(hits(polygons, { x: 7, y: 5 }).click).toBe('upper')
  })

  it('reaches a child region that is larger than its selected parent', () => {
    const polygons = [polygon('parent', [0, 0, 10, 10]), polygon('child', [0, 0, 30, 30], { parentId: 'parent' })]
    expect(hits(polygons, { x: 5, y: 5 }, 'parent')).toEqual({ click: 'child', hover: 'child' })
  })

  it('prefers the smaller of two overlapping child regions', () => {
    const polygons = [
      polygon('parent', [0, 0, 100, 100]),
      polygon('small', [10, 10, 20, 20], { parentId: 'parent' }),
      polygon('large', [0, 0, 60, 60], { parentId: 'parent' })
    ]
    expect(hits(polygons, { x: 15, y: 15 }, 'parent')).toEqual({ click: 'small', hover: 'small' })
  })

  it('keeps text lines in document order', () => {
    const polygons = [
      polygon('region', [0, 0, 100, 100]),
      polygon('small-line', [10, 10, 20, 20], { type: 'textline', parentId: 'region' }),
      polygon('large-line', [0, 0, 60, 30], { type: 'textline', parentId: 'region' })
    ]
    expect(hits(polygons, { x: 15, y: 15 }, undefined, 'textline')).toEqual({ click: 'large-line', hover: 'large-line' })
  })

  it('skips hidden regions', () => {
    const polygons = [polygon('small', [10, 10, 20, 20]), polygon('large', [0, 0, 100, 100])]
    const index = getVisiblePolygonAtPoint(polygons, { x: 15, y: 15 }, -1, undefined, undefined, new Set(['small']))
    expect(polygons[index]?.id).toBe('large')
  })
})
