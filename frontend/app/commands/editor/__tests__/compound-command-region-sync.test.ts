import { beforeEach, describe, expect, it, vi } from 'vitest'
import { Commander } from '../commander'
import { CompoundCommand } from '../compound-command'
import { UpdateTextContentVariantsCommand } from '../update-text-content-variants-command'
import { TextContentVariant, isTextRegion } from '@/models/editor'
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
    const firstRegion = doc.page.regions[0]
    const secondRegion = doc.page.regions[1]
    if (!firstRegion || !secondRegion || !isTextRegion(firstRegion) || !isTextRegion(secondRegion)) {
      throw new Error('Expected text regions in test fixture')
    }

    firstRegion.textContentVariants = [new TextContentVariant('old 1', undefined, undefined, 0)]
    secondRegion.textContentVariants = [new TextContentVariant('old 2', undefined, undefined, 0)]

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
    const updatedFirstRegion = getDocument()?.page.regions[0]
    const updatedSecondRegion = getDocument()?.page.regions[1]
    expect(updatedFirstRegion && isTextRegion(updatedFirstRegion) ? updatedFirstRegion.textContentVariants?.[0]?.unicode : undefined).toBe('new 1')
    expect(updatedSecondRegion && isTextRegion(updatedSecondRegion) ? updatedSecondRegion.textContentVariants?.[0]?.unicode : undefined).toBe('new 2')

    commander.undo(ctx)

    const revertedFirstRegion = getDocument()?.page.regions[0]
    const revertedSecondRegion = getDocument()?.page.regions[1]
    expect(revertedFirstRegion && isTextRegion(revertedFirstRegion) ? revertedFirstRegion.textContentVariants?.[0]?.unicode : undefined).toBe('old 1')
    expect(revertedSecondRegion && isTextRegion(revertedSecondRegion) ? revertedSecondRegion.textContentVariants?.[0]?.unicode : undefined).toBe('old 2')
  })
})
