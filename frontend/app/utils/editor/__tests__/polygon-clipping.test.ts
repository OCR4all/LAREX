import { describe, expect, it } from 'vitest'
import { unionPolygons } from '../polygon-clipping'

describe('polygon clipping utilities', () => {
  it('uses a convex hull instead of a bounding box for disconnected polygon unions', () => {
    const result = unionPolygons([
      [
        { x: 0, y: 0 },
        { x: 2, y: 0 },
        { x: 2, y: 2 },
        { x: 0, y: 2 }
      ],
      [
        { x: 10, y: 10 },
        { x: 12, y: 10 },
        { x: 12, y: 12 },
        { x: 10, y: 12 }
      ]
    ])

    expect(result).toEqual([
      { x: 0, y: 0 },
      { x: 2, y: 0 },
      { x: 12, y: 10 },
      { x: 12, y: 12 },
      { x: 10, y: 12 },
      { x: 0, y: 2 }
    ])
  })
})
