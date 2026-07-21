export default defineEventHandler(async (event) => {
  const query = getQuery(event)

  if (query.kc_action_status === 'success') {
    await clearUserSession(event)
    return sendRedirect(event, '/')
  }

  return sendRedirect(event, '/settings/security')
})
