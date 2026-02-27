export default defineNuxtPlugin(() => {
  const { loggedIn } = useUserSession()
  const { startAuthGuard, stopAuthGuard } = useAuthGuard()

  const stopWatchingAuth = watch(loggedIn, (isLoggedIn) => {
    if (isLoggedIn) {
      startAuthGuard(60000)
    } else {
      stopAuthGuard()
    }
  }, { immediate: true })

  if (import.meta.hot) {
    import.meta.hot.dispose(() => {
      stopWatchingAuth()
      stopAuthGuard()
    })
  }
})
