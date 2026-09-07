import { refreshTokenIfExpired } from '#server/utils/auth'
import type { UserSession } from '#auth-utils'
import { isError, type H3Event } from 'h3'

async function validateSession(session: UserSession, event: H3Event) {
  if (!session.user || event.context.larexAuthValidated) return
  try {
    await refreshTokenIfExpired(event, session)
  } catch (error) {
    if (!isError(error) || error.statusCode !== 401) throw error
    // The session endpoint serializes this snapshot, not the cleared cookie.
    delete session.user
    delete session.secure
  }
  event.context.larexAuthValidated = true
}

export default defineNitroPlugin((nitroApp) => {
  // Refresh on the original page response, so Set-Cookie reaches the browser.
  // Its session context is also shared with Nuxt's subsequent internal fetches.
  nitroApp.hooks.hook('render:before', async ({ event }) => {
    if (/^\/(?:api|auth|share|__nuxt_error)(?:\/|\?|$)/.test(event.path)) return
    setHeader(event, 'Cache-Control', 'private, no-store')
    await validateSession(await getUserSession(event), event)
  })

  sessionHooks.hook('fetch', async (session, event) => {
    setHeader(event, 'Cache-Control', 'private, no-store')
    try {
      await validateSession(session, event)
    } catch (error) {
      if (!isError(error) || error.statusCode !== 503) throw error
      // Keep the cookie for retry, but do not expose a protected page on CSR.
      delete session.user
      delete session.secure
      session.authUnavailable = true
    }
  })
})
