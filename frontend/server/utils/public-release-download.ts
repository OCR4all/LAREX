export const PUBLIC_RELEASE_KINDS = ['dataset-releases', 'project-releases', 'action-outputs'] as const

export type PublicReleaseKind = typeof PUBLIC_RELEASE_KINDS[number]

export function parsePublicReleaseKind(value: string | null | undefined): PublicReleaseKind | null {
  if (!value) {
    return null
  }
  if (value === 'dataset-releases' || value === 'project-releases' || value === 'action-outputs') {
    return value
  }
  return null
}

export function buildPublicReleaseProxyRequest(
  apiBaseInternal: string,
  kind: PublicReleaseKind,
  sharePublicId: string,
  authorizationHeader?: string | null,
  method: 'GET' | 'HEAD' = 'GET'
): { url: string, init: RequestInit } {
  const headers = new Headers()
  if (authorizationHeader) headers.set('Authorization', authorizationHeader)

  return {
    url: `${apiBaseInternal.replace(/\/+$/, '')}/public/${kind}/${sharePublicId}/download`,
    init: { method, headers }
  }
}
