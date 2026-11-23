import { describe, expect, it } from 'vitest'
import { formatTextIndex, matchesTextIndex, normalizeGtTextIndex, textIndexOf, variantIndexFor } from '~/utils/text-index'

describe('text index', () => {
  it('maps a missing variant index to the unindexed text index', () => {
    expect(textIndexOf(undefined)).toBe(-1)
    expect(textIndexOf(null)).toBe(-1)
    expect(textIndexOf(2)).toBe(2)
    expect(matchesTextIndex(undefined, -1)).toBe(true)
    expect(matchesTextIndex(0, -1)).toBe(false)
    expect(matchesTextIndex(undefined, 0)).toBe(false)
  })

  it('omits the variant index for unindexed text', () => {
    expect(variantIndexFor(-1)).toBeUndefined()
    expect(variantIndexFor(3)).toBe(3)
  })

  it('keeps an unindexed ground truth and falls back for invalid values', () => {
    expect(normalizeGtTextIndex(-1)).toBe(-1)
    expect(normalizeGtTextIndex(-4)).toBe(0)
    expect(normalizeGtTextIndex('x')).toBe(0)
    expect(normalizeGtTextIndex(2.7)).toBe(2)
    expect(formatTextIndex(-1)).toBe('Undefined')
    expect(formatTextIndex(1)).toBe('1')
  })
})
