export function buildPublicProjectReleaseProxyRequest(
  apiBaseInternal: string,
  sharePublicId: string,
  authorizationHeader?: string | null,
  method: 'GET' | 'HEAD' = 'GET'
): { url: string, init: RequestInit } {
  const baseUrl = apiBaseInternal.replace(/\/+$/, '')
  const headers = new Headers()

  if (authorizationHeader) {
    headers.set('Authorization', authorizationHeader)
  }

  return {
    url: `${baseUrl}/public/project-releases/${sharePublicId}/download`,
    init: {
      method,
      headers
    }
  }
}
