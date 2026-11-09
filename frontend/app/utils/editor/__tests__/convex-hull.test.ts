import { expect, it } from 'vitest'
import { computeConvexHull } from '../convex-hull'

it('preserves editor winding and starting vertex without mutating the input', () => {
  const points: [number, number][] = [
    [2, 2], [0, 2], [1, 1], [2, 0], [0, 0], [2, 2], [0, 0]
  ]
  const original = structuredClone(points)
  const expected = [[0, 0], [2, 0], [2, 2], [0, 2]]

  expect(computeConvexHull(points)).toEqual(expected)
  expect(computeConvexHull([...points].reverse())).toEqual(expected)
  expect(points).toEqual(original)
})

it.each<{ name: string, points: [number, number][], expected: [number, number][] }>([
  { name: 'empty inputs', points: [], expected: [] },
  { name: 'one point', points: [[2, 3]], expected: [[2, 3]] },
  { name: 'two points in their original order', points: [[2, 3], [0, 0]], expected: [[2, 3], [0, 0]] },
  { name: 'collinear endpoints', points: [[2, 2], [0, 0], [1, 1], [2, 2]], expected: [[0, 0], [2, 2]] },
  { name: 'coincident points', points: [[2, 3], [2, 3], [2, 3]], expected: [[2, 3], [2, 3]] }
])('preserves the existing result for $name', ({ points, expected }) => {
  expect(computeConvexHull(points)).toEqual(expected)
})
