export function buildPublicActionOutputProxyRequest(
  apiBaseInternal: string,
  sharePublicId: string,
  authorizationHeader?: string | null,
  method: 'GET' | 'HEAD' = 'GET'
): { url: string, init: RequestInit } {
  const headers = new Headers()
  if (authorizationHeader) headers.set('Authorization', authorizationHeader)
  return {
    url: `${apiBaseInternal.replace(/\/+$/, '')}/public/action-outputs/${sharePublicId}/download`,
    init: { method, headers }
  }
}
