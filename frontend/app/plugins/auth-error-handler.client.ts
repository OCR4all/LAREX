export default defineNuxtPlugin({
  name: 'auth-error-handler',
  enforce: 'pre', // Run early to intercept all fetch calls
  setup() {
    const { loggedIn } = useUserSession()
    const nuxtApp = useNuxtApp()

    let isHandlingAuthError = false

    const handleAuthErrorOnce = async () => {
      if (isHandlingAuthError) return
      isHandlingAuthError = true

      try {
        const { handleAuthError } = useAuthRedirector()
        await handleAuthError()
      } finally {
        setTimeout(() => {
          isHandlingAuthError = false
        }, 3000)
      }
    }

    nuxtApp.hook('app:error', async (error: any) => {
      if (loggedIn.value && (error?.statusCode === 401 || error?.status === 401)) {
        console.warn('App error with 401, redirecting to login...')
        await handleAuthErrorOnce()
      }
    })

    const originalFetch = globalThis.$fetch
    globalThis.$fetch = originalFetch.create({
      onResponseError({ response }) {
        if (loggedIn.value && response.status === 401) {
          console.warn('401 error in API call, redirecting to login...')
          handleAuthErrorOnce()
        }
      }
    })
  }
})
