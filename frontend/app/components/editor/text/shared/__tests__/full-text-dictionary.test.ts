import { describe, expect, it } from 'vitest'
import {
  getFullTextDictionarySegments,
  getMissingFullTextDictionaryTokens,
  getUnknownFullTextDictionarySegmentAtOffset
} from '../full-text-dictionary'

describe('getFullTextDictionarySegments', () => {
  it('preserves punctuation and marks lookup-backed unknown tokens', () => {
    const results = new Map([
      ['Known', { known: true }],
      ['unknown', { known: false }]
    ])

    expect(getFullTextDictionarySegments(
      'Known, unknown!',
      token => results.get(token) ?? null
    )).toEqual([
      { text: 'Known', start: 0, end: 5, token: true, unknown: false, pending: false },
      { text: ', ', start: 5, end: 7, token: false, unknown: false, pending: false },
      { text: 'unknown', start: 7, end: 14, token: true, unknown: true, pending: false },
      { text: '!', start: 14, end: 15, token: false, unknown: false, pending: false }
    ])
  })

  it('keeps repeated and joined tokens mapped to their exact ranges', () => {
    expect(getFullTextDictionarySegments(
      'lancelot lancelot—roi',
      () => ({ known: false })
    ).filter(segment => segment.token)).toEqual([
      { text: 'lancelot', start: 0, end: 8, token: true, unknown: true, pending: false },
      { text: 'lancelot—roi', start: 9, end: 21, token: true, unknown: true, pending: false }
    ])
  })

  it('marks tokens without cached lookup results as pending', () => {
    expect(getFullTextDictionarySegments('waiting', () => null)).toEqual([
      { text: 'waiting', start: 0, end: 7, token: true, unknown: false, pending: true }
    ])
  })

  it('identifies only tokens missing from the lookup cache', () => {
    const results = new Map([
      ['known', { known: true }],
      ['unknown', { known: false }]
    ])

    expect(getMissingFullTextDictionaryTokens(
      ['known', 'missing', 'unknown'],
      token => results.get(token) ?? null
    )).toEqual(['missing'])
  })

  it('resolves an unknown segment from a textarea caret offset', () => {
    const segments = getFullTextDictionarySegments(
      'Known, unknown!',
      token => ({ known: token === 'Known' })
    )

    expect(getUnknownFullTextDictionarySegmentAtOffset(segments, 9)?.text).toBe('unknown')
    expect(getUnknownFullTextDictionarySegmentAtOffset(segments, 14)?.text).toBe('unknown')
    expect(getUnknownFullTextDictionarySegmentAtOffset(segments, 2)).toBeNull()
    expect(getUnknownFullTextDictionarySegmentAtOffset(segments, 6)).toBeNull()
    expect(getUnknownFullTextDictionarySegmentAtOffset(segments, -1)).toBeNull()
  })
})
