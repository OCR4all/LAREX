import { describe, expect, it } from 'vitest'
import { createReparentRules, type ReparentNode } from '../reparent-rules'

function rect(minX: number, minY: number, maxX: number, maxY: number) {
  return [{ x: minX, y: minY }, { x: maxX, y: minY }, { x: maxX, y: maxY }, { x: minX, y: maxY }]
}

function region(id: string, overrides: Partial<ReparentNode> = {}): ReparentNode {
  return { id, type: 'region', regionKind: 'TextRegion', points: rect(0, 0, 10, 10), ...overrides }
}

describe('reparent rules', () => {
  it('finds the siblings that lie inside a region', () => {
    const rules = createReparentRules([
      region('image', { regionKind: 'ImageRegion', points: rect(0, 0, 100, 100) }),
      region('inside', { points: rect(10, 10, 20, 20) }),
      region('slightly-outside', { points: rect(95, 50, 100.2, 60) }),
      region('half-outside', { points: rect(90, 70, 110, 80) }),
      region('outside', { points: rect(200, 200, 210, 210) }),
      region('nested', { parentId: 'inside', points: rect(12, 12, 14, 14) })
    ])

    expect(rules.containedSiblingRegionIds('image')).toEqual(['inside', 'slightly-outside'])
    expect(rules.containedSiblingRegionIds('inside')).toEqual([])
  })

  it('rejects the element itself, its descendants and its current parent as targets', () => {
    const rules = createReparentRules([
      region('root'),
      region('child', { parentId: 'root' }),
      region('grandchild', { parentId: 'child' }),
      region('other')
    ])

    expect([...rules.targetIdsFor(['child'])]).toEqual(['other'])
    expect([...rules.targetIdsFor(['root'])]).toEqual(['other'])
    expect([...rules.targetIdsFor(['grandchild'])]).toEqual(['root', 'other'])
  })

  it('keeps the subtree of a moved region together', () => {
    const rules = createReparentRules([
      region('root'),
      region('child', { parentId: 'root' }),
      region('other')
    ])

    expect(rules.movableInto(['root', 'child'], 'other')).toEqual(['root'])
    expect(rules.movableInto(['root', 'other'], 'other')).toEqual([])
  })

  it('resolves the next level up', () => {
    const rules = createReparentRules([
      region('root'),
      region('child', { parentId: 'root' }),
      region('grandchild', { parentId: 'child' })
    ])

    expect(rules.parentIdOneLevelUp('grandchild')).toBe('root')
    expect(rules.parentIdOneLevelUp('child')).toBeNull()
    expect(rules.parentIdOneLevelUp('root')).toBeUndefined()
    expect(rules.childRegionIdsMovableOut('root')).toEqual(['child'])
  })

  it('leaves table cells and tables with cells alone', () => {
    const rules = createReparentRules([
      region('table', { regionKind: 'TableRegion' }),
      region('cell', { parentId: 'table', tableCell: { tableId: 'table' } }),
      region('empty-table', { regionKind: 'TableRegion' }),
      region('text'),
      region('in-cell', { parentId: 'cell' })
    ])

    expect(rules.isMovable('cell')).toBe(false)
    expect(rules.childRegionIdsMovableOut('table')).toEqual([])
    expect(rules.movableInto(['text'], 'table')).toEqual([])
    expect(rules.movableInto(['text'], 'empty-table')).toEqual(['text'])
    expect(rules.movableInto(['text'], 'cell')).toEqual(['text'])
    expect(rules.parentIdOneLevelUp('in-cell')).toBeUndefined()
  })

  it('moves text lines, words and glyphs only to matching parents', () => {
    const rules = createReparentRules([
      region('text'),
      region('image', { regionKind: 'ImageRegion' }),
      region('text-2'),
      { id: 'line', type: 'textline', parentId: 'text' },
      { id: 'line-2', type: 'textline', parentId: 'text-2' },
      { id: 'word', type: 'word', parentId: 'line' },
      { id: 'border', type: 'border' }
    ])

    expect([...rules.targetIdsFor(['line'])]).toEqual(['text-2'])
    expect([...rules.targetIdsFor(['word'])]).toEqual(['line-2'])
    expect(rules.movableInto(['line', 'word', 'border'], null)).toEqual([])
    expect(rules.isMovable('border')).toBe(false)
  })
})
