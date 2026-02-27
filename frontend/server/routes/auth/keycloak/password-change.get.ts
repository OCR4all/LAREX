export default defineEventHandler(async (event) => {
  const query = getQuery(event)
  const code = query.code as string

  if (code) {
  }

  return sendRedirect(event, '/settings/security?password-changed=true')
})
