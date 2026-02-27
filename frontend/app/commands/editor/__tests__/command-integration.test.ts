import { describe, it, expect, beforeEach, vi } from 'vitest'
import { Commander } from '../commander'
import { CreatePolygonCommand } from '../create-polygon-command'
import { DeletePolygonCommand } from '../delete-polygon-command'
import { UpdatePolygonCommand } from '../update-polygon-command'
import { PolygonType } from '@/models/editor'
import {
  createMockSession,
  createTestContext,
  createTestDocument,
  createTestTextRegion,
  countTotalRegions,
  findRegionById
} from './test-utils'

vi.mock('@/services/visibility-service', () => ({
  visibilityService: {
    clearCache: vi.fn()
  }
}))

vi.mock('@/composables/use-geometry-cache-integrations', () => ({
  invalidatePolygonGeometry: vi.fn(),
  invalidateMultiplePolygonGeometry: vi.fn()
}))

vi.mock('@/stores/editor.ui.store', () => ({
  useEditorUiStore: () => ({
    bumpReadingOrderVersion: vi.fn()
  })
}))

describe('Commander Integration Tests', () => {
  let commander: Commander

  beforeEach(() => {
    commander = new Commander()
    vi.clearAllMocks()
  })

  describe('Basic undo/redo functionality', () => {
    it('should start with empty history', () => {
      expect(commander.canUndo()).toBe(false)
      expect(commander.canRedo()).toBe(false)
      expect(commander.getState().totalCount).toBe(0)
    })

    it('should track commands after execution', () => {
      const { session } = createMockSession()
      const ctx = createTestContext(session)

      const command = new CreatePolygonCommand({
        points: [
          { x: 0, y: 0 },
          { x: 100, y: 0 },
          { x: 100, y: 100 },
          { x: 0, y: 100 }
        ],
        type: PolygonType.REGION
      })

      commander.execute(command, ctx)

      expect(commander.canUndo()).toBe(true)
      expect(commander.canRedo()).toBe(false)
      expect(commander.getState().totalCount).toBe(1)
    })

    it('should allow undo after execution', () => {
      const { session, getDocument } = createMockSession()
      const ctx = createTestContext(session)

      const initialRegionCount = getDocument()?.page.regions.length ?? 0

      const command = new CreatePolygonCommand({
        points: [
          { x: 0, y: 0 },
          { x: 100, y: 0 },
          { x: 100, y: 100 },
          { x: 0, y: 100 }
        ],
        type: PolygonType.REGION
      })

      commander.execute(command, ctx)

      expect(getDocument()?.page.regions.length).toBe(initialRegionCount + 1)

      commander.undo(ctx)

      expect(getDocument()?.page.regions.length).toBe(initialRegionCount)
      expect(commander.canUndo()).toBe(false)
      expect(commander.canRedo()).toBe(true)
    })

    it('should allow redo after undo', () => {
      const { session, getDocument } = createMockSession()
      const ctx = createTestContext(session)

      const command = new CreatePolygonCommand({
        points: [
          { x: 0, y: 0 },
          { x: 100, y: 0 },
          { x: 100, y: 100 },
          { x: 0, y: 100 }
        ],
        type: PolygonType.REGION
      })

      commander.execute(command, ctx)
      commander.undo(ctx)

      expect(getDocument()?.page.regions.length).toBe(0)

      commander.redo(ctx)

      expect(getDocument()?.page.regions.length).toBe(1)
      expect(commander.canUndo()).toBe(true)
      expect(commander.canRedo()).toBe(false)
    })
  })

  describe('Command branching', () => {
    it('should clear redo stack when new command is executed after undo', () => {
      const { session } = createMockSession()
      const ctx = createTestContext(session)

      commander.execute(
        new CreatePolygonCommand({
          points: [{ x: 0, y: 0 }, { x: 100, y: 0 }, { x: 100, y: 100 }, { x: 0, y: 100 }],
          type: PolygonType.REGION
        }),
        ctx
      )

      commander.execute(
        new CreatePolygonCommand({
          points: [{ x: 200, y: 0 }, { x: 300, y: 0 }, { x: 300, y: 100 }, { x: 200, y: 100 }],
          type: PolygonType.REGION
        }),
        ctx
      )

      expect(commander.getState().totalCount).toBe(2)

      commander.undo(ctx)
      expect(commander.canRedo()).toBe(true)

      commander.execute(
        new CreatePolygonCommand({
          points: [{ x: 400, y: 0 }, { x: 500, y: 0 }, { x: 500, y: 100 }, { x: 400, y: 100 }],
          type: PolygonType.REGION
        }),
        ctx
      )

      expect(commander.canRedo()).toBe(false)
      expect(commander.getState().totalCount).toBe(2) // Original + new (branch)
    })
  })

  describe('History navigation', () => {
    it('should jump to specific history point', () => {
      const { session, getDocument } = createMockSession()
      const ctx = createTestContext(session)

      for (let i = 0; i < 3; i++) {
        commander.execute(
          new CreatePolygonCommand({
            points: [
              { x: i * 100, y: 0 },
              { x: i * 100 + 50, y: 0 },
              { x: i * 100 + 50, y: 50 },
              { x: i * 100, y: 50 }
            ],
            type: PolygonType.REGION
          }),
          ctx
        )
      }

      expect(getDocument()?.page.regions.length).toBe(3)

      commander.jumpToHistory(0, ctx)
      expect(getDocument()?.page.regions.length).toBe(1)

      commander.jumpToHistory(-1, ctx)
      expect(getDocument()?.page.regions.length).toBe(0)

      commander.jumpToHistory(2, ctx)
      expect(getDocument()?.page.regions.length).toBe(3)
    })
  })
})

describe('Create Polygon Command Integration', () => {
  let commander: Commander

  beforeEach(() => {
    commander = new Commander()
    vi.clearAllMocks()
  })

  it('should create a region at root level', () => {
    const { session, getDocument } = createMockSession()
    const ctx = createTestContext(session)

    const result = commander.execute(
      new CreatePolygonCommand({
        points: [
          { x: 100, y: 100 },
          { x: 500, y: 100 },
          { x: 500, y: 300 },
          { x: 100, y: 300 }
        ],
        type: PolygonType.REGION,
        label: 'TestRegion'
      }),
      ctx
    )

    expect(result.id).toBeDefined()
    expect(getDocument()?.page.regions.length).toBe(1)

    const region = getDocument()?.page.regions[0]
    expect(region?.kind).toBe('TextRegion')
  })

  it('should create a nested region inside parent', () => {
    const parentRegion = createTestTextRegion({ id: 'parent-region' })
    const doc = createTestDocument({ regions: [parentRegion] })
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)

    commander.execute(
      new CreatePolygonCommand({
        points: [
          { x: 150, y: 120 },
          { x: 400, y: 120 },
          { x: 400, y: 180 },
          { x: 150, y: 180 }
        ],
        type: PolygonType.REGION,
        parentId: 'parent-region'
      }),
      ctx
    )

    const parent = getDocument()?.page.regions[0]
    expect(parent?.regions?.length).toBe(1)
  })

  it('should create a TextLine inside a TextRegion', () => {
    const parentRegion = createTestTextRegion({ id: 'parent-region' })
    const doc = createTestDocument({ regions: [parentRegion] })
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)

    commander.execute(
      new CreatePolygonCommand({
        points: [
          { x: 110, y: 110 },
          { x: 490, y: 110 },
          { x: 490, y: 130 },
          { x: 110, y: 130 }
        ],
        type: PolygonType.TEXTLINE,
        parentId: 'parent-region'
      }),
      ctx
    )

    const parent = getDocument()?.page.regions[0] as any
    expect(parent?.textLines?.length).toBe(1)
  })

  it('should properly undo TextLine creation', () => {
    const parentRegion = createTestTextRegion({ id: 'parent-region' })
    const doc = createTestDocument({ regions: [parentRegion] })
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)

    commander.execute(
      new CreatePolygonCommand({
        points: [
          { x: 110, y: 110 },
          { x: 490, y: 110 },
          { x: 490, y: 130 },
          { x: 110, y: 130 }
        ],
        type: PolygonType.TEXTLINE,
        parentId: 'parent-region'
      }),
      ctx
    )

    const parent = getDocument()?.page.regions[0] as any
    expect(parent?.textLines?.length).toBe(1)

    commander.undo(ctx)

    const parentAfterUndo = getDocument()?.page.regions[0] as any
    expect(parentAfterUndo?.textLines?.length).toBe(0)
  })
})

describe('Update Polygon Command Integration', () => {
  let commander: Commander

  beforeEach(() => {
    commander = new Commander()
    vi.clearAllMocks()
  })

  it('should update region coordinates', () => {
    const region = createTestTextRegion({
      id: 'test-region',
      points: [
        { x: 100, y: 100 },
        { x: 200, y: 100 },
        { x: 200, y: 200 },
        { x: 100, y: 200 }
      ]
    })
    const doc = createTestDocument({ regions: [region] })
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)

    const newPoints = [
      { x: 150, y: 150 },
      { x: 300, y: 150 },
      { x: 300, y: 300 },
      { x: 150, y: 300 }
    ]

    commander.execute(
      new UpdatePolygonCommand({
        polygonId: 'test-region',
        newPoints
      }),
      ctx
    )

    const updatedRegion = getDocument()?.page.regions[0]
    expect(updatedRegion?.coords.points[0]).toEqual([150, 150])
    expect(updatedRegion?.coords.points[1]).toEqual([300, 150])
  })

  it('should restore original coordinates on undo', () => {
    const originalPoints = [
      { x: 100, y: 100 },
      { x: 200, y: 100 },
      { x: 200, y: 200 },
      { x: 100, y: 200 }
    ]

    const region = createTestTextRegion({
      id: 'test-region',
      points: originalPoints
    })
    const doc = createTestDocument({ regions: [region] })
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)

    commander.execute(
      new UpdatePolygonCommand({
        polygonId: 'test-region',
        newPoints: [
          { x: 500, y: 500 },
          { x: 600, y: 500 },
          { x: 600, y: 600 },
          { x: 500, y: 600 }
        ]
      }),
      ctx
    )

    commander.undo(ctx)

    const restoredRegion = getDocument()?.page.regions[0]
    expect(restoredRegion?.coords.points[0]).toEqual([100, 100])
    expect(restoredRegion?.coords.points[1]).toEqual([200, 100])
  })
})

describe('Delete Polygon Command Integration', () => {
  let commander: Commander

  beforeEach(() => {
    commander = new Commander()
    vi.clearAllMocks()
  })

  it('should delete a region', () => {
    const region = createTestTextRegion({ id: 'to-delete' })
    const doc = createTestDocument({ regions: [region] })
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)

    expect(getDocument()?.page.regions.length).toBe(1)

    commander.execute(
      new DeletePolygonCommand({ polygonId: 'to-delete' }),
      ctx
    )

    expect(getDocument()?.page.regions.length).toBe(0)
  })

  it('should restore region on undo', () => {
    const region = createTestTextRegion({ id: 'to-delete' })
    const doc = createTestDocument({ regions: [region] })
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)

    commander.execute(
      new DeletePolygonCommand({ polygonId: 'to-delete' }),
      ctx
    )

    expect(getDocument()?.page.regions.length).toBe(0)

    commander.undo(ctx)

    expect(getDocument()?.page.regions.length).toBe(1)
    expect(getDocument()?.page.regions[0]?.id).toBe('to-delete')
  })

  it('should preserve region position in array on undo', () => {
    const regions = [
      createTestTextRegion({ id: 'region-1' }),
      createTestTextRegion({ id: 'region-2' }),
      createTestTextRegion({ id: 'region-3' })
    ]
    const doc = createTestDocument({ regions })
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)

    commander.execute(
      new DeletePolygonCommand({ polygonId: 'region-2' }),
      ctx
    )

    expect(getDocument()?.page.regions.length).toBe(2)

    commander.undo(ctx)

    const restoredRegions = getDocument()?.page.regions
    expect(restoredRegions?.length).toBe(3)
    expect(restoredRegions?.[1]?.id).toBe('region-2')
  })
})

describe('Complex Multi-Command Workflows', () => {
  let commander: Commander

  beforeEach(() => {
    commander = new Commander()
    vi.clearAllMocks()
  })

  it('should handle create-update-delete-undo sequence', () => {
    const { session, getDocument } = createMockSession()
    const ctx = createTestContext(session)

    const result = commander.execute(
      new CreatePolygonCommand({
        points: [
          { x: 0, y: 0 },
          { x: 100, y: 0 },
          { x: 100, y: 100 },
          { x: 0, y: 100 }
        ],
        type: PolygonType.REGION
      }),
      ctx
    )
    const regionId = result.id

    commander.execute(
      new UpdatePolygonCommand({
        polygonId: regionId,
        newPoints: [
          { x: 50, y: 50 },
          { x: 150, y: 50 },
          { x: 150, y: 150 },
          { x: 50, y: 150 }
        ]
      }),
      ctx
    )

    commander.execute(
      new DeletePolygonCommand({ polygonId: regionId }),
      ctx
    )

    expect(getDocument()?.page.regions.length).toBe(0)

    commander.undo(ctx)
    expect(getDocument()?.page.regions.length).toBe(1)

    commander.undo(ctx)
    const region = getDocument()?.page.regions[0]
    expect(region?.coords.points[0]).toEqual([0, 0])

    commander.undo(ctx)
    expect(getDocument()?.page.regions.length).toBe(0)
  })
})
