export default defineEventHandler(async (event) => {
  try {
    const { logoutUser } = await import('#server/utils/auth')
    await logoutUser(event)
  } catch (error) {
    console.error(error)
    await clearUserSession(event)
  }

  return { success: true }
})
