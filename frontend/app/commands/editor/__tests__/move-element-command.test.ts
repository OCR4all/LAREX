import { beforeEach, describe, expect, it, vi } from 'vitest'
import { Commander } from '../commander'
import { MoveElementCommand } from '../move-element-command'
import { Polygon, Polyline, TextLine, isTextRegion } from '@/models/editor'
import { baselineIdForTextLineId } from '@/utils/editor/pcgts-editor-primitives'
import { invalidateMultiplePolygonGeometry } from '@/composables/editor/use-geometry-cache-integrations'
import {
  createMockSession,
  createTestContext,
  createTestDocument,
  createTestTextRegion
} from './test-utils'

vi.mock('@/services/editor/visibility-service', () => ({
  visibilityService: {
    clearCache: vi.fn()
  }
}))

vi.mock('@/composables/editor/use-geometry-cache-integrations', () => ({
  invalidatePolygonGeometry: vi.fn(),
  invalidateMultiplePolygonGeometry: vi.fn()
}))

function points(): [number, number][] {
  return [
    [100, 100],
    [200, 100],
    [200, 150],
    [100, 150]
  ]
}

function createTextLineWithBaseline(id: string): TextLine {
  return new TextLine({
    id,
    coords: new Polygon(points()),
    baseline: {
      points: new Polyline([
        [110, 130],
        [190, 130]
      ])
    }
  })
}

describe('MoveElementCommand', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('adds one history entry and supports undo/redo for moved polygons', () => {
    const region = createTestTextRegion({ id: 'r1' })
    const doc = createTestDocument({ regions: [region] })
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)
    const commander = new Commander()

    commander.execute(new MoveElementCommand({
      elementId: 'r1',
      elementType: 'polygon',
      delta: { x: 10, y: 20 }
    }), ctx)

    expect(commander.getDetailedHistory()).toHaveLength(1)
    expect(getDocument()?.page.regions[0]?.coords.points[0]).toEqual([110, 120])
    expect(invalidateMultiplePolygonGeometry).toHaveBeenLastCalledWith('test-canvas', ['r1'])

    commander.undo(ctx)
    expect(getDocument()?.page.regions[0]?.coords.points[0]).toEqual([100, 100])
    expect(invalidateMultiplePolygonGeometry).toHaveBeenLastCalledWith('test-canvas', ['r1'])

    commander.redo(ctx)
    expect(getDocument()?.page.regions[0]?.coords.points[0]).toEqual([110, 120])
  })

  it('moves baselines using the same IDs emitted by the renderable polyline collector', () => {
    const textLine = createTextLineWithBaseline('tl-1')
    const region = createTestTextRegion({ id: 'r1', textLines: [textLine] })
    const doc = createTestDocument({ regions: [region] })
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)
    const commander = new Commander()

    commander.execute(new MoveElementCommand({
      elementId: baselineIdForTextLineId('tl-1'),
      elementType: 'polyline',
      delta: { x: 10, y: 20 }
    }), ctx)

    const movedRegion = getDocument()?.page.regions[0]
    const movedTextLine = movedRegion && isTextRegion(movedRegion) ? movedRegion.textLines?.[0] : null

    expect(commander.getDetailedHistory()).toHaveLength(1)
    expect(movedTextLine?.baseline?.points.points).toEqual([
      [120, 150],
      [200, 150]
    ])
    expect(invalidateMultiplePolygonGeometry).toHaveBeenLastCalledWith(
      'test-canvas',
      [baselineIdForTextLineId('tl-1')]
    )

    commander.undo(ctx)
    expect(movedTextLine?.baseline?.points.points).toEqual([
      [110, 130],
      [190, 130]
    ])

    commander.redo(ctx)
    expect(movedTextLine?.baseline?.points.points).toEqual([
      [120, 150],
      [200, 150]
    ])
    expect(invalidateMultiplePolygonGeometry).toHaveBeenLastCalledWith(
      'test-canvas',
      [baselineIdForTextLineId('tl-1')]
    )
  })

  it('restores child baselines when undoing a move-with-children command', () => {
    const textLine = createTextLineWithBaseline('tl-1')
    const region = createTestTextRegion({ id: 'r1', textLines: [textLine] })
    const doc = createTestDocument({ regions: [region] })
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)
    const commander = new Commander()

    commander.execute(new MoveElementCommand({
      elementId: 'r1',
      elementType: 'polygon',
      delta: { x: 10, y: 20 },
      moveWithChildren: true
    }), ctx)

    const movedRegion = getDocument()?.page.regions[0]
    const movedTextLine = movedRegion && isTextRegion(movedRegion) ? movedRegion.textLines?.[0] : null

    expect(movedRegion?.coords.points[0]).toEqual([110, 120])
    expect(movedTextLine?.coords.points[0]).toEqual([110, 120])
    expect(movedTextLine?.baseline?.points.points).toEqual([
      [120, 150],
      [200, 150]
    ])
    expect(invalidateMultiplePolygonGeometry).toHaveBeenLastCalledWith(
      'test-canvas',
      ['r1', 'tl-1', baselineIdForTextLineId('tl-1')]
    )

    commander.undo(ctx)

    expect(movedRegion?.coords.points[0]).toEqual([100, 100])
    expect(movedTextLine?.coords.points[0]).toEqual([100, 100])
    expect(movedTextLine?.baseline?.points.points).toEqual([
      [110, 130],
      [190, 130]
    ])
  })
})
