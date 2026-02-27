import { buildSessionUser } from '../../utils/session-profile'
import { refreshTokenIfExpired } from '../../utils/auth'

export default defineEventHandler(async (event) => {
  try {
    const { user, secure } = await getUserSession(event)

    if (!user || !secure?.accessToken) {
      throw createError({
        statusCode: 401,
        statusMessage: 'No valid session'
      })
    }

    await refreshTokenIfExpired(event, { user, secure })

    const updatedSession = await getUserSession(event)
    if (!updatedSession.user || !updatedSession.secure?.accessToken) {
      throw createError({
        statusCode: 401,
        statusMessage: 'No valid session after token refresh'
      })
    }

    const config = useRuntimeConfig(event)
    const keycloakConfig = config.oauth.keycloak

    const userInfoUrl = `${keycloakConfig.serverUrl}/realms/${keycloakConfig.realm}/protocol/openid-connect/userinfo`

    const response = await $fetch(userInfoUrl, {
      headers: {
        Authorization: `Bearer ${updatedSession.secure.accessToken}`
      }
    })

    const sessionUser = await buildSessionUser(
      event,
      response as {
        sub?: string
        id?: string
        preferred_username?: string
        name?: string
        given_name?: string
        family_name?: string
        email?: string
        realm_access?: { roles?: string[] }
        roles?: string[]
      },
      updatedSession.secure.accessToken
    )

    await setUserSession(event, {
      user: sessionUser,
      secure: updatedSession.secure
    })

    return {
      success: true,
      message: 'Profile refreshed successfully'
    }
  } catch (error) {
    console.error('Failed to refresh profile:', error)
    throw createError({
      statusCode: 500,
      statusMessage: 'Failed to refresh profile'
    })
  }
})
