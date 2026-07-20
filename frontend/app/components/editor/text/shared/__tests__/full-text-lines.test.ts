import { describe, expect, it } from 'vitest'
import { resolveFullTextDraft, resolveFullTextLineValue } from '../full-text-lines'

describe('resolveFullTextLineValue', () => {
  it('prefers an existing GT even when it is empty', () => {
    expect(resolveFullTextLineValue([
      { unicode: 'prediction', index: 1 },
      { unicode: '', index: 0 }
    ], 0, [1])).toEqual({
      text: '',
      source: 'gt',
      sourceIndex: 0
    })
  })

  it('uses the first non-empty configured prediction', () => {
    expect(resolveFullTextLineValue([
      { unicode: 'second choice', index: 2 },
      { unicode: 'first choice', index: 1 }
    ], 0, [1, 2])).toEqual({
      text: 'first choice',
      source: 'prediction',
      sourceIndex: 1
    })
  })

  it('supports unindexed predictions and an empty fallback', () => {
    expect(resolveFullTextLineValue([
      { unicode: 'unindexed prediction' }
    ], 0, [-1])).toEqual({
      text: 'unindexed prediction',
      source: 'prediction',
      sourceIndex: undefined
    })
    expect(resolveFullTextLineValue([], 0, [1])).toEqual({
      text: '',
      source: 'empty'
    })
  })

  it('keeps an active local edit until the PAGE model catches up', () => {
    expect(resolveFullTextDraft('old PAGE text', 'local edit', true)).toBe('local edit')
    expect(resolveFullTextDraft('updated PAGE text', 'local edit', false)).toBe('updated PAGE text')
  })
})
