import { describe, expect, it } from 'vitest'
import { computeCanvasTextCorrectionAnchorTarget, computeCanvasTextCorrectionPlacement } from '../canvas-text-correction-placement'

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

  describe('anchor target', () => {
    const viewport = { width: 800, height: 600 }

    it('keeps the previous line height and horizontal position', () => {
      const target = computeCanvasTextCorrectionAnchorTarget({
        lineBounds: { minX: 100, maxX: 500, minY: 380, maxY: 400 },
        anchor: { x: 300, y: 240 },
        viewport
      })

      expect(target).toEqual({ x: 300, y: 240 })
    })

    it('moves the line up to keep room for the overlay below it', () => {
      const target = computeCanvasTextCorrectionAnchorTarget({
        lineBounds: { minX: 100, maxX: 500, minY: 380, maxY: 400 },
        anchor: { x: 300, y: 500 },
        viewport,
        reservedBelow: 200
      })

      expect(target.y).toBe(382)
    })

    it('keeps the line visible at the top when the overlay leaves no room', () => {
      const target = computeCanvasTextCorrectionAnchorTarget({
        lineBounds: { minX: 100, maxX: 500, minY: 380, maxY: 400 },
        anchor: { x: 300, y: 240 },
        viewport,
        reservedBelow: 700
      })

      expect(target.y).toBe(18)
    })

    it('centers lines that overflow the viewport horizontally', () => {
      const target = computeCanvasTextCorrectionAnchorTarget({
        lineBounds: { minX: -50, maxX: 500, minY: 380, maxY: 400 },
        anchor: { x: 300, y: 240 },
        viewport
      })

      expect(target.x).toBe(400)
    })
  })
})
