import { describe, expect, it } from 'vitest'
import { convertRegionToDto } from '@/services/editor/page-conversion/region-converter'
import { collectRenderablePolygonsFromPcGts, findRegionRecursive } from '@/utils/editor/pcgts-editor-primitives'
import { UpdateRegionCommentCommand } from '../update-region-comment-command'
import { createMockSession, createTestContext, createTestDocument, createTestTextRegion } from './test-utils'

describe('UpdateRegionCommentCommand', () => {
  it('adds, edits, clears, undoes, and redoes a nested region comment', () => {
    const child = createTestTextRegion({ id: 'child' })
    const parent = createTestTextRegion({ id: 'parent' })
    parent.regions = [child]
    const { session, getDocument } = createMockSession(createTestDocument({ regions: [parent] }))
    const ctx = createTestContext(session)
    const comment = () => findRegionRecursive(getDocument()!.page.regions, 'child')?.region.comments

    const add = new UpdateRegionCommentCommand('child', 'First')
    add.execute(ctx)
    expect(comment()).toBe('First')
    expect(collectRenderablePolygonsFromPcGts(getDocument()!).find(polygon => polygon.id === 'child')?.comments).toBe('First')
    expect(convertRegionToDto(child).comments).toBe('First')

    const edit = new UpdateRegionCommentCommand('child', 'Second')
    edit.execute(ctx)
    expect(comment()).toBe('Second')
    edit.undo(ctx)
    expect(comment()).toBe('First')
    edit.execute(ctx)
    expect(comment()).toBe('Second')

    const clear = new UpdateRegionCommentCommand('child', undefined)
    clear.execute(ctx)
    expect(comment()).toBeUndefined()
    expect(convertRegionToDto(child).comments).toBeUndefined()
    clear.undo(ctx)
    expect(comment()).toBe('Second')

    add.undo(ctx)
    expect(comment()).toBeUndefined()
  })
})
