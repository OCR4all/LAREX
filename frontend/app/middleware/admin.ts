export default defineNuxtRouteMiddleware(async () => {
  const { user, loggedIn } = useUserSession()

  const isAdmin = loggedIn.value && user.value?.roles?.includes('GLOBAL_ADMIN')

  if (!isAdmin) {
    if (import.meta.client) {
      const toast = useToast()
      toast.add({
        title: 'Access Denied',
        description: 'You do not have permission to access the admin panel.',
        color: 'error'
      })
    }
    return navigateTo('/')
  }
})
