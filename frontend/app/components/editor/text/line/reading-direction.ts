import type { CSSProperties } from 'vue'

export type ReadingDirection = 'left-to-right' | 'right-to-left' | 'top-to-bottom' | 'bottom-to-top'

type ReadingDirectionCarrier = {
  readingDirection?: unknown
}

type MinimalTextLine = ReadingDirectionCarrier & {
  id: string
}

type MinimalRegion = ReadingDirectionCarrier & {
  regions?: MinimalRegion[]
  textLines?: MinimalTextLine[]
}

type MinimalPage = ReadingDirectionCarrier & {
  regions?: MinimalRegion[]
}

export function normalizeReadingDirection(value: unknown): ReadingDirection | undefined {
  if (typeof value !== 'string') return undefined
  switch (value) {
    case 'left-to-right':
    case 'right-to-left':
    case 'top-to-bottom':
    case 'bottom-to-top':
      return value
    default:
      return undefined
  }
}

export function computeTextLineReadingDirectionMap(page: MinimalPage | null | undefined): Record<string, ReadingDirection | undefined> {
  const byTextLineId: Record<string, ReadingDirection | undefined> = {}
  const pageReadingDirection = normalizeReadingDirection(page?.readingDirection)

  const collectFromRegions = (
    regions: MinimalRegion[] | undefined,
    inheritedReadingDirection: ReadingDirection | undefined
  ) => {
    for (const region of regions ?? []) {
      const regionReadingDirection = normalizeReadingDirection(region.readingDirection)
      const nextInheritedReadingDirection = regionReadingDirection ?? inheritedReadingDirection

      for (const textLine of region.textLines ?? []) {
        const textLineReadingDirection = normalizeReadingDirection(textLine.readingDirection)
        byTextLineId[textLine.id] = textLineReadingDirection ?? nextInheritedReadingDirection
      }

      collectFromRegions(region.regions, nextInheritedReadingDirection)
    }
  }

  collectFromRegions(page?.regions, pageReadingDirection)
  return byTextLineId
}

export function getReadingDirectionTextAttributes(readingDirection: ReadingDirection | undefined): {
  dir?: 'rtl'
  style?: CSSProperties
} {
  if (!readingDirection || readingDirection === 'left-to-right') {
    return {}
  }

  if (readingDirection === 'right-to-left') {
    return { dir: 'rtl' }
  }

  if (readingDirection === 'top-to-bottom') {
    return {
      style: {
        writingMode: 'vertical-rl'
      }
    }
  }

  return {
    style: {
      writingMode: 'vertical-rl',
      direction: 'rtl'
    }
  }
}
