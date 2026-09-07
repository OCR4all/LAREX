/**
 * Global Auth Middleware
 *
 * Protects routes on both server and client side.
 * Uses useUserSession() which works on both SSR and client.
 */
export default defineNuxtRouteMiddleware(async (to) => {
  const publicRoutes = [
    '/auth/keycloak',
    '/share'
  ]

  const isPublicRoute = publicRoutes.some(route => to.path.startsWith(route))

  const { loggedIn, session } = useUserSession()

  if (!isPublicRoute && session.value?.authUnavailable) {
    throw createError({ statusCode: 503, statusMessage: 'Authentication service unavailable. Please try again.' })
  }

  if (!loggedIn.value && !isPublicRoute) {
    return navigateToAuth({ redirectTo: to.fullPath, replace: true })
  }

  if (loggedIn.value && to.path.startsWith('/auth/keycloak')) {
    return navigateTo('/')
  }

  if (loggedIn.value && !isPublicRoute) {
    const { initialized, fetchPreferences } = useEditorPreferences()

    if (!initialized.value) {
      await fetchPreferences()
    }
  }
})
