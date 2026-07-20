export const FULL_TEXT_MIN_PANE_PX = 280

export function clampFullTextSplitRatio(
  ratio: number,
  containerWidth: number,
  minimumPaneWidth = FULL_TEXT_MIN_PANE_PX
): number {
  if (!Number.isFinite(containerWidth) || containerWidth <= 0) {
    return Math.max(0.2, Math.min(0.8, ratio))
  }
  const minimumRatio = Math.min(0.45, minimumPaneWidth / containerWidth)
  return Math.max(minimumRatio, Math.min(1 - minimumRatio, ratio))
}
