import { describe, expect, it } from 'vitest'
import { Polygon } from '@/models/editor/geometry'
import { bufferPolygonInPixels, getBufferDistanceRange, isBufferedPolygonValid } from '../buffer-polygon'
import { worldToImage } from '../coordinates'

const imageSize = { width: 1000, height: 2000 }
const points: [number, number][] = [[-0.5, 0.5], [0.5, 0.5], [0.5, -0.5], [-0.5, -0.5]]

describe('pixel-distance polygon buffer', () => {
  it.each([false, true])('expands and shrinks with reversed winding: %s', (reverse) => {
    const original = new Polygon(reverse ? [...points].reverse() : points)
    for (const distance of [-10, 10]) {
      const buffered = bufferPolygonInPixels(original, distance, imageSize)
      if (distance > 0) expect(buffered.getArea()).toBeGreaterThan(original.getArea())
      else expect(buffered.getArea()).toBeLessThan(original.getArea())
      buffered.points.forEach(([x, y], index) => {
        const [ox, oy] = original.points[index]!
        const before = worldToImage({ x: ox, y: oy }, imageSize)
        const after = worldToImage({ x, y }, imageSize)
        expect(Math.hypot(after.x - before.x, after.y - before.y)).toBeCloseTo(Math.abs(distance))
      })
    }
  })

  it('leaves zero-distance geometry unchanged', () => {
    expect(bufferPolygonInPixels(new Polygon(points), 0, imageSize).points).toEqual(points)
  })
})

describe('adaptive buffer distance range', () => {
  it('stops shrink before a thin polygon collapses, even if it becomes simple again later', () => {
    const original = new Polygon([[-0.8, 0.1], [0.8, 0.1], [0.8, -0.1], [-0.8, -0.1]])
    const range = getBufferDistanceRange(original, { width: 1000, height: 1000 }, () => true)
    expect(range.min).toBe(-70)
    expect(range.max).toBe(1414)
    const collapsed = bufferPolygonInPixels(original, -100, { width: 1000, height: 1000 })
    expect(isBufferedPolygonValid(
      original.points.map(([x, y]) => ({ x, y })),
      collapsed.points.map(([x, y]) => ({ x, y }))
    )).toBe(false)
  })

  it('provides no range before image dimensions are available', () => {
    expect(getBufferDistanceRange(new Polygon(points), { width: 0, height: 0 }, () => true)).toEqual({ min: 0, max: 0 })
  })

  it('stops at the first disallowed distance rather than skipping a gap', () => {
    const range = getBufferDistanceRange(new Polygon(points), imageSize, (_points, distance) => distance !== 120)
    expect(range.max).toBe(119)
  })
})
