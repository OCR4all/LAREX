import { beforeEach, describe, expect, it, vi } from 'vitest'
import { Commander } from '../commander'
import { SetPageFrameCommand } from '../set-page-frame-command'
import { UpdatePolygonCommand } from '../update-polygon-command'
import { MoveElementCommand } from '../move-element-command'
import { DeleteSelectedElementsCommand } from '../delete-selected-elements-command'
import { PolygonType } from '@/models/editor'
import { Polygon } from '@/models/editor/geometry'
import { collectRenderablePolygonsFromPcGts } from '@/utils/editor/pcgts-editor-primitives'
import { BORDER_POLYGON_ID, PRINT_SPACE_POLYGON_ID } from '@/utils/editor/page-frame'
import { createMockSession, createTestContext, createTestDocument, createTestTextRegion } from './test-utils'

vi.mock('@/services/editor/visibility-service', () => ({
  visibilityService: {
    clearCache: vi.fn()
  }
}))

vi.mock('@/composables/editor/use-geometry-cache-integrations', () => ({
  invalidatePolygonGeometry: vi.fn(),
  invalidateMultiplePolygonGeometry: vi.fn()
}))

const rectangle = (minX: number, minY: number, maxX: number, maxY: number) => [
  { x: minX, y: minY },
  { x: maxX, y: minY },
  { x: maxX, y: maxY },
  { x: minX, y: maxY }
]

describe('page frame commands', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('collects Border and PrintSpace after the region hierarchy', () => {
    const doc = createTestDocument({ regions: [createTestTextRegion({ id: 'r1' })] })
    doc.page.border = { coords: new Polygon([[0, 0], [10, 0], [10, 10]], 0.4) }
    doc.page.printSpace = { coords: new Polygon([[1, 1], [9, 1], [9, 9]]) }

    const polygons = collectRenderablePolygonsFromPcGts(doc)

    expect(polygons.map(p => [p.id, p.type, p.parentId])).toEqual([
      ['r1', PolygonType.REGION, undefined],
      [BORDER_POLYGON_ID, PolygonType.BORDER, undefined],
      [PRINT_SPACE_POLYGON_ID, PolygonType.PRINT_SPACE, undefined]
    ])
    expect(polygons[1]?.confidence).toBe(0.4)
  })

  it('creates and replaces a frame with undo restoring the previous one', () => {
    const { session, getDocument } = createMockSession(createTestDocument())
    const ctx = createTestContext(session)
    const commander = new Commander()

    const created = commander.execute(new SetPageFrameCommand({ kind: PolygonType.BORDER, points: rectangle(0, 0, 100, 100) }), ctx)
    expect(created).toEqual({ id: BORDER_POLYGON_ID, created: true })
    const original = getDocument()!.page.border

    commander.execute(new SetPageFrameCommand({ kind: PolygonType.BORDER, points: rectangle(10, 10, 90, 90) }), ctx)
    expect(getDocument()!.page.border?.coords.points[0]).toEqual([10, 10])

    commander.undo(ctx)
    expect(getDocument()!.page.border).toBe(original)

    commander.undo(ctx)
    expect(getDocument()!.page.border).toBeUndefined()
  })

  it('reshapes and moves frames through the generic polygon commands', () => {
    const doc = createTestDocument()
    doc.page.printSpace = { coords: new Polygon([[0, 0], [10, 0], [10, 10], [0, 10]], 0.7) }
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)
    const commander = new Commander()

    commander.execute(new UpdatePolygonCommand({ polygonId: PRINT_SPACE_POLYGON_ID, newPoints: rectangle(0, 0, 20, 20) }), ctx)
    expect(getDocument()!.page.printSpace?.coords.points[2]).toEqual([20, 20])
    expect(getDocument()!.page.printSpace?.coords.conf).toBe(0.7)

    commander.execute(new MoveElementCommand({ elementId: PRINT_SPACE_POLYGON_ID, elementType: 'polygon', delta: { x: 5, y: -5 } }), ctx)
    expect(getDocument()!.page.printSpace?.coords.points[0]).toEqual([5, -5])

    commander.undo(ctx)
    commander.undo(ctx)
    expect(getDocument()!.page.printSpace?.coords.points[2]).toEqual([10, 10])
  })

  it('deletes a frame without touching regions and restores it on undo', () => {
    const doc = createTestDocument({ regions: [createTestTextRegion({ id: 'r1' })] })
    const border = { coords: new Polygon([[0, 0], [10, 0], [10, 10]]) }
    doc.page.border = border
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)
    const commander = new Commander()

    commander.execute(new DeleteSelectedElementsCommand({ polygonIds: [BORDER_POLYGON_ID] }), ctx)
    expect(getDocument()!.page.border).toBeUndefined()
    expect(getDocument()!.page.regions.map(r => r.id)).toEqual(['r1'])

    commander.undo(ctx)
    expect(getDocument()!.page.border).toBe(border)
  })
})
