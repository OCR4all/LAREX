import { computed, reactive, ref, shallowRef, toValue, watch, type MaybeRefOrGetter, type Ref } from 'vue'
import { useMediaQuery } from '@vueuse/core'

export interface TableInfiniteScroll {
  enabled: boolean
  hasMore: boolean
  loading: boolean
  error: string | null
  shown: number
  total: number
  loadMore: () => void | Promise<void>
}

// Match Tailwind's sm breakpoint, including during server rendering.
export function useSmallTableScreen() {
  return useMediaQuery('(width < 640px)', { ssrWidth: 640 })
}

export function useTablePagination<T>(
  data: MaybeRefOrGetter<T[]>,
  page: Ref<number>,
  pageSize: MaybeRefOrGetter<number>
) {
  const enabled = useSmallTableScreen()
  watch(enabled, () => {
    page.value = 1
  }, { flush: 'sync' })
  watch(() => toValue(data), () => {
    if (enabled.value) page.value = 1
  })

  const rows = computed(() => {
    const size = toValue(pageSize)
    const start = enabled.value ? 0 : (page.value - 1) * size
    return toValue(data).slice(start, page.value * size)
  })
  const infiniteScroll: TableInfiniteScroll = reactive({
    enabled,
    hasMore: computed(() => page.value * toValue(pageSize) < toValue(data).length),
    loading: false,
    error: null,
    shown: computed(() => rows.value.length),
    total: computed(() => toValue(data).length),
    loadMore() {
      if (enabled.value && infiniteScroll.hasMore) page.value += 1
    }
  })
  return { rows, infiniteScroll }
}

export function useServerTablePagination<T>(
  data: MaybeRefOrGetter<T[]>,
  total: MaybeRefOrGetter<number>,
  page: Ref<number>,
  pending: MaybeRefOrGetter<boolean>,
  resetKey: MaybeRefOrGetter<unknown>,
  fetchPage: (page: number) => Promise<T[]>
) {
  const enabled = useSmallTableScreen()
  const extraRows = shallowRef<T[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  let nextPage = 2
  let generation = 0

  function reset() {
    generation += 1
    extraRows.value = []
    nextPage = 2
    loading.value = false
    error.value = null
  }
  watch(enabled, () => {
    page.value = 1
    reset()
  }, { flush: 'sync' })
  // A refreshed first page invalidates subsequent pages, including in-flight requests.
  watch([() => toValue(data), () => toValue(resetKey)], reset, { flush: 'sync' })

  const rows = computed(() => enabled.value ? [...toValue(data), ...extraRows.value] : toValue(data))
  const infiniteScroll: TableInfiniteScroll = reactive({
    enabled,
    hasMore: computed(() => rows.value.length < toValue(total)),
    loading: computed(() => loading.value || toValue(pending)),
    error,
    shown: computed(() => rows.value.length),
    total: computed(() => toValue(total)),
    async loadMore() {
      if (!enabled.value || infiniteScroll.loading || !infiniteScroll.hasMore) return
      const requestGeneration = generation
      loading.value = true
      error.value = null
      try {
        const more = await fetchPage(nextPage)
        if (requestGeneration !== generation) return
        extraRows.value = [...extraRows.value, ...more]
        if (more.length) nextPage += 1
        else error.value = 'No more rows were returned. Refresh the table to try again.'
      } catch {
        if (requestGeneration === generation) error.value = 'Could not load more rows. Try again.'
      } finally {
        if (requestGeneration === generation) loading.value = false
      }
    }
  })
  return { rows, infiniteScroll }
}
