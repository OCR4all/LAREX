import { describe, expect, it } from 'vitest'
import { reactive, toRaw } from 'vue'
import { reconcileShapes } from '../reconcile-shapes'

interface TestShape {
  id: string
  parentId?: string
  label?: string
  points: Array<{ x: number, y: number }>
}

function project(overrides: Record<string, Partial<TestShape>> = {}): TestShape[] {
  return ['a', 'b', 'c'].map((id, index) => ({
    id,
    parentId: undefined,
    label: id,
    points: [{ x: index, y: 0 }, { x: index, y: 1 }],
    ...overrides[id]
  }))
}

describe('reconcileShapes', () => {
  it('keeps unchanged shapes and replaces only edited ones', () => {
    const shapes = reactive<TestShape[]>([])
    expect(reconcileShapes(shapes, project())).toBe(true)
    const [a, b, c] = toRaw(shapes)

    expect(reconcileShapes(shapes, project())).toBe(false)
    expect(toRaw(shapes)).toEqual([a, b, c])
    expect(toRaw(shapes)[0]).toBe(a)

    expect(reconcileShapes(shapes, project({ b: { points: [{ x: 9, y: 9 }] }, c: { label: 'renamed' } }))).toBe(true)
    expect(toRaw(shapes)[0]).toBe(a)
    expect(toRaw(shapes)[1]).not.toBe(b)
    expect(toRaw(shapes)[2]).not.toBe(c)
    expect(shapes[1]!.points).toEqual([{ x: 9, y: 9 }])
    expect(shapes[2]!.label).toBe('renamed')
  })

  it('overwrites points that were edited in place', () => {
    const shapes = reactive<TestShape[]>([])
    reconcileShapes(shapes, project())
    shapes[0]!.points[0] = { x: 5, y: 5 }

    expect(reconcileShapes(shapes, project())).toBe(true)
    expect(shapes[0]!.points[0]).toEqual({ x: 0, y: 0 })
  })

  it('follows additions, removals and reordering', () => {
    const shapes = reactive<TestShape[]>([])
    reconcileShapes(shapes, project())
    const [a, b, c] = toRaw(shapes)

    expect(reconcileShapes(shapes, [...project()].reverse())).toBe(true)
    expect(toRaw(shapes)).toEqual([c, b, a])
    expect(toRaw(shapes)[0]).toBe(c)

    expect(reconcileShapes(shapes, project().slice(0, 2))).toBe(true)
    expect(toRaw(shapes).map(shape => shape.id)).toEqual(['a', 'b'])
    expect(toRaw(shapes)[0]).toBe(a)
  })
})
