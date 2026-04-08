import { jwtDecode } from 'jwt-decode'

interface TokenData {
  exp: number
  iat: number
}

interface AuthCheckResponse {
  valid: boolean
  expiresIn?: number // seconds until token expires
  expiresAt?: number // Unix timestamp when token expires
}

/**
 * Auth check endpoint - validates the current session and returns token expiry info.
 * Used by the client to check auth status without making unnecessary API calls.
 */
export default defineEventHandler(async (event): Promise<AuthCheckResponse> => {
  try {
    const session = await getUserSession(event)

    if (!session?.user || !session?.secure?.accessToken) {
      return { valid: false }
    }

    const decoded = jwtDecode<TokenData>(session.secure.accessToken)
    const now = Math.floor(Date.now() / 1000)
    const expiresIn = decoded.exp - now

    if (expiresIn <= 0) {
      return { valid: false }
    }

    const bufferTime = 5 * 60
    if (expiresIn <= bufferTime) {
      try {
        const { refreshTokenIfExpired } = await import('#server/utils/auth')
        await refreshTokenIfExpired(event, { user: session.user, secure: session.secure })

        const updatedSession = await getUserSession(event)
        if (updatedSession?.secure?.accessToken) {
          const newDecoded = jwtDecode<TokenData>(updatedSession.secure.accessToken)
          const newExpiresIn = newDecoded.exp - now
          return {
            valid: true,
            expiresIn: newExpiresIn,
            expiresAt: newDecoded.exp
          }
        }
      } catch {
        return { valid: false }
      }
    }

    return {
      valid: true,
      expiresIn,
      expiresAt: decoded.exp
    }
  } catch {
    return { valid: false }
  }
})
