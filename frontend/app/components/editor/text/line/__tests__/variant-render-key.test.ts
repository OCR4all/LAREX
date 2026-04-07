import { describe, expect, it } from 'vitest'
import { getTextContentVariantRenderKey } from '../variant-render-key'

describe('variant-render-key', () => {
  it('keeps indexed variant keys stable when a GT variant is inserted before them', () => {
    const before = [
      { index: 1, pos: 0 },
      { index: 2, pos: 1 }
    ]
    const after = [
      { index: 0, pos: 0 },
      { index: 1, pos: 1 },
      { index: 2, pos: 2 }
    ]

    expect(before.map(getTextContentVariantRenderKey)).toEqual(['index:1', 'index:2'])
    expect(after.map(getTextContentVariantRenderKey)).toEqual(['index:0', 'index:1', 'index:2'])
  })

  it('uses the array position only for unindexed variants', () => {
    expect(getTextContentVariantRenderKey({ pos: 3 })).toBe('unindexed:3')
  })
})
