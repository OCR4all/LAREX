import { describe, expect, it, vi, beforeEach } from 'vitest'
import { Commander } from '../commander'
import { DeletePolygonCommand } from '../delete-polygon-command'
import { MergeElementsCommand } from '../merge-elements-command'
import { CutElementsCommand } from '../cut-elements-command'
import { CreateRelationCommand } from '../create-relation-command'
import { UpdateRelationCommand } from '../update-relation-command'
import { DeleteRelationCommand } from '../delete-relation-command'
import {
  createMockSession,
  createTestContext,
  createTestDocument,
  createTestRelation,
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

describe('Relation commands', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('creates a relation and supports undo/redo', () => {
    const r1 = createTestTextRegion({ id: 'r1' })
    const r2 = createTestTextRegion({
      id: 'r2',
      points: [
        { x: 700, y: 100 },
        { x: 1100, y: 100 },
        { x: 1100, y: 200 },
        { x: 700, y: 200 }
      ]
    })
    const doc = createTestDocument({ regions: [r1, r2] })
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)
    const commander = new Commander()

    const result = commander.execute(new CreateRelationCommand({
      relation: {
        id: 'rel-create',
        type: 'link',
        sourceRegionRef: 'r1',
        targetRegionRef: 'r2',
        custom: 'grouped'
      }
    }), ctx)

    expect(result?.id).toBe('rel-create')
    expect(getDocument()?.page.relations).toHaveLength(1)
    expect(getDocument()?.page.relations?.[0]?.custom).toBe('grouped')

    commander.undo(ctx)
    expect(getDocument()?.page.relations).toBeUndefined()

    commander.redo(ctx)
    expect(getDocument()?.page.relations).toHaveLength(1)
    expect(getDocument()?.page.relations?.[0]?.id).toBe('rel-create')
  })

  it('updates a relation and supports undo', () => {
    const r1 = createTestTextRegion({ id: 'r1' })
    const r2 = createTestTextRegion({
      id: 'r2',
      points: [
        { x: 700, y: 100 },
        { x: 1100, y: 100 },
        { x: 1100, y: 200 },
        { x: 700, y: 200 }
      ]
    })
    const r3 = createTestTextRegion({
      id: 'r3',
      points: [
        { x: 1300, y: 100 },
        { x: 1700, y: 100 },
        { x: 1700, y: 200 },
        { x: 1300, y: 200 }
      ]
    })
    const relation = createTestRelation({
      id: 'rel-update',
      sourceRegionRef: 'r1',
      targetRegionRef: 'r2',
      type: 'link'
    })
    const doc = createTestDocument({ regions: [r1, r2, r3], relations: [relation] })
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)

    const command = new UpdateRelationCommand({
      relationId: 'rel-update',
      relation: {
        ...relation,
        id: 'rel-updated',
        type: 'join',
        targetRegionRef: 'r3'
      }
    })

    command.execute(ctx)

    expect(getDocument()?.page.relations?.[0]?.id).toBe('rel-updated')
    expect(getDocument()?.page.relations?.[0]?.type).toBe('join')
    expect(getDocument()?.page.relations?.[0]?.targetRegionRef).toBe('r3')

    command.undo(ctx)

    expect(getDocument()?.page.relations?.[0]?.id).toBe('rel-update')
    expect(getDocument()?.page.relations?.[0]?.type).toBe('link')
    expect(getDocument()?.page.relations?.[0]?.targetRegionRef).toBe('r2')
  })

  it('deletes a relation and supports undo', () => {
    const relation = createTestRelation({ id: 'rel-delete' })
    const doc = createTestDocument({
      regions: [createTestTextRegion({ id: 'r1' }), createTestTextRegion({ id: 'r2' })],
      relations: [relation]
    })
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)

    const command = new DeleteRelationCommand({ relationId: 'rel-delete' })
    command.execute(ctx)

    expect(getDocument()?.page.relations).toBeUndefined()

    command.undo(ctx)
    expect(getDocument()?.page.relations?.[0]?.id).toBe('rel-delete')
  })

  it('removes dangling relations when a referenced region is deleted', () => {
    const r1 = createTestTextRegion({ id: 'r1' })
    const r2 = createTestTextRegion({
      id: 'r2',
      points: [
        { x: 700, y: 100 },
        { x: 1100, y: 100 },
        { x: 1100, y: 200 },
        { x: 700, y: 200 }
      ]
    })
    const relation = createTestRelation({ id: 'rel-delete-cleanup', sourceRegionRef: 'r1', targetRegionRef: 'r2' })
    const doc = createTestDocument({ regions: [r1, r2], relations: [relation] })
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)

    const command = new DeletePolygonCommand({ polygonId: 'r1' })
    command.execute(ctx)

    expect(getDocument()?.page.relations).toBeUndefined()

    command.undo(ctx)
    expect(getDocument()?.page.relations?.[0]?.id).toBe('rel-delete-cleanup')
  })

  it('removes dangling relations when merged regions disappear', () => {
    const r1 = createTestTextRegion({ id: 'r1' })
    const r2 = createTestTextRegion({
      id: 'r2',
      points: [
        { x: 450, y: 100 },
        { x: 850, y: 100 },
        { x: 850, y: 200 },
        { x: 450, y: 200 }
      ]
    })
    const r3 = createTestTextRegion({
      id: 'r3',
      points: [
        { x: 950, y: 100 },
        { x: 1350, y: 100 },
        { x: 1350, y: 200 },
        { x: 950, y: 200 }
      ]
    })
    const relation = createTestRelation({ id: 'rel-merge-cleanup', sourceRegionRef: 'r1', targetRegionRef: 'r3' })
    const doc = createTestDocument({ regions: [r1, r2, r3], relations: [relation] })
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)

    const command = new MergeElementsCommand({
      elementIds: ['r1', 'r2'],
      elementType: 'region'
    })
    command.execute(ctx)

    expect(getDocument()?.page.relations).toBeUndefined()

    command.undo(ctx)
    expect(getDocument()?.page.relations?.[0]?.id).toBe('rel-merge-cleanup')
  })

  it('removes dangling relations when cut deletes a region', () => {
    const r1 = createTestTextRegion({ id: 'r1' })
    const r2 = createTestTextRegion({
      id: 'r2',
      points: [
        { x: 700, y: 100 },
        { x: 1100, y: 100 },
        { x: 1100, y: 200 },
        { x: 700, y: 200 }
      ]
    })
    const relation = createTestRelation({ id: 'rel-cut-cleanup', sourceRegionRef: 'r1', targetRegionRef: 'r2' })
    const doc = createTestDocument({ regions: [r1, r2], relations: [relation] })
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)

    const command = new CutElementsCommand({
      mode: 'rectangle',
      cutPoints: [
        { x: 50, y: 50 },
        { x: 550, y: 50 },
        { x: 550, y: 250 },
        { x: 50, y: 250 }
      ]
    })

    command.execute(ctx)

    expect(getDocument()?.page.relations).toBeUndefined()

    command.undo(ctx)
    expect(getDocument()?.page.relations?.[0]?.id).toBe('rel-cut-cleanup')
  })
})
