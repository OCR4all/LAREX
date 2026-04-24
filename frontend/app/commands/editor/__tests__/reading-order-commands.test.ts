import { beforeEach, describe, expect, it, vi } from 'vitest'
import { Commander } from '../commander'
import { UpdateReadingOrderCommand } from '../update-reading-order-command'
import { DeletePolygonCommand } from '../delete-polygon-command'
import { MergeElementsCommand } from '../merge-elements-command'
import { CutElementsCommand } from '../cut-elements-command'
import { createMockSession, createTestContext, createTestDocument, createTestReadingOrder, createTestTextRegion } from './test-utils'

const bumpReadingOrderVersion = vi.fn()

vi.mock('@/services/editor/visibility-service', () => ({
  visibilityService: {
    clearCache: vi.fn()
  }
}))

vi.mock('@/composables/editor/use-geometry-cache-integrations', () => ({
  invalidateMultiplePolygonGeometry: vi.fn()
}))

vi.mock('@/stores/editor/editor.ui.store', () => ({
  useEditorUiStore: () => ({
    bumpReadingOrderVersion
  })
}))

describe('reading order commands', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('updates reading order through the command stack with undo/redo', () => {
    const doc = createTestDocument({
      regions: [
        createTestTextRegion({ id: 'r1' }),
        createTestTextRegion({ id: 'r2' })
      ],
      readingOrder: createTestReadingOrder(['r1'])
    })
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)
    const commander = new Commander()

    commander.execute(new UpdateReadingOrderCommand({
      readingOrder: createTestReadingOrder(['r1', 'r2'])
    }), ctx)

    expect(getDocument()?.page.readingOrder?.root.elements).toHaveLength(2)

    commander.undo(ctx)
    expect(getDocument()?.page.readingOrder?.root.elements).toHaveLength(1)
    expect(getDocument()?.page.readingOrder?.root.elements[0]).toMatchObject({ regionRef: 'r1' })

    commander.redo(ctx)
    expect(getDocument()?.page.readingOrder?.root.elements).toHaveLength(2)
    expect(bumpReadingOrderVersion).toHaveBeenCalled()
  })

  it('restores reading order membership when deleting a region and undoing', () => {
    const doc = createTestDocument({
      regions: [
        createTestTextRegion({ id: 'r1' }),
        createTestTextRegion({ id: 'r2' })
      ],
      readingOrder: createTestReadingOrder(['r1', 'r2'])
    })
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)
    const commander = new Commander()

    commander.execute(new DeletePolygonCommand({ polygonId: 'r1' }), ctx)

    expect(getDocument()?.page.readingOrder?.root.elements).toHaveLength(1)
    expect(getDocument()?.page.readingOrder?.root.elements[0]).toMatchObject({ regionRef: 'r2' })

    commander.undo(ctx)

    expect(getDocument()?.page.regions.map(region => region.id)).toContain('r1')
    expect(getDocument()?.page.readingOrder?.root.elements).toHaveLength(2)
    expect(getDocument()?.page.readingOrder?.root.elements[0]).toMatchObject({ regionRef: 'r1' })
    expect(getDocument()?.page.readingOrder?.root.elements[1]).toMatchObject({ regionRef: 'r2' })
  })

  it('places a merged region at the earliest previous reading order position', () => {
    const doc = createTestDocument({
      regions: [
        createTestTextRegion({ id: 'r1' }),
        createTestTextRegion({ id: 'r2' }),
        createTestTextRegion({ id: 'r3' })
      ],
      readingOrder: createTestReadingOrder(['r1', 'r2', 'r3'])
    })
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)

    const result = new MergeElementsCommand({
      elementIds: ['r3', 'r2'],
      elementType: 'region'
    }).execute(ctx)

    const elements = getDocument()?.page.readingOrder?.root.elements
    expect(result?.id).toBeTruthy()
    expect(elements).toHaveLength(2)
    expect(elements?.[0]).toMatchObject({ regionRef: 'r1' })
    expect(elements?.[1]).toMatchObject({ regionRef: result?.id })
  })

  it('adds split region pieces after the original reading order position', () => {
    const doc = createTestDocument({
      regions: [
        createTestTextRegion({
          id: 'r1',
          points: [
            { x: 100, y: 200 },
            { x: 900, y: 200 },
            { x: 900, y: 300 },
            { x: 100, y: 300 }
          ]
        })
      ],
      readingOrder: createTestReadingOrder(['r1'])
    })
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)

    new CutElementsCommand({
      mode: 'rectangle',
      targetElementIds: ['r1'],
      cutPoints: [
        { x: 450, y: 150 },
        { x: 550, y: 150 },
        { x: 550, y: 350 },
        { x: 450, y: 350 }
      ]
    }).execute(ctx)

    const elements = getDocument()?.page.readingOrder?.root.elements
    expect(getDocument()?.page.regions).toHaveLength(2)
    expect(elements).toHaveLength(2)
    expect(elements?.[0]).toMatchObject({ regionRef: 'r1' })
    expect((elements?.[1] as { regionRef?: string } | undefined)?.regionRef).toMatch(/^cut_/)
  })
})
