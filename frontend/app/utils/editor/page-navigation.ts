import type { PageData } from '@/stores/editor/types'

type PageNavigationDirection = 'next' | 'prev'

type ResolveAdjacentPageIdOptions = {
  allPages: PageData[]
  availablePages?: PageData[] | null
  currentPageId: string | null | undefined
  direction: PageNavigationDirection
}

function findCurrentPageIndex(pages: PageData[], currentPageId: string | null | undefined): number {
  if (!currentPageId) return -1
  return pages.findIndex(page => page.id === currentPageId)
}

export function resolveAdjacentPageId(options: ResolveAdjacentPageIdOptions): string | null {
  const allPages = options.allPages
  const availablePages = options.availablePages ?? allPages
  const currentPageId = options.currentPageId ?? null

  if (availablePages.length === 0 || !currentPageId) return null

  const availableIndex = findCurrentPageIndex(availablePages, currentPageId)
  if (availableIndex >= 0) {
    const adjacent = options.direction === 'next'
      ? availablePages[availableIndex + 1]
      : availablePages[availableIndex - 1]
    return adjacent?.id ?? null
  }

  const currentIndex = findCurrentPageIndex(allPages, currentPageId)
  if (currentIndex < 0) return null

  const allPageIndexById = new Map(allPages.map((page, index) => [page.id, index]))
  const availableIndices = availablePages
    .map(page => ({ pageId: page.id, index: allPageIndexById.get(page.id) ?? -1 }))
    .filter(({ index }) => index >= 0)

  if (options.direction === 'next') {
    return availableIndices.find(({ index }) => index > currentIndex)?.pageId ?? null
  }

  for (let index = availableIndices.length - 1; index >= 0; index--) {
    const candidate = availableIndices[index]
    if (candidate && candidate.index < currentIndex) {
      return candidate.pageId
    }
  }

  return null
}
