export default defineEventHandler(async (event) => {
  const { refreshAccessToken } = await import('#server/utils/auth')
  const { user, secure } = await getUserSession(event)

  if (!user || !secure?.refreshToken) {
    throw createError({
      statusCode: 401,
      statusMessage: 'No valid session or refresh token'
    })
  }

  try {
    await refreshAccessToken(event, { user, secure })
    return { success: true }
  } catch (error) {
    console.error('Token refresh failed:', error)
    await clearUserSession(event)
    throw createError({
      statusCode: 401,
      statusMessage: 'Token refresh failed'
    })
  }
})
