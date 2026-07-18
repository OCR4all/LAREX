import { describe, expect, it } from 'vitest'
import { parseTakeoverResponseBody } from '../collaboration-lease-action'

describe('collaboration takeover response validation', () => {
  it('accepts every supported decision and handoff mode', () => {
    expect(parseTakeoverResponseBody({
      decision: 'accept',
      handoffMode: 'save'
    })).toEqual({
      decision: 'accept',
      handoffMode: 'save'
    })

    expect(parseTakeoverResponseBody({
      decision: 'decline',
      handoffMode: 'discard'
    })).toEqual({
      decision: 'decline',
      handoffMode: 'discard'
    })
  })

  it.each([
    null,
    {},
    { decision: 'approve', handoffMode: 'save' },
    { decision: 'accept', handoffMode: 'keep' }
  ])('rejects malformed payload %#', (body) => {
    expect(() => parseTakeoverResponseBody(body)).toThrow()
  })
})
