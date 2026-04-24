import { beforeEach, describe, expect, it, vi } from 'vitest'
import { Polygon, TextLine, isTextRegion } from '@/models/editor'
import { CutElementsCommand } from '../cut-elements-command'
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
})
