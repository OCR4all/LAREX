import { refreshTokenIfExpired } from '#server/utils/auth'

/**
 * Validate the Keycloak token before Nuxt renders a session-aware page.
 *
 * The session cookie can still contain a user after its access token has
 * expired. Without this hook, SSR renders the protected page first and the
 * client-side auth guard only redirects after hydration, causing a flash of
 * the application.
 */
export default defineNitroPlugin(() => {
  sessionHooks.hook('fetch', async (session, event) => {
    if (!session.user || !session.secure?.accessToken) return

    await refreshTokenIfExpired(event, {
      user: session.user,
      secure: session.secure
    })
  })
})
