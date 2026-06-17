import { describe, expect, it } from 'vitest'
import { computeCanvasTextCorrectionPlacement } from '../canvas-text-correction-placement'

describe('canvas-text-correction-placement', () => {
  it('places overlay below anchor when there is enough space', () => {
    const result = computeCanvasTextCorrectionPlacement({
      anchorBounds: { minX: 100, maxX: 200, minY: 100, maxY: 120 },
      viewport: { width: 800, height: 600 },
      overlay: { width: 240, height: 80 }
    })

    expect(result.placement).toBe('below')
    expect(result.top).toBe(126)
  })

  it('places overlay above anchor when below would overflow', () => {
    const result = computeCanvasTextCorrectionPlacement({
      anchorBounds: { minX: 300, maxX: 420, minY: 560, maxY: 580 },
      viewport: { width: 800, height: 600 },
      overlay: { width: 240, height: 90 }
    })

    expect(result.placement).toBe('above')
    expect(result.top).toBe(464)
  })

  it('clamps overlay horizontally to viewport margins', () => {
    const leftClamped = computeCanvasTextCorrectionPlacement({
      anchorBounds: { minX: -40, maxX: 20, minY: 120, maxY: 140 },
      viewport: { width: 500, height: 400 },
      overlay: { width: 220, height: 80 }
    })
    const rightClamped = computeCanvasTextCorrectionPlacement({
      anchorBounds: { minX: 470, maxX: 520, minY: 120, maxY: 140 },
      viewport: { width: 500, height: 400 },
      overlay: { width: 220, height: 80 }
    })

    expect(leftClamped.left).toBe(8)
    expect(rightClamped.left).toBe(272)
  })
})
