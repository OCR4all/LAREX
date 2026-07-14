import { jwtDecode } from 'jwt-decode'
import type { H3Event } from 'h3'

interface TokenData {
  exp: number
  iat: number
  [key: string]: unknown
}

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

const refreshInFlightByToken = new Map<string, Promise<RefreshedSecureSession>>()

function getErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error)
}

async function withRefreshTokenLock(
  refreshToken: string,
  refreshAction: () => Promise<RefreshedSecureSession>
): Promise<RefreshedSecureSession> {
  const pendingRefresh = refreshInFlightByToken.get(refreshToken)
  if (pendingRefresh) {
    return await pendingRefresh
  }

  const refreshPromise = refreshAction()
    .finally(() => {
      if (refreshInFlightByToken.get(refreshToken) === refreshPromise) {
        refreshInFlightByToken.delete(refreshToken)
      }
    })

  refreshInFlightByToken.set(refreshToken, refreshPromise)
  return await refreshPromise
}

export const refreshTokenIfExpired = async (event: H3Event, session: AuthSession) => {
  if (!session.secure?.accessToken) {
    throw new Error('No access token available')
  }

  let decoded: TokenData
  try {
    decoded = jwtDecode<TokenData>(session.secure.accessToken)
  } catch (error: unknown) {
    throw new Error(`Invalid access token: ${getErrorMessage(error)}`, { cause: error })
  }

  const now = Math.floor(Date.now() / 1000)
  const bufferTime = 5 * 60

  if (decoded.exp > now + bufferTime) {
    return
  }

  if (!session.secure.refreshToken) {
    throw new Error('No refresh token available')
  }

  const refreshedSecure = await withRefreshTokenLock(session.secure.refreshToken, async () => {
    return await refreshAccessToken(event, session)
  })

  const currentSession = await getUserSession(event)
  const currentAccessToken = currentSession.secure?.accessToken
  const currentRefreshToken = currentSession.secure?.refreshToken

  if (currentAccessToken !== refreshedSecure.accessToken || currentRefreshToken !== refreshedSecure.refreshToken) {
    await replaceUserSession(event, {
      user: currentSession.user ?? session.user,
      secure: refreshedSecure
    })
  }
}

export const refreshAccessToken = async (event: H3Event, session: AuthSession) => {
  const config = useRuntimeConfig(event)

  if (!session.secure?.refreshToken) {
    throw new Error('No refresh token available')
  }

  try {
    const keycloakConfig = config.oauth.keycloak
    const tokenUrl = `${keycloakConfig.serverUrlInternal || keycloakConfig.serverUrl}/realms/${keycloakConfig.realm}/protocol/openid-connect/token`

    const response = await $fetch<RefreshTokenResponse>(tokenUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded'
      },
      body: new URLSearchParams({
        grant_type: 'refresh_token',
        client_id: keycloakConfig.clientId,
        client_secret: keycloakConfig.clientSecret,
        refresh_token: session.secure.refreshToken
      })
    })

    const refreshedSecure: RefreshedSecureSession = {
      accessToken: response.access_token,
      refreshToken: response.refresh_token || session.secure?.refreshToken,
      accessTokenExpires: Date.now() + response.expires_in * 1000
    }

    await replaceUserSession(event, {
      user: session.user,
      secure: refreshedSecure
    })

    return refreshedSecure
  } catch (error: unknown) {
    throw new Error(`Token refresh failed: ${getErrorMessage(error)}`, { cause: error })
  }
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
