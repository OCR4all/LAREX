import { describe, expect, it } from 'vitest'
import { buildPublicReleaseProxyRequest, parsePublicReleaseKind } from '../public-release-download'

describe('public release download utils', () => {
  it('parses supported release kinds', () => {
    expect(parsePublicReleaseKind('dataset-releases')).toBe('dataset-releases')
    expect(parsePublicReleaseKind('project-releases')).toBe('project-releases')
    expect(parsePublicReleaseKind('action-outputs')).toBe('action-outputs')
    expect(parsePublicReleaseKind('other')).toBeNull()
    expect(parsePublicReleaseKind(null)).toBeNull()
  })

  it.each([
    {
      kind: 'dataset-releases',
      shareId: 'share-123',
      baseUrl: 'http://backend.internal/api/v1',
      authorization: 'Bearer secret',
      method: 'HEAD'
    },
    {
      kind: 'project-releases',
      shareId: 'share-456',
      baseUrl: 'http://backend.internal/api/v1/',
      authorization: null,
      method: 'GET'
    },
    {
      kind: 'action-outputs',
      shareId: 'output-share',
      baseUrl: 'http://backend.internal/api/v1///',
      authorization: 'Bearer output-secret',
      method: 'GET'
    }
  ] as const)('builds a $method request for $kind', ({ kind, shareId, baseUrl, authorization, method }) => {
    const request = buildPublicReleaseProxyRequest(
      baseUrl,
      kind,
      shareId,
      authorization,
      method
    )

    expect(request.url).toBe(`http://backend.internal/api/v1/public/${kind}/${shareId}/download`)
    expect(request.init.method).toBe(method)
    expect(new Headers(request.init.headers).get('Authorization')).toBe(authorization)
  })
})
