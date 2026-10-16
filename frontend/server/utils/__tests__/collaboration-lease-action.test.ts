import { describe, expect, it } from 'vitest'
import { parseTakeoverRequestBody, parseTakeoverResponseBody } from '../collaboration-lease-action'

describe('collaboration takeover request validation', () => {
  it('preserves annotations and the decimal-string epoch for a forced save', () => {
    const body = { force: true, instanceId: 'curator-instance', annotations: { imageWidth: 100, imageHeight: 100 }, expectedLeaseEpoch: '9007199254740993' }
    expect(parseTakeoverRequestBody(body)).toEqual(body)
  })

  it('keeps ordinary takeover requests compatible', () => {
    expect(parseTakeoverRequestBody({ instanceId: ' viewer ' })).toEqual({ force: false, instanceId: 'viewer' })
    expect(parseTakeoverRequestBody({ force: true, instanceId: 'curator' })).toEqual({ force: true, instanceId: 'curator' })
  })

  it.each([
    null,
    {},
    { force: true, instanceId: 123 },
    { force: false, instanceId: 'viewer', annotations: {}, expectedLeaseEpoch: '2' },
    { force: true, instanceId: 'curator', annotations: {} },
    { force: true, instanceId: 'curator', annotations: {}, expectedLeaseEpoch: 'invalid' }
  ])('rejects malformed save and takeover payload %#', (body) => {
    expect(() => parseTakeoverRequestBody(body)).toThrow()
  })
})

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
