type TabHeaderRect = Pick<DOMRect, 'left' | 'top' | 'bottom'>

type TabScrollMetrics = {
  scrollLeft: number
  viewportWidth: number
  tabLeft: number
  tabWidth: number
}

export function isDetachedTabHeader(page: TabHeaderRect, parent: TabHeaderRect): boolean {
  return Math.abs(page.left - parent.left) > 1 || Math.abs(page.top - parent.bottom) > 1
}

export function getScrollLeftToRevealTab(metrics: TabScrollMetrics): number {
  if (metrics.tabLeft < metrics.scrollLeft) return metrics.tabLeft
  const tabRight = metrics.tabLeft + metrics.tabWidth
  return tabRight > metrics.scrollLeft + metrics.viewportWidth
    ? tabRight - metrics.viewportWidth
    : metrics.scrollLeft
}
