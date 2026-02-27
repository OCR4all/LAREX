import { describe, expect, it } from 'vitest'
import { Polygon } from '@/models/editor/geometry'
import type { GraphicRegion, TextRegion } from '@/models/editor/region'
import { ChangeRegionKindCommand } from '../change-region-kind-command'
import {
  createMockSession,
  createTestContext,
  createTestDocument,
  createTestTextRegion,
  findRegionById
} from './test-utils'

describe('ChangeRegionKindCommand', () => {
  it('clears TextRegion subtype when switching to plain TextRegion label', () => {
    const region = createTestTextRegion({
      id: 'r1',
      type: 'paragraph'
    })
    const doc = createTestDocument({ regions: [region] })
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)

    const command = new ChangeRegionKindCommand({
      regionId: 'r1',
      newKind: 'TextRegion'
    })

    command.execute(ctx)

    const updated = findRegionById(getDocument()!.page.regions, 'r1') as TextRegion
    expect(updated.kind).toBe('TextRegion')
    expect(updated.type).toBeUndefined()

    command.undo(ctx)

    const restored = findRegionById(getDocument()!.page.regions, 'r1') as TextRegion
    expect(restored.kind).toBe('TextRegion')
    expect(restored.type).toBe('paragraph')
  })

  it('clears non-TextRegion subtype when subtype is omitted', () => {
    const graphicRegion: GraphicRegion = {
      id: 'g1',
      kind: 'GraphicRegion',
      type: 'logo',
      coords: new Polygon([
        [10, 10],
        [100, 10],
        [100, 100],
        [10, 100]
      ]),
      regions: []
    }
    const doc = createTestDocument({ regions: [graphicRegion] })
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)

    const command = new ChangeRegionKindCommand({
      regionId: 'g1',
      newKind: 'GraphicRegion'
    })

    command.execute(ctx)

    const updated = findRegionById(getDocument()!.page.regions, 'g1') as GraphicRegion
    expect(updated.kind).toBe('GraphicRegion')
    expect(updated.type).toBeUndefined()
  })

  it('updates and restores region custom when requested', () => {
    const region = createTestTextRegion({
      id: 'r-custom',
      type: 'paragraph'
    })
    region.custom = 'reading { dir:ltr; }'
    const doc = createTestDocument({ regions: [region] })
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)

    const command = new ChangeRegionKindCommand({
      regionId: 'r-custom',
      newKind: 'TextRegion',
      newSubtype: 'other',
      updateCustom: true,
      newCustom: 'reading { dir:ltr; } structure { type:article; subclass:lead; }'
    })

    command.execute(ctx)

    const updated = findRegionById(getDocument()!.page.regions, 'r-custom') as TextRegion
    expect(updated.type).toBe('other')
    expect(updated.custom).toBe('reading { dir:ltr; } structure { type:article; subclass:lead; }')

    command.undo(ctx)

    const restored = findRegionById(getDocument()!.page.regions, 'r-custom') as TextRegion
    expect(restored.type).toBe('paragraph')
    expect(restored.custom).toBe('reading { dir:ltr; }')
  })

  it('preserves unrelated custom data when subtype changes without custom update', () => {
    const region = createTestTextRegion({
      id: 'r-preserve',
      type: 'paragraph'
    })
    region.custom = 'reading { dir:ltr; }'
    const doc = createTestDocument({ regions: [region] })
    const { session, getDocument } = createMockSession(doc)
    const ctx = createTestContext(session)

    const command = new ChangeRegionKindCommand({
      regionId: 'r-preserve',
      newKind: 'TextRegion',
      newSubtype: 'heading'
    })

    command.execute(ctx)

    const updated = findRegionById(getDocument()!.page.regions, 'r-preserve') as TextRegion
    expect(updated.type).toBe('heading')
    expect(updated.custom).toBe('reading { dir:ltr; }')
  })
})
