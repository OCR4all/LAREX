import { describe, expect, it } from 'vitest'
import { Polygon } from '@/models/editor/geometry'
import { TextLine } from '@/models/editor/text'
import { findTextLineRecursive } from '@/utils/editor/pcgts-editor-primitives'
import { UpdateTextlineCommentCommand } from '../update-textline-comment-command'
import {
  createMockSession,
  createTestContext,
  createTestDocument,
  createTestTextRegion
} from './test-utils'

function createTextline(comments?: string): TextLine {
  return new TextLine({
    id: 'line-1',
    coords: new Polygon([
      [100, 100],
      [500, 100],
      [500, 150],
      [100, 150]
    ]),
    comments
  })
}

function getComment(regions: ReturnType<typeof createTestDocument>['page']['regions']): string | undefined {
  return findTextLineRecursive(regions, 'line-1')?.textLine.comments
}

describe('UpdateTextlineCommentCommand', () => {
  it('updates a text-line comment and restores it on undo', () => {
    const region = createTestTextRegion({
      id: 'region-1',
      textLines: [createTextline('Original comment')]
    })
    const { session, getDocument } = createMockSession(createTestDocument({ regions: [region] }))
    const command = new UpdateTextlineCommentCommand({
      textlineId: 'line-1',
      comment: 'Updated comment'
    })

    command.execute(createTestContext(session))
    expect(getComment(getDocument()!.page.regions)).toBe('Updated comment')

    command.undo(createTestContext(session))
    expect(getComment(getDocument()!.page.regions)).toBe('Original comment')
  })

  it('supports clearing and redoing a comment', () => {
    const region = createTestTextRegion({
      id: 'region-1',
      textLines: [createTextline('Remove me')]
    })
    const { session, getDocument } = createMockSession(createTestDocument({ regions: [region] }))
    const ctx = createTestContext(session)
    const command = new UpdateTextlineCommentCommand({
      textlineId: 'line-1',
      comment: undefined
    })

    command.execute(ctx)
    expect(getComment(getDocument()!.page.regions)).toBeUndefined()

    command.undo(ctx)
    expect(getComment(getDocument()!.page.regions)).toBe('Remove me')

    command.execute(ctx)
    expect(getComment(getDocument()!.page.regions)).toBeUndefined()
  })
})
