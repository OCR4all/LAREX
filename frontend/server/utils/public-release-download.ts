import { buildPublicDatasetReleaseProxyRequest } from './public-dataset-release-download'
import { buildPublicProjectReleaseProxyRequest } from './public-project-release-download'
import { buildPublicActionOutputProxyRequest } from './public-action-output-download'

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
  if (kind === 'dataset-releases') {
    return buildPublicDatasetReleaseProxyRequest(apiBaseInternal, sharePublicId, authorizationHeader, method)
  }
  if (kind === 'action-outputs') {
    return buildPublicActionOutputProxyRequest(apiBaseInternal, sharePublicId, authorizationHeader, method)
  }
  return buildPublicProjectReleaseProxyRequest(apiBaseInternal, sharePublicId, authorizationHeader, method)
}
