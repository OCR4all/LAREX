import { jwtDecode } from 'jwt-decode'
import { createError, type H3Event } from 'h3'

type AuthSession = Pick<Awaited<ReturnType<typeof getUserSession>>, 'user' | 'secure'>

type RefreshTokenResponse = {
  access_token: string
  refresh_token?: string
  expires_in: number
}

type RefreshedSecureSession = {
  accessToken: string
  refreshToken: string
  accessTokenExpires: number
}

// ponytail: process-local coordination; shared sessions are needed for strict
// single-use refresh tokens across multiple frontend replicas.
const refreshInFlightByToken = new Map<string, Promise<RefreshedSecureSession>>()

async function invalidateSession(event: H3Event): Promise<never> {
  await clearUserSession(event)
  throw createError({ statusCode: 401, statusMessage: 'Session expired' })
}

export const refreshTokenIfExpired = async (event: H3Event, session: AuthSession) => {
  let expiresAt: number
  try {
    expiresAt = jwtDecode<{ exp: number }>(session.secure?.accessToken || '').exp
  } catch {
    return await invalidateSession(event)
  }

  if (!Number.isFinite(expiresAt)) return await invalidateSession(event)
  if (expiresAt > Math.floor(Date.now() / 1000) + 60) return
  await refreshAccessToken(event, session)
}

export const refreshAccessToken = async (event: H3Event, session: AuthSession) => {
  const refreshToken = session.secure?.refreshToken
  if (!refreshToken) return await invalidateSession(event)

  let pending = refreshInFlightByToken.get(refreshToken)
  if (!pending) {
    const keycloak = useRuntimeConfig(event).oauth.keycloak
    const tokenUrl = `${keycloak.serverUrlInternal || keycloak.serverUrl}/realms/${keycloak.realm}/protocol/openid-connect/token`
    pending = $fetch<RefreshTokenResponse>(tokenUrl, {
      method: 'POST',
      timeout: 10000,
      retry: false,
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'refresh_token',
        client_id: keycloak.clientId,
        client_secret: keycloak.clientSecret,
        refresh_token: refreshToken
      })
    }).then((response) => {
      const expiresAt = jwtDecode<{ exp: number }>(response.access_token).exp
      if (!Number.isFinite(expiresAt) || expiresAt <= Date.now() / 1000
        || !Number.isFinite(response.expires_in) || response.expires_in <= 0
        || (response.refresh_token !== undefined && (typeof response.refresh_token !== 'string' || !response.refresh_token))) {
        throw new Error('Invalid token response')
      }
      return {
        accessToken: response.access_token,
        refreshToken: response.refresh_token ?? refreshToken,
        accessTokenExpires: expiresAt * 1000
      }
    })
    refreshInFlightByToken.set(refreshToken, pending)
    // Let requests already carrying the old cookie reuse the rotated token.
    void pending.then(() => {
      setTimeout(() => refreshInFlightByToken.delete(refreshToken), 5000).unref()
    }, () => refreshInFlightByToken.delete(refreshToken))
  }

  let secure: RefreshedSecureSession
  try {
    secure = await pending
  } catch (error) {
    const tokenError = error as { statusCode?: number, data?: { error?: string } }
    if (tokenError?.statusCode === 400 && tokenError.data?.error === 'invalid_grant') {
      return await invalidateSession(event)
    }
    throw createError({ statusCode: 503, statusMessage: 'Authentication service unavailable. Please try again.' })
  }

  // Every waiting request must persist the result to its own response cookie.
  await replaceUserSession(event, { user: session.user, secure })
  return secure
}

export const logoutUser = async (event: H3Event) => {
  const { secure } = await getUserSession(event)
  const config = useRuntimeConfig(event)

  if (secure?.refreshToken) {
    try {
      const keycloakConfig = config.oauth.keycloak
      const logoutUrl = `${keycloakConfig.serverUrlInternal || keycloakConfig.serverUrl}/realms/${keycloakConfig.realm}/protocol/openid-connect/logout`
      await $fetch(logoutUrl, {
        method: 'POST',
        body: new URLSearchParams({
          client_id: keycloakConfig.clientId,
          client_secret: keycloakConfig.clientSecret,
          refresh_token: secure.refreshToken
        })
      })
    } catch {
      // logout should still clear local session when remote logout fails
    }
  }

  await clearUserSession(event)

  return { success: true }
}
