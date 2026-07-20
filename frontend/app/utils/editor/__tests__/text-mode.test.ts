import { describe, expect, it } from 'vitest'
import { resolveTextModeSubmodeFromQuery } from '../text-mode'

describe('resolveTextModeSubmodeFromQuery', () => {
  it('resolves Full text and legacy List aliases', () => {
    expect(resolveTextModeSubmodeFromQuery('full')).toBe('full')
    expect(resolveTextModeSubmodeFromQuery('expert')).toBe('expert')
    expect(resolveTextModeSubmodeFromQuery('textline')).toBe('expert')
    expect(resolveTextModeSubmodeFromQuery('region')).toBe('expert')
  })

  it('falls back to Canvas for missing and unknown values', () => {
    expect(resolveTextModeSubmodeFromQuery(null)).toBe('visual')
    expect(resolveTextModeSubmodeFromQuery('unknown')).toBe('visual')
  })
})
