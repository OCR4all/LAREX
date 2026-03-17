import { describe, expect, it } from 'vitest'
import { Polygon } from '@/models/editor/geometry'
import { TextContentVariant, TextLine } from '@/models/editor'
import {
  buildRegionGtSyncedVariants,
  composeRegionGtFromTextLines
} from '../region-gt-sync'

function createTextLine(id: string, variants: TextContentVariant[]) {
  return new TextLine({
    id,
    coords: new Polygon([[0, 0], [1, 0], [1, 1], [0, 1]]),
    textContentVariants: variants
  })
}

describe('region-gt-sync', () => {
  it('composes region GT from textline GT values with newline separators', () => {
    const text = composeRegionGtFromTextLines([
      createTextLine('tl-1', [new TextContentVariant('First', undefined, undefined, 0)]),
      createTextLine('tl-2', [new TextContentVariant('Second', undefined, undefined, 0)]),
      createTextLine('tl-3', [new TextContentVariant('', undefined, undefined, 0)])
    ], 0)

    expect(text).toBe('First\nSecond\n')
  })

  it('returns null when composed GT is fully blank', () => {
    const text = composeRegionGtFromTextLines([
      createTextLine('tl-1', [new TextContentVariant('', undefined, undefined, 0)]),
      createTextLine('tl-2', [new TextContentVariant('   ', undefined, undefined, 0)])
    ], 0)

    expect(text).toBeNull()
  })

  it('preserves non-GT region variants while updating GT text', () => {
    const next = buildRegionGtSyncedVariants([
      { unicode: 'ocr', index: 1, confidence: 0.8 },
      { unicode: 'old gt', index: 0, confidence: 1 }
    ], 'line 1\nline 2', 0)

    expect(next).toEqual([
      { unicode: 'line 1\nline 2', index: 0, confidence: 1 },
      { unicode: 'ocr', index: 1, confidence: 0.8 }
    ])
  })

  it('removes the GT variant when synced text is blank', () => {
    const next = buildRegionGtSyncedVariants([
      { unicode: 'old gt', index: 0 },
      { unicode: 'ocr', index: 2 }
    ], null, 0)

    expect(next).toEqual([
      { unicode: 'ocr', index: 2 }
    ])
  })
})
