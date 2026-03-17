import { beforeEach, describe, expect, it, vi } from 'vitest'
import { Commander } from '../commander'
import { CompoundCommand } from '../compound-command'
import { UpdateTextContentVariantsCommand } from '../update-text-content-variants-command'
import { TextContentVariant } from '@/models/editor'
import { createMockSession, createTestContext, createTestDocument, createTestTextRegion } from './test-utils'

vi.mock('@/services/visibility-service', () => ({
  visibilityService: {
    clearCache: vi.fn()
  }
}))

describe('Compound region sync command', () => {
  let commander: Commander

  beforeEach(() => {
    commander = new Commander()
    vi.clearAllMocks()
  })

  it('records bulk region GT sync as a single undo step', () => {
    const doc = createTestDocument({
      regions: [
        createTestTextRegion({
          id: 'region-1'
        }),
        createTestTextRegion({
          id: 'region-2'
        })
      ]
    })
    doc.page.regions[0]!.textContentVariants = [new TextContentVariant('old 1', undefined, undefined, 0)]
    doc.page.regions[1]!.textContentVariants = [new TextContentVariant('old 2', undefined, undefined, 0)]

    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)

    commander.execute(new CompoundCommand([
      new UpdateTextContentVariantsCommand({
        elementId: 'region-1',
        nextTextContentVariants: [{ unicode: 'new 1', index: 0 }]
      }),
      new UpdateTextContentVariantsCommand({
        elementId: 'region-2',
        nextTextContentVariants: [{ unicode: 'new 2', index: 0 }]
      })
    ], 'Sync region GT from textlines (2)'), ctx)

    expect(commander.getState().totalCount).toBe(1)
    expect(getDocument()?.page.regions[0]?.textContentVariants?.[0]?.unicode).toBe('new 1')
    expect(getDocument()?.page.regions[1]?.textContentVariants?.[0]?.unicode).toBe('new 2')

    commander.undo(ctx)

    expect(getDocument()?.page.regions[0]?.textContentVariants?.[0]?.unicode).toBe('old 1')
    expect(getDocument()?.page.regions[1]?.textContentVariants?.[0]?.unicode).toBe('old 2')
  })
})
