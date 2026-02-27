export default defineEventHandler(async (event) => {
  const session = await getUserSession(event)

  return {
    loggedIn: !!session.user,
    user: session.user,
    hasSecureData: !!session.secure,
    tokenPresent: !!session.secure?.accessToken,
    secure: session.secure
      ? {
          accessTokenExpires: session.secure.accessTokenExpires
        }
      : null
  }
})
