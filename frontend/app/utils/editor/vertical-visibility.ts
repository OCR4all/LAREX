export type VerticalVisibilityDirection = 'up' | 'down' | null

type VerticalBounds = {
  top: number
  bottom: number
}

export function getVerticalVisibilityDirection(
  itemBounds: VerticalBounds,
  viewportBounds: VerticalBounds,
  padding: number = 0
): VerticalVisibilityDirection {
  if (itemBounds.top < viewportBounds.top + padding) return 'up'
  if (itemBounds.bottom > viewportBounds.bottom - padding) return 'down'
  return null
}
