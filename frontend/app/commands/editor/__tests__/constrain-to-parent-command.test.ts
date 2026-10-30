import { describe, expect, it, vi } from 'vitest'
import { Polygon, Polyline, TextLine } from '@/models/editor'
import { ConstrainToParentCommand } from '../constrain-to-parent-command'
import { Commander } from '../commander'
import { createMockSession, createTestContext, createTestDocument, createTestTextRegion } from './test-utils'
import { collectRenderablePolygonsFromPcGts } from '@/utils/editor/pcgts-editor-primitives'
import { areAllPointsWithinParentBounds } from '@/utils/editor/parent-constraint-utils'
import { calculatePolygonArea } from '@/utils/editor/polygon-clipping'

vi.mock('@/composables/editor/use-geometry-cache-integrations', () => ({
  invalidateMultiplePolygonGeometry: vi.fn()
}))
vi.mock('@/stores/editor/editor.ui.store', () => ({
  useEditorUiStore: () => ({ bumpReadingOrderVersion: vi.fn() })
}))

const square: [number, number][] = [[0, 0], [4, 0], [4, 4], [0, 4]]
const points = (vertices: [number, number][]) => vertices.map(([x, y]) => ({ x, y }))

function setup(linePoints: [number, number][], parentPoints = square) {
  const line = new TextLine({ id: 'line', coords: new Polygon(linePoints), comments: 'Keep this comment' })
  const parent = createTestTextRegion({ id: 'parent', points: points(parentPoints), textLines: [line] })
  const { session } = createMockSession(createTestDocument({ regions: [parent] }))
  const ctx = createTestContext(session)
  const commander = new Commander()
  const getParent = () => session.document.value!.page.regions[0]! as typeof parent
  return { line, parent, session, ctx, commander, getParent }
}

function expectLegal(session: ReturnType<typeof setup>['session']) {
  const polygons = collectRenderablePolygonsFromPcGts(session.document.value!)
  for (const polygon of polygons) {
    const parent = polygons.find(p => p.id === polygon.parentId)
    if (parent) expect(areAllPointsWithinParentBounds(polygon.points, parent)).toBe(true)
  }
}

describe('ConstrainToParentCommand', () => {
  it('clips an imported textline and baseline with undo and redo', () => {
    const h = setup([[-1, 1], [5, 1], [5, 2], [-1, 2]])
    const original = [...h.line.coords.points]
    h.line.baseline = { points: new Polyline([[-1, 1.5], [5, 1.5]]) }
    h.commander.execute(new ConstrainToParentCommand(['line']), h.ctx)
    expectLegal(h.session)
    expect(calculatePolygonArea(points(h.getParent().textLines![0]!.coords.points))).toBe(4)
    expect(h.getParent().textLines![0]!.baseline!.points.points).toEqual([[0, 1.5], [4, 1.5]])
    expect(h.getParent().coords.points).toEqual(square)
    h.commander.undo(h.ctx)
    expect(h.getParent().textLines![0]!.coords.points).toEqual(original)
    expect(h.getParent().textLines![0]!.baseline!.points.points).toEqual([[-1, 1.5], [5, 1.5]])
    h.commander.redo(h.ctx)
    expectLegal(h.session)
    expect(calculatePolygonArea(points(h.getParent().textLines![0]!.coords.points))).toBe(4)
  })

  it('preserves every disconnected intersection of a concave parent', () => {
    const h = setup([[-1, 2], [5, 2], [5, 3], [-1, 3]],
      [[0, 0], [4, 0], [4, 4], [3, 4], [3, 1], [1, 1], [1, 4], [0, 4]])
    h.commander.execute(new ConstrainToParentCommand(['line']), h.ctx)
    expect(h.getParent().textLines).toHaveLength(2)
    expect(new Set(h.getParent().textLines!.map(l => l.id)).size).toBe(2)
    expect(h.getParent().textLines!.every(l => l.comments === 'Keep this comment')).toBe(true)
    expect(h.getParent().textLines!.reduce((sum, line) => sum + calculatePolygonArea(points(line.coords.points)), 0)).toBe(2)
    expectLegal(h.session)
    h.commander.undo(h.ctx)
    expect(h.getParent().textLines).toHaveLength(1)
  })

  it.each([
    [[5, 0], [6, 0], [6, 1], [5, 1]],
    [[4, 0], [5, 0], [5, 1], [4, 1]]
  ] as [number, number][][])('removes a shape without an area of overlap and can restore it (%j)', (...vertices) => {
    const h = setup(vertices)
    h.commander.execute(new ConstrainToParentCommand(['line']), h.ctx)
    expect(h.getParent().textLines).toHaveLength(0)
    h.commander.undo(h.ctx)
    expect(h.getParent().textLines![0]!.coords.points).toEqual(vertices)
  })

  it('retains nested textlines in every piece when splitting a region', () => {
    const h = setup([[-1, 2.2], [5, 2.2], [5, 2.8], [-1, 2.8]],
      [[0, 0], [4, 0], [4, 4], [3, 4], [3, 1], [1, 1], [1, 4], [0, 4]])
    const child = createTestTextRegion({ id: 'child', points: points([[-1, 2], [5, 2], [5, 3], [-1, 3]]), textLines: [h.line] })
    const target = createTestTextRegion({ id: 'target', points: points([[-1, 2], [5, 2], [5, 3], [-1, 3]]) })
    target.regions = [child]
    h.parent.textLines = []
    h.parent.regions = [target]
    h.commander.execute(new ConstrainToParentCommand(['target']), h.ctx)
    const polygons = collectRenderablePolygonsFromPcGts(h.session.document.value!)
    expect(polygons.filter(p => p.type === 'textline')).toHaveLength(2)
    expectLegal(h.session)
    h.commander.undo(h.ctx)
    expect(collectRenderablePolygonsFromPcGts(h.session.document.value!).filter(p => p.type === 'textline')).toHaveLength(1)
  })

  it('clips multiple selected siblings in a single history entry and skips root polygons', () => {
    const h = setup([[-1, 1], [5, 1], [5, 2], [-1, 2]])
    h.parent.textLines!.push(new TextLine({ id: 'other', coords: new Polygon([[-1, 2], [5, 2], [5, 3], [-1, 3]]) }))
    h.commander.execute(new ConstrainToParentCommand(['parent', 'other', 'line']), h.ctx)
    expectLegal(h.session)
    expect(h.getParent().coords.points).toEqual(square)
    expect(h.commander.getState().totalCount).toBe(1)
    h.commander.undo(h.ctx)
    expect(h.getParent().textLines!.every(line => line.coords.points.some(([x]) => x === 5))).toBe(true)
    h.commander.redo(h.ctx)
    expectLegal(h.session)
  })

  it('constrains nested regions and their descendants against the resulting parent', () => {
    const h = setup([[-1, 1], [5, 1], [5, 2], [-1, 2]])
    const container = createTestTextRegion({ id: 'container', points: points([[1, 0], [3, 0], [3, 3], [1, 3]]) })
    container.regions = [h.parent]
    h.session.document.value!.page.regions = [container]
    h.commander.execute(new ConstrainToParentCommand(['line', 'parent']), h.ctx)
    expectLegal(h.session)
    const polygons = collectRenderablePolygonsFromPcGts(h.session.document.value!)
    expect(polygons.find(p => p.id === 'line')!.points.every(p => p.x >= 1 && p.x <= 3)).toBe(true)
    h.commander.undo(h.ctx)
    expect(collectRenderablePolygonsFromPcGts(h.session.document.value!).find(p => p.id === 'line')!.points.some(p => p.x === 5)).toBe(true)
  })
})
