import { describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick, reactive } from 'vue'
import { watchShapes } from '../watch-shapes'

describe('shape watches', () => {
  it('tracks replacements, reordering and removals and disposes item watches', async () => {
    const shapes = reactive([
      { id: 'first', points: [{ x: 0, y: 0 }] },
      { id: 'second', points: [{ x: 1, y: 1 }] }
    ])
    const changed = vi.fn()
    const removed = vi.fn()
    const scope = effectScope()
    scope.run(() => watchShapes(shapes, changed, removed))
    expect(changed.mock.calls.map(([shape, index]) => [shape.id, index])).toEqual([['first', 0], ['second', 1]])

    changed.mockClear()
    const oldFirst = shapes[0]!
    shapes[0] = { id: 'first', points: [{ x: 2, y: 2 }] }
    await nextTick()
    expect(removed).not.toHaveBeenCalled()
    expect(changed).toHaveBeenCalledExactlyOnceWith(shapes[0], 0)
    changed.mockClear()
    oldFirst.points[0]!.x = 99
    await nextTick()
    expect(changed).not.toHaveBeenCalled()

    shapes.reverse()
    await nextTick()
    expect(changed.mock.calls.map(([shape, index]) => [shape.id, index])).toEqual([['second', 0], ['first', 1]])

    changed.mockClear()
    shapes[1]!.points.push({ x: 3, y: 3 })
    await nextTick()
    expect(changed).toHaveBeenCalledExactlyOnceWith(shapes[1], 1)

    const removedShape = shapes.pop()!
    await nextTick()
    expect(removed).toHaveBeenLastCalledWith(removedShape)
    changed.mockClear()
    removedShape.points[0]!.y = 99
    await nextTick()
    expect(changed).not.toHaveBeenCalled()

    scope.stop()
    shapes[0]!.points[0]!.x = 99
    shapes.push({ id: 'after-disposal', points: [] })
    await nextTick()
    expect(changed).not.toHaveBeenCalled()
  })
})
