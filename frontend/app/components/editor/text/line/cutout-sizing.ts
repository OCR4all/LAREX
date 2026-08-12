interface FitCutoutDimensionsOptions {
  sourceWidth: number
  sourceHeight: number
  targetHeight: number
  maxHeight?: number | null
  availableWidth?: number | null
}

export interface CutoutDimensions {
  width: number
  height: number
  scale: number
}

/**
 * Scale a text-line cutout to its preferred height while keeping the complete
 * line visible inside the available column width.
 */
export function fitCutoutDimensions({
  sourceWidth,
  sourceHeight,
  targetHeight,
  maxHeight,
  availableWidth
}: FitCutoutDimensionsOptions): CutoutDimensions {
  const width = Math.max(1, sourceWidth)
  const height = Math.max(1, sourceHeight)
  const scales = [Math.max(1, targetHeight) / height]

  if (typeof maxHeight === 'number' && Number.isFinite(maxHeight) && maxHeight > 0) {
    scales.push(maxHeight / height)
  }
  if (typeof availableWidth === 'number' && Number.isFinite(availableWidth) && availableWidth > 0) {
    scales.push(availableWidth / width)
  }

  const scale = Math.max(Number.EPSILON, Math.min(...scales))
  return {
    width: Math.max(1, Math.round(width * scale)),
    height: Math.max(1, Math.round(height * scale)),
    scale
  }
}
