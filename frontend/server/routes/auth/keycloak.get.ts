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
    console.error(error)
    consumeAuthRedirect(event)
    return sendRedirect(event, '/')
  }
})

export default defineEventHandler(async (event) => {
  const query = getQuery(event)

  if (!query.code && !query.error) {
    storeAuthRedirect(event, query.redirectTo)
  }

  return keycloakHandler(event)
})
