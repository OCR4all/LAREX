export interface CanvasTextCorrectionPlacementInput {
  anchorBounds: {
    minX: number
    maxX: number
    minY: number
    maxY: number
  }
  viewport: {
    width: number
    height: number
  }
  overlay: {
    width: number
    height: number
  }
  margin?: number
  gap?: number
}

export interface CanvasTextCorrectionPlacementResult {
  left: number
  top: number
  placement: 'below' | 'above'
}

function clamp(value: number, min: number, max: number): number {
  if (max < min) return min
  return Math.min(Math.max(value, min), max)
}

export function computeCanvasTextCorrectionPlacement(
  input: CanvasTextCorrectionPlacementInput
): CanvasTextCorrectionPlacementResult {
  const margin = Number.isFinite(input.margin) ? Math.max(0, Number(input.margin)) : 8
  const gap = Number.isFinite(input.gap) ? Math.max(0, Number(input.gap)) : 6

  const viewportWidth = Math.max(0, input.viewport.width)
  const viewportHeight = Math.max(0, input.viewport.height)
  const overlayWidth = Math.max(0, input.overlay.width)
  const overlayHeight = Math.max(0, input.overlay.height)

  const anchorWidth = Math.max(0, input.anchorBounds.maxX - input.anchorBounds.minX)
  const centeredLeft = input.anchorBounds.minX + (anchorWidth - overlayWidth) / 2
  const left = clamp(centeredLeft, margin, viewportWidth - overlayWidth - margin)

  const topBelow = input.anchorBounds.maxY + gap
  const fitsBelow = topBelow + overlayHeight <= viewportHeight - margin

  if (fitsBelow) {
    return {
      left,
      top: topBelow,
      placement: 'below'
    }
  }

  const topAbove = input.anchorBounds.minY - overlayHeight - gap
  return {
    left,
    top: clamp(topAbove, margin, viewportHeight - overlayHeight - margin),
    placement: 'above'
  }
}
