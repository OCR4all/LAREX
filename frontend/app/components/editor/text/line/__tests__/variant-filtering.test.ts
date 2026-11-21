import { describe, expect, it } from 'vitest'
import {
  compareConfidenceLowFirst,
  createVariantFilterState,
  filterTextContentVariants,
  getHiddenTextVariantIndices,
  getMinVariantConfidence,
  revealTextVariants
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

  it('lists indices of non-empty variants that are not rendered', () => {
    const all = [
      { pos: 0, index: 0, text: 'gt' },
      { pos: 1, index: 3, text: 'ocr' },
      { pos: 2, text: 'unindexed' },
      { pos: 3, index: 2, text: '  ' },
      { pos: 4, index: 1, text: 'other' },
      { pos: 5, index: 1, text: 'duplicate' }
    ]

    expect(getHiddenTextVariantIndices(all, [{ pos: 0 }])).toEqual([1, 3, undefined])
    expect(getHiddenTextVariantIndices(all, all)).toEqual([])
  })

  it('widens the visibility settings just enough to show the given variants', () => {
    const settings = {
      selectedIndices: [0],
      filterUnindexed: false,
      confidenceRange: [0.5, 1] as [number, number],
      showNonAssignedIndices: false
    }
    const isNonAssigned = (index: number | undefined) => index === 4

    expect(revealTextVariants(settings, [{ index: 2, confidence: 0.9 }], isNonAssigned)).toEqual({
      ...settings,
      selectedIndices: [0, 2]
    })
    expect(revealTextVariants(settings, [{ confidence: 0.1 }, { index: 4 }], isNonAssigned)).toEqual({
      selectedIndices: [0, 4],
      filterUnindexed: true,
      confidenceRange: [0, 1],
      showNonAssignedIndices: true
    })
  })
})
