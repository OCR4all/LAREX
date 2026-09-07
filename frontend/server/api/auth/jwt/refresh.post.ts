export default defineEventHandler(async (event) => {
  const { refreshAccessToken } = await import('#server/utils/auth')
  const { user, secure } = await getUserSession(event)

  if (!user || !secure?.refreshToken) {
    throw createError({
      statusCode: 401,
      statusMessage: 'No valid session or refresh token'
    })
  }

  await refreshAccessToken(event, { user, secure })
  return { success: true }
})
