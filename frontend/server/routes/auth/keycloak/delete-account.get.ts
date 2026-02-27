export default defineEventHandler(async (event) => {
  const query = getQuery(event)
  const code = query.code as string

  if (code) {
    await clearUserSession(event)
  }

  return sendRedirect(event, '/auth/keycloak')
})
