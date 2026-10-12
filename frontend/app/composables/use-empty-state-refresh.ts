import { computed, ref } from 'vue'

export function useEmptyStateRefresh(refresh: () => unknown | Promise<unknown>) {
  const isRefreshing = ref(false)

  async function onClick() {
    if (isRefreshing.value) return

    isRefreshing.value = true
    // Start the minimum feedback duration alongside the request, not after it.
    const minimumDuration = new Promise<void>(resolve => setTimeout(resolve, 500))
    try {
      await refresh()
    } finally {
      await minimumDuration
      isRefreshing.value = false
    }
  }

  return computed(() => ({
    'icon': 'i-lucide-refresh-cw',
    'loadingIcon': 'i-lucide-refresh-cw',
    'label': 'Refresh',
    'color': 'neutral' as const,
    'variant': 'subtle' as const,
    'loading': isRefreshing.value,
    'aria-busy': isRefreshing.value,
    onClick
  }))
}
