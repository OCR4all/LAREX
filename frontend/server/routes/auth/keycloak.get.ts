export default defineOAuthKeycloakEventHandler({
  async onSuccess(event, { user, tokens }) {
    const roles = user.realm_access?.roles || user.roles || []

    await setUserSession(event, {
      user: {
        id: user.sub || user.id,
        login: user.preferred_username || user.id,
        name: user.name || user.preferred_username || user.given_name || user.family_name,
        email: user.email,
        avatar: user.picture,
        roles
      },
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
