import type { H3Event } from 'h3'
import { refreshTokenIfExpired } from './auth'

export async function backendFetch(
  event: H3Event,
  path: string,
  init?: RequestInit
): Promise<Response> {
  const session = await getUserSession(event)

  if (!session.user || !session.secure?.accessToken) {
    throw createError({ statusCode: 401, statusMessage: 'Unauthorized' })
  }

  try {
    await refreshTokenIfExpired(event, session)
  } catch {
    throw createError({ statusCode: 401, statusMessage: 'Token refresh failed' })
  }

  const updatedSession = await getUserSession(event)
  const config = useRuntimeConfig(event)

  const url = `${config.apiBaseInternal}/${path.replace(/^\//, '')}`

  const headers = new Headers(init?.headers)
  headers.set('Authorization', `Bearer ${updatedSession.secure?.accessToken}`)

  return fetch(url, {
    ...init,
    headers
  })
}
