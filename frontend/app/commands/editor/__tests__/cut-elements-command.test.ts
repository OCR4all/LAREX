import { beforeEach, describe, expect, it, vi } from 'vitest'
import { Polygon, Polyline, TextLine, isTextRegion } from '@/models/editor'
import { CutElementsCommand } from '../cut-elements-command'
import { invalidateMultiplePolygonGeometry } from '@/composables/editor/use-geometry-cache-integrations'
import { baselineIdForTextLineId } from '@/utils/editor/pcgts-editor-primitives'
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

vi.mock('@/stores/editor/editor.ui.store', () => ({
  useEditorUiStore: () => ({
    bumpReadingOrderVersion: vi.fn()
  })
}))

function createTextLine(id: string): TextLine {
  return new TextLine({
    id,
    coords: new Polygon([
      [100, 200],
      [900, 200],
      [900, 300],
      [100, 300]
    ])
  })
}

describe('CutElementsCommand', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('cuts targeted textlines without cutting their parent region', () => {
    const textLine = createTextLine('tl-1')
    const parentRegion = createTestTextRegion({
      id: 'parent',
      points: [
        { x: 0, y: 0 },
        { x: 1000, y: 0 },
        { x: 1000, y: 1000 },
        { x: 0, y: 1000 }
      ],
      textLines: [textLine]
    })
    const originalParentPoints = parentRegion.coords.points.map(point => [...point])
    const doc = createTestDocument({ regions: [parentRegion] })
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)

    const command = new CutElementsCommand({
      mode: 'rectangle',
      targetElementIds: ['tl-1'],
      cutPoints: [
        { x: 450, y: 150 },
        { x: 550, y: 150 },
        { x: 550, y: 350 },
        { x: 450, y: 350 }
      ]
    })

    const result = command.execute(ctx)
    const region = getDocument()?.page.regions[0]

    expect(result.cutCount).toBe(1)
    expect(region?.coords.points).toEqual(originalParentPoints)
    expect(region && isTextRegion(region) ? region.textLines?.length : 0).toBe(2)
  })

  it('splits every guide line of a cut text line between the pieces', () => {
    const textLine = createTextLine('tl-1')
    const guide = (y: number) => ({ points: new Polyline([[120, y], [880, y]]) })
    textLine.ascentLine = guide(210)
    textLine.meanLine = guide(240)
    textLine.baseline = guide(270)
    textLine.descentLine = guide(290)
    const parentRegion = createTestTextRegion({
      id: 'parent',
      points: [{ x: 0, y: 0 }, { x: 1000, y: 0 }, { x: 1000, y: 1000 }, { x: 0, y: 1000 }],
      textLines: [textLine]
    })
    const { session, getDocument } = createMockSession(createTestDocument({ regions: [parentRegion] }))

    new CutElementsCommand({
      mode: 'rectangle',
      targetElementIds: ['tl-1'],
      cutPoints: [{ x: 450, y: 150 }, { x: 550, y: 150 }, { x: 550, y: 350 }, { x: 450, y: 350 }]
    }).execute(createTestContext(session))

    const region = getDocument()?.page.regions[0]
    const lines = region && isTextRegion(region) ? region.textLines ?? [] : []
    expect(lines).toHaveLength(2)
    expect(invalidateMultiplePolygonGeometry).toHaveBeenCalledWith(
      'test-canvas',
      expect.arrayContaining(lines.map(line => baselineIdForTextLineId(line.id)))
    )
    for (const line of lines) {
      for (const [key, y] of [['ascentLine', 210], ['meanLine', 240], ['baseline', 270], ['descentLine', 290]] as const) {
        const points = line[key]?.points.points ?? []
        expect(points.length, `${line.id} ${key}`).toBeGreaterThanOrEqual(2)
        expect(points.every(([, py]) => py === y)).toBe(true)
        const xs = points.map(([px]) => px)
        expect(Math.max(...xs) <= 450 || Math.min(...xs) >= 550).toBe(true)
      }
    }
  })

  it('cuts targeted child regions without cutting their root parent', () => {
    const childRegion = createTestTextRegion({
      id: 'child',
      points: [
        { x: 100, y: 200 },
        { x: 900, y: 200 },
        { x: 900, y: 300 },
        { x: 100, y: 300 }
      ]
    })
    const parentRegion = createTestTextRegion({
      id: 'parent',
      points: [
        { x: 0, y: 0 },
        { x: 1000, y: 0 },
        { x: 1000, y: 1000 },
        { x: 0, y: 1000 }
      ]
    })
    parentRegion.regions = [childRegion]
    const originalParentPoints = parentRegion.coords.points.map(point => [...point])
    const doc = createTestDocument({ regions: [parentRegion] })
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)

    const command = new CutElementsCommand({
      mode: 'rectangle',
      targetElementIds: ['child'],
      cutPoints: [
        { x: 450, y: 150 },
        { x: 550, y: 150 },
        { x: 550, y: 350 },
        { x: 450, y: 350 }
      ]
    })

    const result = command.execute(ctx)
    const region = getDocument()?.page.regions[0]

    expect(result.cutCount).toBe(1)
    expect(getDocument()?.page.regions).toHaveLength(1)
    expect(region?.coords.points).toEqual(originalParentPoints)
    expect(region?.regions).toHaveLength(2)
  })

  it('keeps relations between text lines that the cut leaves in place', () => {
    const cutLine = createTextLine('tl-1')
    const keptLine = new TextLine({
      id: 'tl-2',
      coords: new Polygon([[100, 600], [900, 600], [900, 700], [100, 700]])
    })
    const parentRegion = createTestTextRegion({
      id: 'parent',
      points: [{ x: 0, y: 0 }, { x: 1000, y: 0 }, { x: 1000, y: 1000 }, { x: 0, y: 1000 }],
      textLines: [cutLine, keptLine]
    })
    const doc = createTestDocument({
      regions: [parentRegion],
      relations: [
        { id: 'rel-kept', type: 'link', sourceRegionRef: 'tl-2', targetRegionRef: 'parent' },
        { id: 'rel-cut', type: 'link', sourceRegionRef: 'tl-1', targetRegionRef: 'tl-2' }
      ]
    })
    const { session, getDocument } = createMockSession(doc)

    new CutElementsCommand({
      mode: 'rectangle',
      targetElementIds: ['tl-1'],
      cutPoints: [{ x: 450, y: 150 }, { x: 550, y: 150 }, { x: 550, y: 350 }, { x: 450, y: 350 }]
    }).execute(createTestContext(session))

    const region = getDocument()?.page.regions[0]
    const lineIds = region && isTextRegion(region) ? region.textLines?.map(line => line.id) : []
    const relationIds = getDocument()?.page.relations?.map(relation => relation.id)
    expect(relationIds).toContain('rel-kept')
    expect(relationIds?.includes('rel-cut')).toBe(lineIds?.includes('tl-1'))
  })
})
