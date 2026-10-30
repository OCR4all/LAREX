import { describe, expect, it } from 'vitest'
import { areAllPointsWithinParentBounds, isPointWithinParentBounds } from '../parent-constraint-utils'

const parent = { id: 'parent', points: [{ x: 0, y: 0 }, { x: 1, y: 0 }, { x: 1, y: 1 }, { x: 0, y: 1 }] }

describe('parent boundaries', () => {
  it('accepts all edges and corners produced by clipping', () => {
    expect(areAllPointsWithinParentBounds(parent.points, parent)).toBe(true)
    for (const point of [{ x: 1, y: 0.5 }, { x: 0.5, y: 1 }, { x: 0, y: 0.5 }, { x: 0.5, y: 0 }]) {
      expect(isPointWithinParentBounds(point, parent)).toBe(true)
    }
  })

  it('still rejects points outside the parent or beyond an edge endpoint', () => {
    expect(isPointWithinParentBounds({ x: 1.01, y: 0.5 }, parent)).toBe(false)
    expect(isPointWithinParentBounds({ x: 2, y: 1 }, parent)).toBe(false)
    expect(areAllPointsWithinParentBounds([{ x: 0.5, y: 0.5 }, { x: -0.1, y: 0.5 }], parent)).toBe(false)
  })
})
