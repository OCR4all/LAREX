export default defineEventHandler(async (event) => {
  try {
    const { logoutUser } = await import('../../utils/auth')
    await logoutUser(event)
  } catch (error) {
    console.error(error)
    await clearUserSession(event)
  }

  return { success: true }
})
