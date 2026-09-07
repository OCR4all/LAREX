import { buildSessionUser } from '#server/utils/session-profile'

const keycloakHandler = defineOAuthKeycloakEventHandler({
  async onSuccess(event, { user, tokens }) {
    const sessionUser = await buildSessionUser(event, user, tokens.access_token)

    await setUserSession(event, {
      user: sessionUser,
      secure: {
        accessToken: tokens.access_token,
        refreshToken: tokens.refresh_token,
        accessTokenExpires: Date.now() + tokens.expires_in * 1000
      }
    })
    return sendRedirect(event, consumeAuthRedirect(event))
  },
  onError(event, error) {
    consumeAuthRedirect(event)
    throw error
  }
})

export default defineEventHandler(async (event) => {
  const query = getQuery(event)

  if (!query.code && !query.error) {
    storeAuthRedirect(event, query.redirectTo)
  }

  try {
    return await keycloakHandler(event)
  } catch (error) {
    const tokenError = error as { statusCode?: number, data?: { error?: string } }
    if (query.code && tokenError?.statusCode === 400 && tokenError.data?.error === 'invalid_grant') {
      // Authorization codes are single-use and may expire while the tab is closed.
      await clearUserSession(event)
      return sendRedirect(event, `/auth/keycloak?redirectTo=${encodeURIComponent(consumeAuthRedirect(event))}`)
    }
    throw error
  }
})
