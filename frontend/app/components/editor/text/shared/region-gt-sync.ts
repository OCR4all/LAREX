import type { TextContentVariantData } from '@/models/editor'
import { normalizeTextContentVariants, sortByIndex } from './text-view-runtime'

type TextLineLike = {
  textContentVariants?: TextContentVariantData[] | undefined
}

export function composeRegionGtFromTextLines(textLines: TextLineLike[] | undefined, gtIndex: number): string | null {
  const lines = (textLines ?? []).map((textLine) => {
    const gtVariant = normalizeTextContentVariants(textLine.textContentVariants)
      .find(variant => variant.index === gtIndex)
    return gtVariant?.unicode ?? ''
  })

  const combined = lines.join('\n')
  return combined.trim().length > 0 ? combined : null
}

export function buildRegionGtSyncedVariants(
  currentVariants: TextContentVariantData[] | undefined,
  nextGtText: string | null,
  gtIndex: number
): TextContentVariantData[] | undefined {
  const current = normalizeTextContentVariants(currentVariants)
  const existingGt = current.find(variant => variant.index === gtIndex)
  const withoutGt = current.filter(variant => variant.index !== gtIndex)

  // TODO: add region -> textline GT sync once we have a safe split/assignment strategy.
  if (!nextGtText) {
    return withoutGt.length > 0 ? withoutGt : undefined
  }

  withoutGt.push({
    ...existingGt,
    unicode: nextGtText,
    index: gtIndex
  })
  withoutGt.sort(sortByIndex)
  return withoutGt
}
