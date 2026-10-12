import { computed, ref } from 'vue'

export function useRefreshFeedback(refresh: () => unknown | Promise<unknown>, minimumDurationMs = 500) {
  const isRefreshing = ref(false)

  async function onClick() {
    if (isRefreshing.value) return

    isRefreshing.value = true
    // Run alongside the request so slow refreshes incur no additional delay.
    const minimumDuration = new Promise<void>(resolve => setTimeout(resolve, minimumDurationMs))
    try {
      await refresh()
    } finally {
      await minimumDuration
      isRefreshing.value = false
    }
  }

  return computed(() => ({
    'loadingIcon': 'i-lucide-refresh-cw',
    'loading': isRefreshing.value,
    'aria-busy': isRefreshing.value,
    onClick
  }))
}
