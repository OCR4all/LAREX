export default defineEventHandler(async (event) => {
  try {
    const { user, secure } = await getUserSession(event)

    if (!user || !secure?.accessToken) {
      throw createError({
        statusCode: 401,
        statusMessage: 'No valid session'
      })
    }

    const config = useRuntimeConfig(event)
    const keycloakConfig = config.oauth.keycloak

    const userInfoUrl = `${keycloakConfig.serverUrl}/realms/${keycloakConfig.realm}/protocol/openid-connect/userinfo`

    const response = await $fetch(userInfoUrl, {
      headers: {
        Authorization: `Bearer ${secure.accessToken}`
      }
    })

    const roles = response.realm_access?.roles || response.roles || []

    await setUserSession(event, {
      user: {
        id: response.sub,
        login: response.preferred_username || response.sub,
        name: response.name || response.preferred_username || response.given_name || response.family_name,
        email: response.email,
        avatar: response.picture,
        roles
      },
      secure
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
