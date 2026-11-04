/** useToast for callers outside setup, such as stores and realtime handlers. */
export function useAppToast() {
  return useNuxtApp().vueApp.runWithContext(() => useToast())
}
