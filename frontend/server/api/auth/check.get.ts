import { jwtDecode } from 'jwt-decode'
import { isError } from 'h3'
import { refreshTokenIfExpired } from '#server/utils/auth'

export default defineEventHandler(async (event) => {
  const session = await getUserSession(event)
  if (!session.user) return { valid: false }

  try {
    // An expired access token can still have a valid refresh token after sleep.
    await refreshTokenIfExpired(event, session)
  } catch (error) {
    if (isError(error) && error.statusCode === 401) return { valid: false }
    throw error
  }

  const updatedSession = await getUserSession(event)
  const { exp } = jwtDecode<{ exp: number }>(updatedSession.secure!.accessToken)
  return {
    valid: true,
    expiresIn: exp - Math.floor(Date.now() / 1000),
    expiresAt: exp
  }
})
