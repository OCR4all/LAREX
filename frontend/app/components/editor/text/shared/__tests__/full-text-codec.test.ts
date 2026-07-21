import { describe, expect, it } from 'vitest'
import {
  getFullTextCodecSegments,
  getUnknownCodecCharacters
} from '../full-text-codec'

describe('getFullTextCodecSegments', () => {
  it('groups adjacent characters by codec membership and preserves ranges', () => {
    const codec = new Set(['a', 'b', ' '])

    expect(getFullTextCodecSegments('ab x', codec, false)).toEqual([
      { text: 'ab ', start: 0, end: 3, unknown: false },
      { text: 'x', start: 3, end: 4, unknown: true }
    ])
  })

  it('supports treating whitespace as a codec character', () => {
    const codec = new Set(['a'])

    expect(getFullTextCodecSegments('a \t', codec, true)).toEqual([
      { text: 'a', start: 0, end: 1, unknown: false },
      { text: ' \t', start: 1, end: 3, unknown: true }
    ])
  })

  it('keeps Unicode code-point boundaries and reports unique unknown characters', () => {
    const segments = getFullTextCodecSegments('a😀😀𐍈', new Set(['a']), false)

    expect(segments).toEqual([
      { text: 'a', start: 0, end: 1, unknown: false },
      { text: '😀😀𐍈', start: 1, end: 7, unknown: true }
    ])
    expect(getUnknownCodecCharacters(segments)).toEqual(['😀', '𐍈'])
  })
})
