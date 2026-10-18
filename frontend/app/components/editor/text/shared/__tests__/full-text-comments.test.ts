import { describe, expect, it } from 'vitest'
import { serializeFullTextComment } from '../full-text-comments'

describe('Full text comments', () => {
  it('removes empty PAGE XML comments', () => {
    expect(serializeFullTextComment(' \n\t ')).toBeUndefined()
    expect(serializeFullTextComment(null)).toBeUndefined()
  })

  it('serializes non-empty comments while preserving internal formatting', () => {
    expect(serializeFullTextComment('  first line\nsecond line  ')).toBe('first line\nsecond line')
  })
})
