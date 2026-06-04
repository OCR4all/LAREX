import type { PageData } from '@/stores/editor/types'
import { naturalCompare } from '@/utils/natural-sort'

export type PageSortMetric = 'projectOrder' | 'name' | 'confidenceMin' | 'confidenceMax' | 'confidenceMean' | 'confidenceMedian'
export type PageSortDirection = 'asc' | 'desc'
export type PageSortMode = `${PageSortMetric}:${PageSortDirection}`

export const DEFAULT_PAGE_SORT_MODE: PageSortMode = 'projectOrder:asc'

export function createPageSortOrderRequest(pages: Pick<PageData, 'id'>[]): { pageIds: string[] } {
  return {
    pageIds: pages.map(page => page.id)
  }
}

export function sortPagesForEditor(pages: PageData[], mode: PageSortMode = DEFAULT_PAGE_SORT_MODE): PageData[] {
  return pages
    .map((page, index) => ({ page, index }))
    .sort((left, right) => comparePageEntries(left, right, mode))
    .map(entry => entry.page)
}

function comparePageEntries(
  left: { page: PageData, index: number },
  right: { page: PageData, index: number },
  mode: PageSortMode
): number {
  const [metric, direction] = mode.split(':') as [PageSortMetric, PageSortDirection]
  const multiplier = direction === 'desc' ? -1 : 1

  if (metric === 'name') {
    return (naturalCompare(left.page.label ?? '', right.page.label ?? '') * multiplier) || (left.index - right.index)
  }

  if (metric === 'projectOrder') {
    return (left.index - right.index) * multiplier
  }

  const leftConfidence = getConfidenceMetric(left.page, metric)
  const rightConfidence = getConfidenceMetric(right.page, metric)
  const leftMissingConfidence = leftConfidence === null || !Number.isFinite(leftConfidence)
  const rightMissingConfidence = rightConfidence === null || !Number.isFinite(rightConfidence)
  if (leftMissingConfidence && !rightMissingConfidence) return 1
  if (!leftMissingConfidence && rightMissingConfidence) return -1

  const confidenceComparison = leftMissingConfidence && rightMissingConfidence
    ? 0
    : (leftConfidence as number) - (rightConfidence as number)

  return (confidenceComparison * multiplier)
    || naturalCompare(left.page.label ?? '', right.page.label ?? '')
    || (left.index - right.index)
}

function getConfidenceMetric(page: PageData, metric: PageSortMetric): number | null {
  const stats = page.textConfidence
  if (!stats || stats.count <= 0) return null

  switch (metric) {
    case 'confidenceMin':
      return stats.min
    case 'confidenceMax':
      return stats.max
    case 'confidenceMean':
      return stats.mean
    case 'confidenceMedian':
      return stats.median
    default:
      return null
  }
}
