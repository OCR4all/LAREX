export default defineEventHandler(async (event) => {
  return sendRedirect(event, '/settings/security?password-changed=true')
})
