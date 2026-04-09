import { describe, expect, it } from 'vitest'
import { buildPublicProjectReleaseProxyRequest } from '../public-project-release-download'

describe('buildPublicProjectReleaseProxyRequest', () => {
  it('targets the backend public project release endpoint and forwards bearer auth', () => {
    const request = buildPublicProjectReleaseProxyRequest(
      'http://backend.internal/api/v1/',
      'share-123',
      'Bearer secret-value',
      'HEAD'
    )

    expect(request.url).toBe('http://backend.internal/api/v1/public/project-releases/share-123/download')
    expect(request.init.method).toBe('HEAD')
    expect(new Headers(request.init.headers).get('Authorization')).toBe('Bearer secret-value')
  })

  it('omits authorization when none is provided', () => {
    const request = buildPublicProjectReleaseProxyRequest(
      'http://backend.internal/api/v1',
      'share-456'
    )

    expect(new Headers(request.init.headers).has('Authorization')).toBe(false)
  })
})
