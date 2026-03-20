import { beforeEach, describe, expect, it, vi } from 'vitest'
import { Commander } from '../commander'
import { UpdateReadingOrderCommand } from '../update-reading-order-command'
import { DeletePolygonCommand } from '../delete-polygon-command'
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
})
