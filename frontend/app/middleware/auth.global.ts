/**
 * Global Auth Middleware
 *
 * Protects routes on both server and client side.
 * Uses useUserSession() which works on both SSR and client.
 */
export default defineNuxtRouteMiddleware((to) => {
  const publicRoutes = [
    '/auth/keycloak',
    '/share'
  ]

  const isPublicRoute = publicRoutes.some(route => to.path.startsWith(route))

  const { loggedIn } = useUserSession()

  if (!loggedIn.value && !isPublicRoute) {
    return navigateToAuth({ replace: true })
  }

  if (loggedIn.value && to.path.startsWith('/auth/keycloak')) {
    return navigateTo('/')
  }
})
