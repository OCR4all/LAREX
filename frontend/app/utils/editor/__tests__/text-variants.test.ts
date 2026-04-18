import { describe, expect, it } from 'vitest'
import { ensureGtVariantAtIndex, setGtVariantUnicode } from '../text-variants'

describe('text-variants', () => {
  it('creates missing GT variant at requested index', () => {
    const result = ensureGtVariantAtIndex([
      { unicode: 'ocr-a', index: 1 },
      { unicode: 'ocr-b', index: 2 }
    ], 0)

    expect(result.created).toBe(true)
    expect(result.gtPos).toBe(0)
    expect(result.variants.map(v => [v.index, v.unicode])).toEqual([
      [0, ''],
      [1, 'ocr-a'],
      [2, 'ocr-b']
    ])
  })

  it('updates GT unicode in place and reports changed state', () => {
    const result = setGtVariantUnicode([
      { unicode: 'gt-old', index: 0 },
      { unicode: 'ocr-a', index: 1 }
    ], 0, 'gt-new')

    expect(result.created).toBe(false)
    expect(result.changed).toBe(true)
    expect(result.variants[0]?.unicode).toBe('gt-new')
    expect(result.variants[1]?.unicode).toBe('ocr-a')
  })

  it('does not report changed when GT text is identical', () => {
    const result = setGtVariantUnicode([
      { unicode: 'gt', index: 0 }
    ], 0, 'gt')

    expect(result.created).toBe(false)
    expect(result.changed).toBe(false)
    expect(result.variants[0]?.unicode).toBe('gt')
  })
})

