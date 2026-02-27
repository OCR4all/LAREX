import { buildSessionUser } from '../../utils/session-profile'

export default defineOAuthKeycloakEventHandler({
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
    return sendRedirect(event, '/')
  },
  onError(event, error) {
    console.error(error)
    return sendRedirect(event, '/')
  }
})
