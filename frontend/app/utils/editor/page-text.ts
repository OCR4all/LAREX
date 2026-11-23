import type { ReadingOrder, TextContentVariantData } from '@/models/editor'
import { isTextRegion, type Region } from '@/models/editor/region'
import { matchesTextIndex } from '@/utils/text-index'
import { collectTextlineIdsInReadingOrder, type TextlineSortOrder } from '@/utils/editor/textline-navigation'

type TextCarrier = { textContentVariants?: unknown }

function hasText(variant: TextContentVariantData): boolean {
  return (variant.unicode ?? '').trim().length > 0
}

function resolveExportText(carrier: TextCarrier, gtIndex: number): string {
  const variants = ((carrier.textContentVariants ?? []) as TextContentVariantData[]).filter(hasText)
  const selected = variants.find(variant => matchesTextIndex(variant.index, gtIndex))
    ?? variants.toSorted((a, b) => (a.index ?? Infinity) - (b.index ?? Infinity))[0]
  return selected?.unicode ?? ''
}

export function collectPageText(
  page: { regions?: Region[], readingOrder?: ReadingOrder },
  gtIndex: number,
  sortOrder?: TextlineSortOrder
): string {
  const textByLineId = new Map<string, string>()
  const visit = (regions: Region[] | undefined) => {
    for (const region of regions ?? []) {
      if (isTextRegion(region)) {
        for (const line of region.textLines ?? []) {
          const wordText = () => (line.words ?? []).map(word => resolveExportText(word, gtIndex)).filter(Boolean).join(' ')
          textByLineId.set(line.id, resolveExportText(line, gtIndex) || wordText())
        }
      }
      visit(region.regions)
    }
  }
  visit(page.regions)

  return collectTextlineIdsInReadingOrder(page.regions, page.readingOrder, sortOrder)
    .flatMap(id => textByLineId.get(id) || [])
    .join('\n')
}
