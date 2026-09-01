import { beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, ref } from 'vue'
import type { Point } from '@/models/editor'
import { PolygonType } from '@/models/editor'
import { LabelDefinition } from '@/models/editor/labels'
import { Commander } from '../commander'
import { CreatePolygonCommand } from '../create-polygon-command'
import { DeletePolygonCommand } from '../delete-polygon-command'
import { UpdatePolygonCommand } from '../update-polygon-command'
import {
  createMockSession,
  createTestContext,
  createTestDocument,
  createTestTextRegion,
  findRegionById
} from './test-utils'

vi.mock('@/services/visibility-service', () => ({
  visibilityService: { clearCache: vi.fn() }
}))

vi.mock('@/composables/use-geometry-cache-integrations', () => ({
  invalidatePolygonGeometry: vi.fn(),
  invalidateMultiplePolygonGeometry: vi.fn()
}))

vi.mock('@/stores/editor/editor.ui.store', () => ({
  useEditorUiStore: () => ({ bumpReadingOrderVersion: vi.fn() })
}))

function square(x = 0, y = 0, size = 100): Point[] {
  return [
    { x, y },
    { x: x + size, y },
    { x: x + size, y: y + size },
    { x, y: y + size }
  ]
}

function createRegionCommand(
  overrides: Partial<ConstructorParameters<typeof CreatePolygonCommand>[0]> = {}
): CreatePolygonCommand {
  return new CreatePolygonCommand({
    points: square(),
    type: PolygonType.REGION,
    ...overrides
  })
}

function installPolygonControls(
  session: ReturnType<typeof createMockSession>['session'],
  polygons: Array<{ id: string, type: PolygonType, points: Point[] }>,
  hiddenPolygonIds: string[] = [],
  selectedPolygonIndex = -1
) {
  session.controls.value = {
    polygons,
    polylines: [],
    selectedPolygonIndex: ref(selectedPolygonIndex),
    selectedPolylineIndex: ref(-1),
    viewMode: ref('default'),
    hiddenPolygonIds: computed(() => hiddenPolygonIds),
    hiddenPolylineIds: computed(() => [])
  } as any
}

describe('Commander integration', () => {
  let commander: Commander

  beforeEach(() => {
    commander = new Commander()
    vi.clearAllMocks()
  })

  it('tracks a complete execute, undo, and redo lifecycle', () => {
    const { session, getDocument } = createMockSession()
    const ctx = createTestContext(session)

    expect(commander.getState().totalCount).toBe(0)
    expect(commander.canUndo()).toBe(false)
    expect(commander.canRedo()).toBe(false)

    commander.execute(createRegionCommand(), ctx)
    expect(getDocument()?.page.regions).toHaveLength(1)
    expect(commander.getState().totalCount).toBe(1)
    expect(commander.canUndo()).toBe(true)
    expect(commander.canRedo()).toBe(false)

    commander.undo(ctx)
    expect(getDocument()?.page.regions).toHaveLength(0)
    expect(commander.canUndo()).toBe(false)
    expect(commander.canRedo()).toBe(true)

    commander.redo(ctx)
    expect(getDocument()?.page.regions).toHaveLength(1)
    expect(commander.canUndo()).toBe(true)
    expect(commander.canRedo()).toBe(false)
  })

  it('clears the redo branch when a new command follows undo', () => {
    const { session } = createMockSession()
    const ctx = createTestContext(session)

    commander.execute(createRegionCommand(), ctx)
    commander.execute(createRegionCommand({ points: square(200) }), ctx)
    commander.undo(ctx)
    expect(commander.canRedo()).toBe(true)

    commander.execute(createRegionCommand({ points: square(400) }), ctx)
    expect(commander.canRedo()).toBe(false)
    expect(commander.getState().totalCount).toBe(2)
  })

  it('jumps backward and forward to exact history points', () => {
    const { session, getDocument } = createMockSession()
    const ctx = createTestContext(session)

    for (let index = 0; index < 3; index++) {
      commander.execute(createRegionCommand({ points: square(index * 100, 0, 50) }), ctx)
    }

    commander.jumpToHistory(0, ctx)
    expect(getDocument()?.page.regions).toHaveLength(1)
    commander.jumpToHistory(-1, ctx)
    expect(getDocument()?.page.regions).toHaveLength(0)
    commander.jumpToHistory(2, ctx)
    expect(getDocument()?.page.regions).toHaveLength(3)
  })
})

describe('CreatePolygonCommand integration', () => {
  let commander: Commander

  beforeEach(() => {
    commander = new Commander()
    vi.clearAllMocks()
  })

  it('creates a root text region', () => {
    const { session, getDocument } = createMockSession()
    const result = commander.execute(createRegionCommand({ label: 'TestRegion' }), createTestContext(session))

    expect(result.id).toBeDefined()
    expect(getDocument()?.page.regions).toHaveLength(1)
    expect(getDocument()?.page.regions[0]?.kind).toBe('TextRegion')
  })

  it('creates a region using the supplied label mapping', () => {
    const { session, getDocument } = createMockSession()
    const imageLabel = new LabelDefinition(
      'image-label',
      'Illustration',
      'region',
      '#123456',
      '',
      false,
      false,
      null,
      {
        pageXml: {
          regionType: 'ImageRegion',
          textType: null,
          customSubType: 'photo',
          customKey: 'structure',
          customData: ''
        }
      }
    )

    commander.execute(createRegionCommand({ labelDefinition: imageLabel }), createTestContext(session))

    const region = getDocument()?.page.regions[0]
    expect(region?.kind).toBe('ImageRegion')
    expect((region as { type?: string })?.type).toBe('photo')
    expect(region?.custom).toBeUndefined()
  })

  it('creates a nested region inside its parent', () => {
    const document = createTestDocument({ regions: [createTestTextRegion({ id: 'parent-region' })] })
    const { session, getDocument } = createMockSession(document)

    commander.execute(
      createRegionCommand({ points: square(20, 20, 50), parentId: 'parent-region' }),
      createTestContext(session)
    )

    expect(getDocument()?.page.regions[0]?.regions).toHaveLength(1)
  })

  it('creates, undoes, and redoes a text line inside a text region', () => {
    const document = createTestDocument({ regions: [createTestTextRegion({ id: 'parent-region' })] })
    const { session, getDocument } = createMockSession(document)
    const ctx = createTestContext(session)

    commander.execute(new CreatePolygonCommand({
      points: square(10, 10, 20),
      type: PolygonType.TEXTLINE,
      parentId: 'parent-region'
    }), ctx)
    expect((getDocument()?.page.regions[0] as any)?.textLines).toHaveLength(1)

    commander.undo(ctx)
    expect((getDocument()?.page.regions[0] as any)?.textLines).toHaveLength(0)
    commander.redo(ctx)
    expect((getDocument()?.page.regions[0] as any)?.textLines).toHaveLength(1)
  })

  it('subtracts overlap from visible peer regions when enabled', () => {
    const existing = createTestTextRegion({ id: 'existing', points: square() })
    const { session, getDocument } = createMockSession(createTestDocument({ regions: [existing] }))
    installPolygonControls(session, [{ id: 'existing', type: PolygonType.REGION, points: square() }])

    const result = commander.execute(createRegionCommand({
      points: square(50),
      preventOverlapOnCreate: true,
      overlapMinAreaThreshold: 0
    }), createTestContext(session))

    expect(result.created).toBe(true)
    const created = findRegionById(getDocument()!.page.regions, result.id) as any
    expect(Math.min(...created.coords.points.map((point: [number, number]) => point[0]))).toBeGreaterThanOrEqual(99.999)
  })

  it('does not subtract overlap from hidden peer regions', () => {
    const existing = createTestTextRegion({ id: 'existing', points: square() })
    const { session, getDocument } = createMockSession(createTestDocument({ regions: [existing] }))
    installPolygonControls(
      session,
      [{ id: 'existing', type: PolygonType.REGION, points: square() }],
      ['existing']
    )

    const result = commander.execute(createRegionCommand({
      points: square(50),
      preventOverlapOnCreate: true,
      overlapMinAreaThreshold: 0
    }), createTestContext(session))

    const created = findRegionById(getDocument()!.page.regions, result.id) as any
    expect(Math.min(...created.coords.points.map((point: [number, number]) => point[0]))).toBeLessThanOrEqual(50.001)
  })

  it('does not subtract against ancestors during nested creation', () => {
    const parent = createTestTextRegion({ id: 'parent-region', points: square(0, 0, 200) })
    const { session, getDocument } = createMockSession(createTestDocument({ regions: [parent] }))
    installPolygonControls(
      session,
      [{ id: 'parent-region', type: PolygonType.REGION, points: square(0, 0, 200) }],
      [],
      0
    )

    const result = commander.execute(createRegionCommand({
      points: square(50),
      parentId: 'parent-region',
      preventOverlapOnCreate: true,
      overlapMinAreaThreshold: 0
    }), createTestContext(session))

    expect(result.created).toBe(true)
    const nested = (getDocument()!.page.regions[0] as any).regions[0]
    expect(Math.min(...nested.coords.points.map((point: [number, number]) => point[0]))).toBeLessThanOrEqual(50.001)
  })
})

describe('UpdatePolygonCommand integration', () => {
  it('updates coordinates and restores them on undo', () => {
    const document = createTestDocument({
      regions: [createTestTextRegion({ id: 'test-region', points: square(100, 100) })]
    })
    const { session, getDocument } = createMockSession(document)
    const ctx = createTestContext(session)
    const commander = new Commander()

    commander.execute(new UpdatePolygonCommand({
      polygonId: 'test-region',
      newPoints: square(500, 500)
    }), ctx)
    expect(getDocument()?.page.regions[0]?.coords.points.slice(0, 2)).toEqual([[500, 500], [600, 500]])

    commander.undo(ctx)
    expect(getDocument()?.page.regions[0]?.coords.points.slice(0, 2)).toEqual([[100, 100], [200, 100]])
  })
})

describe('DeletePolygonCommand integration', () => {
  it('deletes a region and restores its exact position on undo', () => {
    const document = createTestDocument({
      regions: ['region-1', 'region-2', 'region-3'].map(id => createTestTextRegion({ id }))
    })
    const { session, getDocument } = createMockSession(document)
    const ctx = createTestContext(session)
    const commander = new Commander()

    commander.execute(new DeletePolygonCommand({ polygonId: 'region-2' }), ctx)
    expect(getDocument()?.page.regions.map(region => region.id)).toEqual(['region-1', 'region-3'])

    commander.undo(ctx)
    expect(getDocument()?.page.regions.map(region => region.id)).toEqual(['region-1', 'region-2', 'region-3'])
  })
})

describe('Multi-command integration', () => {
  it('unwinds a create-update-delete workflow one command at a time', () => {
    const { session, getDocument } = createMockSession()
    const ctx = createTestContext(session)
    const commander = new Commander()
    const { id } = commander.execute(createRegionCommand(), ctx)

    commander.execute(new UpdatePolygonCommand({ polygonId: id, newPoints: square(50) }), ctx)
    commander.execute(new DeletePolygonCommand({ polygonId: id }), ctx)
    expect(getDocument()?.page.regions).toHaveLength(0)

    commander.undo(ctx)
    expect(getDocument()?.page.regions).toHaveLength(1)
    commander.undo(ctx)
    expect(getDocument()?.page.regions[0]?.coords.points[0]).toEqual([0, 0])
    commander.undo(ctx)
    expect(getDocument()?.page.regions).toHaveLength(0)
  })
})
