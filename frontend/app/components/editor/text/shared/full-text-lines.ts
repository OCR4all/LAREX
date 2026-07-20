import type { TextContentVariantData } from '@/models/editor'

export type FullTextLineSource = 'gt' | 'prediction' | 'empty'

export type FullTextLineValue = {
  text: string
  source: FullTextLineSource
  sourceIndex?: number
}

export function resolveFullTextDraft(
  modelText: string,
  localDraft: string | undefined,
  keepLocalDraft: boolean
): string {
  return keepLocalDraft ? (localDraft ?? modelText) : modelText
}

/**
 * Resolve the single value shown by Full text mode without mutating variants.
 * An existing GT wins even when it is intentionally empty. Predictions are
 * considered in configured recognition-index order, with -1 representing
 * unindexed variants.
 */
export function resolveFullTextLineValue(
  variants: TextContentVariantData[] | undefined,
  gtIndex: number,
  recognitionIndices: number[]
): FullTextLineValue {
  const current = variants ?? []
  const gt = current.find(variant => variant.index === gtIndex)
  if (gt) {
    return {
      text: gt.unicode ?? '',
      source: 'gt',
      sourceIndex: gtIndex
    }
  }

  for (const recognitionIndex of [...new Set(recognitionIndices)]) {
    const prediction = current.find((variant) => {
      const indexMatches = recognitionIndex === -1
        ? variant.index === undefined
        : variant.index === recognitionIndex
      return indexMatches && (variant.unicode ?? '').trim().length > 0
    })
    if (!prediction) continue

    return {
      text: prediction.unicode ?? '',
      source: 'prediction',
      sourceIndex: prediction.index
    }
  }

  return {
    text: '',
    source: 'empty'
  }
}
