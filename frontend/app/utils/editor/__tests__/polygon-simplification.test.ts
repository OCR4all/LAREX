import { describe, expect, it } from 'vitest'
import { polygonArea } from 'd3-polygon'
import { Polygon } from '@/models/editor/geometry'

describe('polygon simplification', () => {
  it.each([
    { reverse: false, closed: false },
    { reverse: true, closed: false },
    { reverse: false, closed: true },
    { reverse: true, closed: true }
  ])('simplifies the closing edge while preserving winding and closure ($reverse, $closed)', ({ reverse, closed }) => {
    const points: [number, number][] = [
      [0, 0], [0.5, 0], [1, 0], [1, 1], [0, 1], [0, 0.5]
    ]
    const ring = reverse ? [points[0]!, ...points.slice(1).reverse()] : points
    if (closed) ring.push(ring[0]!)
    const original = structuredClone(ring)

    const simplified = new Polygon(ring).simplify(0.001)

    const corners: [number, number][] = reverse
      ? [[0, 0], [0, 1], [1, 1], [1, 0]]
      : [[0, 0], [1, 0], [1, 1], [0, 1]]
    expect(simplified.points).toEqual(closed ? [...corners, corners[0]] : corners)
    expect(Math.sign(polygonArea(simplified.points))).toBe(Math.sign(polygonArea(ring)))
    expect(ring).toEqual(original)
  })

  it('uses the existing coordinate units for distance tolerance', () => {
    const polygon = new Polygon([[0, 0], [0.5, 0.0005], [1, 0], [1, 1], [0, 1], [0, 0.5]])

    expect(polygon.simplify(0.0001).points).toContainEqual([0.5, 0.0005])
    expect(polygon.simplify(0.001).points).toEqual([[0, 0], [1, 0], [1, 1], [0, 1]])
  })

  it('keeps a narrow concave channel at a tolerance below its width', () => {
    const polygon = new Polygon([
      [0, 0], [1, 0], [2, 0], [2, 4], [1.1, 4],
      [1.1, 0.2], [0.9, 0.2], [0.9, 4], [0, 4], [0, 2]
    ])

    const simplified = polygon.simplify(0.1)

    expect(simplified.points).toHaveLength(8)
    expect(simplified.getArea()).toBeCloseTo(polygon.getArea())
    expect(simplified.containsPoint(1, 3)).toBe(false)
    expect(simplified.containsPoint(0.5, 3)).toBe(true)
    expect(simplified.containsPoint(1.5, 3)).toBe(true)
  })

  it('removes repeated vertices without collapsing a valid polygon at high tolerance', () => {
    const polygon = new Polygon([[0, 0], [0, 0], [1, 0], [2, 0], [2, 2], [0, 2], [0, 0]])

    const simplified = polygon.simplify(10)

    expect(simplified.points).toEqual([[0, 0], [2, 0], [2, 2], [0, 2], [0, 0]])
    expect(simplified.getArea()).toBeGreaterThan(0)
  })

  it.each<{ name: string, points: [number, number][] }>([
    { name: 'empty inputs', points: [] },
    { name: 'one point', points: [[0, 0]] },
    { name: 'two points', points: [[0, 0], [1, 0]] },
    { name: 'triangles', points: [[0, 0], [1, 0], [0, 1]] },
    { name: 'collinear points', points: [[0, 0], [1, 0], [2, 0], [3, 0]] },
    { name: 'coincident points', points: [[0, 0], [0, 0], [0, 0], [0, 0]] },
    { name: 'self-intersecting rings', points: [[0, 0], [2, 2], [0, 2], [2, 0]] }
  ])('leaves $name unchanged', ({ points }) => {
    const polygon = new Polygon(points)
    expect(polygon.simplify(10).points).toEqual(points)
  })
})
