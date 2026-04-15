import { describe, expect, it } from 'vitest'
import { buildPublicReleaseProxyRequest, parsePublicReleaseKind } from '../public-release-download'

describe('public release download utils', () => {
  it('parses supported release kinds', () => {
    expect(parsePublicReleaseKind('dataset-releases')).toBe('dataset-releases')
    expect(parsePublicReleaseKind('project-releases')).toBe('project-releases')
    expect(parsePublicReleaseKind('other')).toBeNull()
  })

  it('builds dataset proxy request', () => {
    const request = buildPublicReleaseProxyRequest(
      'http://backend.internal/api/v1',
      'dataset-releases',
      'share-123',
      'Bearer secret',
      'HEAD'
    )

    expect(request.url).toBe('http://backend.internal/api/v1/public/dataset-releases/share-123/download')
    expect(request.init.method).toBe('HEAD')
    expect(new Headers(request.init.headers).get('Authorization')).toBe('Bearer secret')
  })

  it('builds project proxy request', () => {
    const request = buildPublicReleaseProxyRequest(
      'http://backend.internal/api/v1',
      'project-releases',
      'share-456'
    )

    expect(request.url).toBe('http://backend.internal/api/v1/public/project-releases/share-456/download')
    expect(request.init.method).toBe('GET')
  })
})
