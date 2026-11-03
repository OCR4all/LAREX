import { describe, expect, it } from 'vitest'
import { PolygonType } from '@/models/editor'
import type { Point } from '@/models/editor'
import type { RenderablePolygon } from '@/types/editor/rendering'
import { VisibilityService, normalizeLayoutViewMode, type VisibilityContext } from '../visibility-service'
import { computeAutoParentPreview } from '@/utils/editor/auto-parent-utils'
import { isValidParentChildType, validatePolygonParent } from '@/utils/editor/hierarchy-validation'
import { buildChildrenByParentId } from '@/components/editor/sidebar/structure-tree'

function square(minX: number, minY: number, maxX: number, maxY: number): Point[] {
  return [{ x: minX, y: minY }, { x: maxX, y: minY }, { x: maxX, y: maxY }, { x: minX, y: maxY }]
}

const polygons: RenderablePolygon[] = [
  { id: 'r1', type: PolygonType.REGION, regionKind: 'TextRegion', points: square(0, 0, 1, 1) },
  { id: 'tl1', type: PolygonType.TEXTLINE, parentId: 'r1', points: square(0.1, 0.1, 0.9, 0.2) },
  { id: 'w1', type: PolygonType.WORD, parentId: 'tl1', points: square(0.1, 0.1, 0.4, 0.2) },
  { id: 'w2', type: PolygonType.WORD, parentId: 'tl1', points: square(0.5, 0.1, 0.9, 0.2) },
  { id: 'g1', type: PolygonType.GLYPH, parentId: 'w1', points: square(0.1, 0.1, 0.2, 0.2) }
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

function polygon(id: string): RenderablePolygon {
  return polygons.find(p => p.id === id)!
}

describe('word and glyph view modes', () => {
  const service = new VisibilityService()

  it('accepts the new layout view modes', () => {
    expect(normalizeLayoutViewMode('word')).toBe('word')
    expect(normalizeLayoutViewMode('glyph')).toBe('glyph')
    expect(normalizeLayoutViewMode('visual')).toBeUndefined()
  })

  it('shows only words in word view and renders their ancestors as background', () => {
    const ctx = context({ viewMode: 'word' })

    expect(polygons.filter(p => service.shouldShowPolygon(p, ctx)).map(p => p.id)).toEqual(['w1', 'w2'])
    expect(polygons.filter(p => service.shouldBeSelectablePolygon(p, ctx)).map(p => p.id)).toEqual(['w1', 'w2'])
    expect(polygons.filter(p => service.shouldRenderAsBackground(p, ctx)).map(p => p.id)).toEqual(['r1', 'tl1'])
  })

  it('shows only glyphs in glyph view with words as background', () => {
    const ctx = context({ viewMode: 'glyph' })

    expect(polygons.filter(p => service.shouldShowPolygon(p, ctx)).map(p => p.id)).toEqual(['g1'])
    expect(service.shouldRenderAsBackground(polygon('w1'), ctx)).toBe(true)
    expect(service.shouldRenderAsBackground(polygon('g1'), ctx)).toBe(false)
  })

  it('exposes words as selectable children of a selected textline in hierarchy view', () => {
    const ctx = context({ selectedPolygonIndex: polygons.indexOf(polygon('tl1')) })

    expect(service.shouldBeSelectablePolygon(polygon('w1'), ctx)).toBe(true)
    expect(service.shouldBeSelectablePolygon(polygon('g1'), ctx)).toBe(false)
    expect(service.shouldShowPolygon(polygon('w2'), ctx)).toBe(true)
  })
})

describe('word and glyph hierarchy rules', () => {
  it('allows textline -> word -> glyph nesting only', () => {
    expect(isValidParentChildType(PolygonType.TEXTLINE, PolygonType.WORD)).toBe(true)
    expect(isValidParentChildType(PolygonType.WORD, PolygonType.GLYPH)).toBe(true)
    expect(isValidParentChildType(PolygonType.REGION, PolygonType.WORD)).toBe(false)
    expect(isValidParentChildType(PolygonType.TEXTLINE, PolygonType.GLYPH)).toBe(false)
    expect(isValidParentChildType(PolygonType.GLYPH, PolygonType.GLYPH)).toBe(false)
  })

  it('requires a parent for words and glyphs', () => {
    const items = polygons.map(p => ({ id: p.id, type: p.type!, parentId: p.parentId }))

    expect(validatePolygonParent(PolygonType.WORD, undefined, items).valid).toBe(false)
    expect(validatePolygonParent(PolygonType.GLYPH, 'tl1', items).valid).toBe(false)
    expect(validatePolygonParent(PolygonType.GLYPH, 'w2', items).valid).toBe(true)
  })
})

describe('word and glyph auto-parent preview', () => {
  it('highlights the textline that will receive a new word', () => {
    const preview = computeAutoParentPreview('word', true, false, square(0.4, 0.12, 0.5, 0.18), [], polygons, [], PolygonType.WORD)

    expect(preview).toMatchObject({ isExisting: true, creationLevel: 'none' })
    expect(preview?.parentPolygon?.id).toBe('tl1')
  })

  it('previews a helper word and textline for a glyph outside any word', () => {
    const preview = computeAutoParentPreview('glyph', true, false, square(0.4, 0.5, 0.45, 0.55), [], polygons, [], PolygonType.GLYPH)

    expect(preview?.helperWordPoints).toHaveLength(4)
    expect(preview?.helperTextlinePoints).toHaveLength(4)
    expect(preview?.parentPolygon?.id).toBe('r1')
    expect(preview?.creationLevel).toBe('textline')
  })
})

describe('structure tree', () => {
  it('keeps words in document order below their textline', () => {
    const reordered = [polygon('r1'), polygon('tl1'), { ...polygon('w2'), label: 'a' }, { ...polygon('w1'), label: 'z' }]
    const children = buildChildrenByParentId(reordered, [])

    expect(children.get('tl1')?.map(item => item.id)).toEqual(['w2', 'w1'])
  })
})
