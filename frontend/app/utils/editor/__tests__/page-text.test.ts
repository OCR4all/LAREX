import { describe, expect, it } from 'vitest'
import type { ReadingOrder } from '@/models/editor'
import type { Region } from '@/models/editor/region'
import { collectPageText } from '../page-text'

function region(id: string, lines: Array<{ id: string, variants: Array<{ unicode: string, index?: number }> }>, regions: Region[] = []): Region {
  return {
    id,
    kind: 'TextRegion',
    regions,
    textLines: lines.map(line => ({ id: line.id, textContentVariants: line.variants }))
  } as unknown as Region
}

describe('collectPageText', () => {
  it('follows the reading order and appends regions outside of it', () => {
    const regions = [
      region('r1', [{ id: 'l1', variants: [{ unicode: 'second', index: 0 }] }]),
      region('r2', [{ id: 'l2', variants: [{ unicode: 'first', index: 0 }] }]),
      region('r3', [{ id: 'l3', variants: [{ unicode: 'unordered', index: 0 }] }])
    ]
    const readingOrder = {
      root: { kind: 'OrderedGroup', id: 'ro', elements: [{ regionRef: 'r2' }, { regionRef: 'r1' }] }
    } as unknown as ReadingOrder

    expect(collectPageText({ regions, readingOrder }, 0)).toBe('first\nsecond\nunordered')
  })

  it('prefers GT, then the lowest index, then unindexed text', () => {
    const regions = [region('r1', [
      { id: 'l1', variants: [{ unicode: 'zero', index: 0 }, { unicode: 'ground truth', index: 3 }] },
      { id: 'l2', variants: [{ unicode: 'two', index: 2 }, { unicode: 'one', index: 1 }, { unicode: 'unindexed' }] },
      { id: 'l3', variants: [{ unicode: ' ', index: 3 }, { unicode: 'only unindexed' }] }
    ])]

    expect(collectPageText({ regions }, 3)).toBe('ground truth\none\nonly unindexed')
  })

  it('reads nested regions, falls back to words and skips lines without text', () => {
    const nested = region('nested', [{ id: 'l3', variants: [{ unicode: 'nested', index: 0 }] }])
    const withWords = region('r1', [{ id: 'l1', variants: [] }, { id: 'l2', variants: [] }], [nested])
    Object.assign((withWords as unknown as { textLines: object[] }).textLines[0]!, {
      words: [{ textContentVariants: [{ unicode: 'two' }] }, { textContentVariants: [{ unicode: 'words' }] }]
    })

    expect(collectPageText({ regions: [withWords] }, 0)).toBe('two words\nnested')
  })

  it('returns an empty string for a page without regions', () => {
    expect(collectPageText({}, 0)).toBe('')
  })
})
