import { describe, expect, it, vi } from 'vitest'
import type { Region } from '@/models/editor'
import { createReparentCommand, ReparentElementCommand } from '../reparent-element-command'
import { createMockSession, createTestContext, createTestDocument, createTestReadingOrder, createTestTextRegion } from './test-utils'

vi.mock('@/composables/editor/use-geometry-cache-integrations', () => ({
  invalidateMultiplePolygonGeometry: vi.fn()
}))

type Tree = Array<string | [string, Tree]>

function treeOf(regions: Region[]): Tree {
  return regions.map(region => region.regions?.length ? [region.id, treeOf(region.regions)] : region.id)
}

function setup() {
  const image = createTestTextRegion({ id: 'image' })
  const inner = createTestTextRegion({ id: 'inner' })
  image.regions = [inner]
  const doc = createTestDocument({
    regions: [createTestTextRegion({ id: 'a' }), image, createTestTextRegion({ id: 'b' })],
    readingOrder: createTestReadingOrder(['b', 'a', 'inner'])
  })
  const { session } = createMockSession(doc)
  return {
    ctx: createTestContext(session),
    tree: () => treeOf(session.document.value!.page.regions),
    readingOrder: () => JSON.stringify(session.document.value!.page.readingOrder)
  }
}

describe('reparenting regions', () => {
  it('moves a region into another region and restores its position on undo', () => {
    const { ctx, tree, readingOrder } = setup()
    const before = readingOrder()
    const command = new ReparentElementCommand({ elementId: 'a', elementType: 'region', newParentId: 'image' })

    expect(command.execute(ctx)).toBe(true)
    expect(tree()).toEqual([['image', ['inner', 'a']], 'b'])
    expect(readingOrder()).toBe(before)

    command.undo(ctx)
    expect(tree()).toEqual(['a', ['image', ['inner']], 'b'])
  })

  it('moves a nested region to the top level and back', () => {
    const { ctx, tree } = setup()
    const command = new ReparentElementCommand({ elementId: 'inner', elementType: 'region', newParentId: null })

    expect(command.execute(ctx)).toBe(true)
    expect(tree()).toEqual(['a', 'image', 'b', 'inner'])

    command.undo(ctx)
    expect(tree()).toEqual(['a', ['image', ['inner']], 'b'])
  })

  it.each([
    ['itself', 'image'],
    ['a descendant', 'inner'],
    ['a missing region', 'gone']
  ])('keeps the document unchanged when the target is %s', (_label, newParentId) => {
    const { ctx, tree } = setup()
    const command = new ReparentElementCommand({ elementId: 'image', elementType: 'region', newParentId })

    expect(command.execute(ctx)).toBe(false)
    command.undo(ctx)
    expect(tree()).toEqual(['a', ['image', ['inner']], 'b'])
  })

  it('moves several regions as one undoable and repeatable step', () => {
    const { ctx, tree } = setup()
    const command = createReparentCommand([
      { id: 'a', type: 'region', newParentId: 'image' },
      { id: 'b', type: 'region', newParentId: 'image' }
    ], 'Move 2 regions')

    command.execute(ctx)
    expect(tree()).toEqual([['image', ['inner', 'a', 'b']]])
    command.undo(ctx)
    expect(tree()).toEqual(['a', ['image', ['inner']], 'b'])
    command.execute(ctx)
    expect(tree()).toEqual([['image', ['inner', 'a', 'b']]])
  })

  it('keeps a moved region when its previous parent no longer exists', () => {
    const { ctx, tree } = setup()
    const command = new ReparentElementCommand({ elementId: 'inner', elementType: 'region', newParentId: null })
    command.execute(ctx)
    ctx.session.document.value!.page.regions.splice(1, 1)

    command.undo(ctx)
    expect(tree()).toEqual(['a', 'b', 'inner'])
  })
})
