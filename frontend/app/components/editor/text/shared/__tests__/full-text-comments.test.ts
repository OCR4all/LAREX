import { describe, expect, it } from 'vitest'
import {
  normalizeFullTextComment,
  serializeFullTextComment
} from '../full-text-comments'

describe('Full text comments', () => {
  it('normalizes comments for display while preserving internal formatting', () => {
    expect(normalizeFullTextComment('  first line\nsecond line  ')).toBe('first line\nsecond line')
  })

  it('removes empty PAGE XML comments', () => {
    expect(serializeFullTextComment(' \n\t ')).toBeUndefined()
    expect(serializeFullTextComment(null)).toBeUndefined()
  })

  it('serializes non-empty comments', () => {
    expect(serializeFullTextComment('  review this  ')).toBe('review this')
  })
})
