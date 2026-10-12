import { describe, expect, it } from 'vitest'
import { isClosedPolygonSelfIntersecting, wouldSelfIntersect } from '../hit-detection'
import type { Point } from '@/models/editor'

describe('vertex move validation', () => {
  it('checks a dense polygon in linear work', () => {
    let reads = 0
    const points = Array.from({ length: 2000 }, (_, i) => {
      const angle = i * Math.PI * 2 / 2000
      return {
        get x() {
          reads++
          return Math.cos(angle)
        },
        y: Math.sin(angle)
      }
    })
    expect(wouldSelfIntersect(points, 0, { x: 1.01, y: 0 })).toBe(false)
    expect(reads).toBeLessThan(points.length * 32)
  })

  it('matches full validation for moves of a simple polygon, including wraparound edges', () => {
    const points: Point[] = [
      { x: 0, y: 0 }, { x: 4, y: 0 }, { x: 4, y: 4 },
      { x: 2, y: 2 }, { x: 0, y: 4 }
    ]
    for (let index = 0; index < points.length; index++) {
      for (let x = -1; x <= 5; x += 0.5) {
        for (let y = -1; y <= 5; y += 0.5) {
          const next = [...points]
          next[index] = { x, y }
          expect(wouldSelfIntersect(points, index, { x, y })).toBe(isClosedPolygonSelfIntersecting(next))
        }
      }
    }
  })

  it('allows editing away from a pre-existing crossing in an imported polygon', () => {
    const points = [
      { x: 0, y: 0 }, { x: 4, y: 4 }, { x: 0, y: 4 }, { x: 4, y: 0 },
      { x: 5, y: -1 }, { x: 6, y: -1 }, { x: 6, y: -2 }, { x: 5, y: -2 }
    ]
    expect(isClosedPolygonSelfIntersecting(points)).toBe(true)
    expect(wouldSelfIntersect(points, 6, { x: 6.1, y: -2 })).toBe(false)
  })
})
