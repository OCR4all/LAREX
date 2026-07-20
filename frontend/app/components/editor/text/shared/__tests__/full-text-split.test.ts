import { describe, expect, it } from 'vitest'
import { clampFullTextSplitRatio } from '../full-text-split'

describe('clampFullTextSplitRatio', () => {
  it('keeps both panes at least 280px wide when space permits', () => {
    expect(clampFullTextSplitRatio(0.1, 1000)).toBe(0.28)
    expect(clampFullTextSplitRatio(0.9, 1000)).toBe(0.72)
    expect(clampFullTextSplitRatio(0.5, 1000)).toBe(0.5)
  })

  it('keeps a usable narrow split and a safe fallback without measurements', () => {
    expect(clampFullTextSplitRatio(0.1, 500)).toBe(0.45)
    expect(clampFullTextSplitRatio(0.9, 500)).toBe(0.55)
    expect(clampFullTextSplitRatio(0, 0)).toBe(0.2)
    expect(clampFullTextSplitRatio(1, 0)).toBe(0.8)
  })
})
