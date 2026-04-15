import { describe, expect, it } from 'vitest'
import {
  assertPublicReleaseAttemptAllowed,
  buildPublicReleaseAttemptKey,
  consumePublicReleaseBrowserSession,
  createPublicReleaseBrowserSession,
  registerPublicReleaseAttemptResult
} from '../public-release-browser-session'

describe('public release browser sessions', () => {
  it('creates and consumes one-time sessions', () => {
    const now = 1_000
    const token = createPublicReleaseBrowserSession(
      'dataset-releases',
      'share-123',
      'Bearer secret',
      '127.0.0.1',
      'test-agent',
      now
    )

    const session = consumePublicReleaseBrowserSession(token, '127.0.0.1', 'test-agent', now + 100)
    expect(session).not.toBeNull()
    expect(session?.sharePublicId).toBe('share-123')

    const secondAttempt = consumePublicReleaseBrowserSession(token, '127.0.0.1', 'test-agent', now + 200)
    expect(secondAttempt).toBeNull()
  })

  it('blocks mismatched requester context', () => {
    const now = 2_000
    const token = createPublicReleaseBrowserSession(
      'project-releases',
      'share-abc',
      'Bearer secret',
      '10.0.0.1',
      'agent-a',
      now
    )

    const session = consumePublicReleaseBrowserSession(token, '10.0.0.2', 'agent-a', now + 100)
    expect(session).toBeNull()
  })
})

describe('public release attempt limiter', () => {
  it('allows requests until max failures and then blocks', () => {
    const key = buildPublicReleaseAttemptKey('share-123', '127.0.0.1', 'agent')
    const now = 10_000

    for (let i = 0; i < 7; i++) {
      registerPublicReleaseAttemptResult(key, false, now + i)
      expect(() => assertPublicReleaseAttemptAllowed(key, now + i + 1)).not.toThrow()
    }

    registerPublicReleaseAttemptResult(key, false, now + 8)
    expect(() => assertPublicReleaseAttemptAllowed(key, now + 9)).toThrowError(/Too many failed attempts/i)
  })
})
