import { describe, expect, it, vi } from 'vitest'
import { Commander } from '@/commands/editor/commander'
import { TextContentVariant, TextLine, isTextRegion } from '@/models/editor'
import { createMockSession, createTestContext, createTestDocument, createTestTextRegion } from '@/commands/editor/__tests__/test-utils'
import { createTextlineVariantsUpdateCommand } from '../textline-variant-update'
import { Polygon } from '@/models/editor/geometry'

vi.mock('@/services/visibility-service', () => ({
  visibilityService: {
    clearCache: vi.fn()
  }
}))

function makeTextline() {
  return new TextLine({
    id: 'line-1',
    coords: new Polygon([[0, 0], [1, 0], [1, 1], [0, 1]]),
    textContentVariants: [
      new TextContentVariant('old GT', undefined, undefined, 0),
      new TextContentVariant('prediction', undefined, 0.9, 1)
    ]
  })
}

describe('createTextlineVariantsUpdateCommand', () => {
  it('updates line and parent GT in one undoable step while preserving predictions', () => {
    const region = createTestTextRegion({
      id: 'region-1',
      textLines: [makeTextline()]
    })
    region.textContentVariants = [new TextContentVariant('old GT', undefined, undefined, 0)]
    const doc = createTestDocument({ regions: [region] })
    const { session, getDocument } = createMockSession(doc)
    const commander = new Commander()
    const context = createTestContext(session)

    commander.execute(createTextlineVariantsUpdateCommand({
      pageRegions: doc.page.regions,
      textlineId: 'line-1',
      nextTextContentVariants: [
        { unicode: 'new GT', index: 0 },
        { unicode: 'prediction', confidence: 0.9, index: 1 }
      ],
      gtIndex: 0
    }), context)

    expect(commander.getState().totalCount).toBe(1)
    const updatedRegion = getDocument()?.page.regions[0]
    if (!updatedRegion || !isTextRegion(updatedRegion)) throw new Error('Expected TextRegion')
    expect(updatedRegion.textLines?.[0]?.textContentVariants?.map(variant => variant.unicode))
      .toEqual(['new GT', 'prediction'])
    expect(updatedRegion.textContentVariants?.[0]?.unicode).toBe('new GT')

    commander.undo(context)

    const restoredRegion = getDocument()?.page.regions[0]
    if (!restoredRegion || !isTextRegion(restoredRegion)) throw new Error('Expected TextRegion')
    expect(restoredRegion.textLines?.[0]?.textContentVariants?.map(variant => variant.unicode))
      .toEqual(['old GT', 'prediction'])
    expect(restoredRegion.textContentVariants?.[0]?.unicode).toBe('old GT')
  })
})
