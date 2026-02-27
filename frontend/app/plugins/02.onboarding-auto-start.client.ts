export default defineNuxtPlugin(() => {
  const route = useRoute()
  const { maybeAutoStartContextTour, isActive } = useOnboarding()

  watch(
    () => route.path,
    (path) => {
      setTimeout(() => {
        if (isActive.value) return
        void maybeAutoStartContextTour(path)
      }, 300)
    },
    { immediate: true }
  )
})
