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
  const viewportTop = viewportBounds.top + padding
  const viewportBottom = viewportBounds.bottom - padding
  const extendsAbove = itemBounds.top < viewportTop
  const extendsBelow = itemBounds.bottom > viewportBottom

  if (extendsAbove && extendsBelow) {
    const itemCenter = (itemBounds.top + itemBounds.bottom) / 2
    const viewportCenter = (viewportTop + viewportBottom) / 2
    if (itemCenter < viewportCenter) return 'up'
    if (itemCenter > viewportCenter) return 'down'
    return null
  }

  if (extendsAbove) return 'up'
  if (extendsBelow) return 'down'
  return null
}

export function getVerticalScrollDirection(
  currentOffset: number,
  targetOffset: number,
  tolerance: number = 1
): VerticalVisibilityDirection {
  if (targetOffset < currentOffset - tolerance) return 'up'
  if (targetOffset > currentOffset + tolerance) return 'down'
  return null
}
