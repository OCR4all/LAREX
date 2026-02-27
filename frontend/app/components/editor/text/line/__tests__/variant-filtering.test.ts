import { describe, expect, it } from 'vitest'
import {
  compareConfidenceLowFirst,
  createVariantFilterState,
  filterTextContentVariants,
  getMinVariantConfidence
} from '../variant-filtering'

type Variant = {
  index?: number
  confidence?: number
  text?: string
}

describe('variant-filtering', () => {
  it('keeps all variants when no variant filter is active', () => {
    const variants: Variant[] = [
      { index: 0, confidence: 0.9 },
      { index: 1, confidence: 0.5 },
      { confidence: undefined }
    ]
    const state = createVariantFilterState({
      selectedIndices: [],
      filterUnindexed: false,
      confidenceRange: [0, 1]
    })

    expect(state.hasVariantFilter).toBe(false)
    expect(filterTextContentVariants(variants, state)).toEqual(variants)
  })

  it('filters by confidence and excludes missing confidence while confidence filter is active', () => {
    const variants: Variant[] = [
      { index: 0, confidence: 0.95 },
      { index: 1, confidence: 0.6 },
      { index: 2 },
      { index: 3, confidence: 0.2 }
    ]
    const state = createVariantFilterState({
      selectedIndices: [],
      filterUnindexed: false,
      confidenceRange: [0.4, 0.9]
    })

    const filtered = filterTextContentVariants(variants, state)

    expect(state.hasConfidenceFilter).toBe(true)
    expect(filtered).toEqual([{ index: 1, confidence: 0.6 }])
  })

  it('combines index and confidence filters with AND logic', () => {
    const variants: Variant[] = [
      { index: 0, confidence: 0.95 },
      { index: 1, confidence: 0.7 },
      { index: 1, confidence: 0.85 },
      { confidence: 0.9 }
    ]
    const state = createVariantFilterState({
      selectedIndices: [1],
      filterUnindexed: true,
      confidenceRange: [0.8, 1]
    })

    const filtered = filterTextContentVariants(variants, state)

    expect(filtered).toEqual([
      { index: 1, confidence: 0.85 },
      { confidence: 0.9 }
    ])
  })

  it('supports textline visibility checks based on active variant filter', () => {
    const variants: Variant[] = [{ confidence: 0.5 }]
    const state = createVariantFilterState({
      selectedIndices: [2],
      filterUnindexed: false,
      confidenceRange: [0, 1]
    })

    const filtered = filterTextContentVariants(variants, state)
    const isVisible = !state.hasVariantFilter || filtered.length > 0

    expect(state.hasVariantFilter).toBe(true)
    expect(filtered).toEqual([])
    expect(isVisible).toBe(false)
  })

  it('computes line confidence as minimum confidence of visible variants', () => {
    const variants: Variant[] = [
      { confidence: 0.92 },
      { confidence: 0.77 },
      { confidence: 0.81 },
      { index: 4 }
    ]

    expect(getMinVariantConfidence(variants)).toBe(0.77)
  })

  it('sorts undefined confidence values last', () => {
    const values: Array<number | undefined> = [undefined, 0.5, 0.2, undefined, 0.9]
    const sorted = [...values].sort(compareConfidenceLowFirst)

    expect(sorted).toEqual([0.2, 0.5, 0.9, undefined, undefined])
  })
})
