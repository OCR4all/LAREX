import { describe, expect, it } from 'vitest'
import { matchesTextlineFilters, type TextlineFilters } from '../textline-filtering'

const all: TextlineFilters = { gt: 'all', comments: 'all', diff: 'all' }
const line = {
  hasGtVariant: true,
  comments: 'Review this line',
  textContentVariants: [{ index: 3, text: 'GT text' }, { index: 1, text: 'Other text' }]
}

describe('textline filters', () => {
  it('filters both sides of GT presence independently of visible variants', () => {
    const hiddenGt = { ...line, textContentVariants: [{ index: 1, text: 'OCR only' }] }
    expect(matchesTextlineFilters(hiddenGt, { ...all, gt: 'with' }, 3)).toBe(true)
    expect(matchesTextlineFilters(hiddenGt, { ...all, gt: 'without' }, 3)).toBe(false)
    expect(matchesTextlineFilters({ ...line, hasGtVariant: false }, { ...all, gt: 'without' }, 3)).toBe(true)
  })

  it('treats absent and whitespace-only comments as without comments', () => {
    for (const comments of [undefined, '', ' \n ']) {
      expect(matchesTextlineFilters({ ...line, comments }, { ...all, comments: 'without' }, 3)).toBe(true)
      expect(matchesTextlineFilters({ ...line, comments }, { ...all, comments: 'with' }, 3)).toBe(false)
    }
    expect(matchesTextlineFilters(line, { ...all, comments: 'with' }, 3)).toBe(true)
    expect(matchesTextlineFilters(line, { ...all, comments: 'without' }, 3)).toBe(false)
  })

  it('filters mismatches and matches using the configured GT index and visible variants', () => {
    expect(matchesTextlineFilters(line, { ...all, diff: 'with' }, 3)).toBe(true)
    expect(matchesTextlineFilters(line, { ...all, diff: 'without' }, 3)).toBe(false)
    const matching = { ...line, textContentVariants: [{ index: 3, text: 'Same' }, { index: 1, text: 'Same' }] }
    expect(matchesTextlineFilters(matching, { ...all, diff: 'without' }, 3)).toBe(true)
    expect(matchesTextlineFilters(matching, { ...all, diff: 'with' }, 3)).toBe(false)
    expect(matchesTextlineFilters({ ...line, textContentVariants: [] }, { ...all, diff: 'without' }, 3)).toBe(true)
  })

  it('combines GT, comments and diff filters with AND logic', () => {
    const filters: TextlineFilters = { gt: 'with', comments: 'with', diff: 'with' }
    expect(matchesTextlineFilters(line, filters, 3)).toBe(true)
    expect(matchesTextlineFilters({ ...line, hasGtVariant: false }, filters, 3)).toBe(false)
    expect(matchesTextlineFilters({ ...line, comments: '' }, filters, 3)).toBe(false)
    expect(matchesTextlineFilters({ ...line, textContentVariants: [] }, filters, 3)).toBe(false)
  })
})
