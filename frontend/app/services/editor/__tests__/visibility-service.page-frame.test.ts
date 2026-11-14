import { describe, expect, it } from 'vitest'
import { PolygonType } from '@/models/editor'
import type { Point } from '@/models/editor'
import type { RenderablePolygon } from '@/types/editor/rendering'
import { VisibilityService, type VisibilityContext } from '../visibility-service'
import { BORDER_POLYGON_ID, PRINT_SPACE_POLYGON_ID } from '@/utils/editor/page-frame'

function square(minX: number, minY: number, maxX: number, maxY: number): Point[] {
  return [{ x: minX, y: minY }, { x: maxX, y: minY }, { x: maxX, y: maxY }, { x: minX, y: maxY }]
}

const polygons: RenderablePolygon[] = [
  { id: 'r1', type: PolygonType.REGION, regionKind: 'TextRegion', points: square(0.2, 0.2, 0.4, 0.4) },
  { id: 'tl1', type: PolygonType.TEXTLINE, parentId: 'r1', points: square(0.2, 0.2, 0.4, 0.3) },
  { id: BORDER_POLYGON_ID, type: PolygonType.BORDER, points: square(0, 0, 1, 1) },
  { id: PRINT_SPACE_POLYGON_ID, type: PolygonType.PRINT_SPACE, points: square(0.1, 0.1, 0.9, 0.9) }
]

function context(overrides: Partial<VisibilityContext> = {}): VisibilityContext {
  return {
    selectedPolygonIndex: -1,
    selectedPolylineIndex: -1,
    allPolygons: polygons,
    allPolylines: [],
    ...overrides
  }
}

const ids = (predicate: (polygon: RenderablePolygon) => boolean) => polygons.filter(predicate).map(p => p.id)

describe('page frame visibility', () => {
  const service = new VisibilityService()

  it('keeps frames out of every other view', () => {
    for (const viewMode of [undefined, 'default', 'textline', 'word'] as const) {
      const ctx = context({ viewMode })
      expect(ids(p => service.shouldShowPolygon(p, ctx))).not.toContain(BORDER_POLYGON_ID)
      expect(ids(p => service.shouldShowPolygonHover(p, ctx))).not.toContain(BORDER_POLYGON_ID)
      expect(ids(p => service.shouldBeSelectablePolygon(p, ctx))).not.toContain(BORDER_POLYGON_ID)
      expect(ids(p => service.shouldRenderAsBackground(p, ctx))).not.toContain(BORDER_POLYGON_ID)
    }

    const regionSelected = context({ selectedPolygonIndex: 0 })
    expect(ids(p => service.shouldShowNonSelectedPolygon(p, regionSelected))).toEqual(['tl1'])
  })

  it('makes frames the only interactive shapes in the frame view, with top-level regions as background', () => {
    const ctx = context({ viewMode: 'frame' })

    expect(ids(p => service.shouldShowPolygon(p, ctx))).toEqual([BORDER_POLYGON_ID, PRINT_SPACE_POLYGON_ID])
    expect(ids(p => service.shouldBeSelectablePolygon(p, ctx))).toEqual([BORDER_POLYGON_ID, PRINT_SPACE_POLYGON_ID])
    expect(ids(p => service.shouldRenderAsBackground(p, ctx))).toEqual(['r1'])
  })

  it('keeps both frames visible while one is selected and leaves clicks to deselect first', () => {
    const ctx = context({ viewMode: 'frame', selectedPolygonIndex: 3 })

    expect(ids(p => service.shouldShowPolygon(p, ctx))).toEqual([BORDER_POLYGON_ID, PRINT_SPACE_POLYGON_ID])
    expect(ids(p => service.shouldShowNonSelectedPolygon(p, ctx))).toEqual([BORDER_POLYGON_ID])
    expect(ids(p => service.shouldBeSelectablePolygon(p, ctx))).toEqual([])
    expect(ids(p => service.shouldRenderAsBackground(p, ctx))).toEqual(['r1'])
  })

  it('hides baselines in the frame view', () => {
    const baseline = { id: 'baseline:tl1', parentId: 'tl1', points: square(0.2, 0.25, 0.4, 0.25) }
    const ctx = context({ viewMode: 'frame', allPolylines: [baseline] })

    expect(service.shouldShowPolyline(baseline, ctx)).toBe(false)
    expect(service.shouldShowPolylineHover(baseline, ctx)).toBe(false)
  })
})
