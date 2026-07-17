import { computed, onBeforeUnmount, onMounted, ref, toValue, watch, type ComputedRef, type Ref } from 'vue'

type MaybeRefOrGetter<T> = T | Ref<T> | ComputedRef<T> | (() => T)

type IndexStatusPollingOptions = {
  ids: MaybeRefOrGetter<string[]>
  hasPending: (id: string) => boolean
  poll: (id: string) => Promise<boolean | undefined>
  intervalMs?: number
  enabled?: MaybeRefOrGetter<boolean>
  signature?: MaybeRefOrGetter<string>
  immediate?: boolean
}

export function useIndexStatusPolling(options: IndexStatusPollingOptions) {
  const intervalMs = options.intervalMs ?? 3000
  const realtimeAuditMs = 60_000
  const timeouts = new Map<string, ReturnType<typeof setTimeout>>()
  const inFlight = new Set<string>()
  const canPoll = ref(false)
  const realtime = useRealtimeSocket()
  let realtimeUnsubscribe: (() => void) | null = null

  const idsSignature = computed(() => toValue(options.ids).join('|'))
  const stateSignature = computed(() => toValue(options.signature) ?? '')
  const isEnabled = computed(() => {
    const externalEnabled = options.enabled === undefined ? true : toValue(options.enabled)
    return import.meta.client && canPoll.value && realtime.isPageVisible.value && externalEnabled
  })

  function clear(id?: string) {
    if (id) {
      const timeoutId = timeouts.get(id)
      if (timeoutId) {
        clearTimeout(timeoutId)
        timeouts.delete(id)
      }
      return
    }

    for (const timeoutId of timeouts.values()) {
      clearTimeout(timeoutId)
    }
    timeouts.clear()
  }

  function schedule(id: string, delayMs?: number) {
    if (!isEnabled.value) return
    if (!id || timeouts.has(id) || inFlight.has(id) || !options.hasPending(id)) {
      return
    }

    const delay = delayMs ?? (realtime.connectionStatus.value === 'connected' ? realtimeAuditMs : intervalMs)
    timeouts.set(id, setTimeout(() => {
      timeouts.delete(id)
      void pollId(id)
    }, delay))
  }

  async function pollId(id: string, force = false) {
    if (!isEnabled.value || !id || inFlight.has(id)) return
    if (!force && !options.hasPending(id)) {
      clear(id)
      return
    }

    inFlight.add(id)
    let shouldContinue = true
    try {
      const result = await options.poll(id)
      shouldContinue = result !== false
    } finally {
      inFlight.delete(id)
      if (isEnabled.value && shouldContinue && options.hasPending(id)) {
        schedule(id)
      } else {
        clear(id)
      }
    }
  }

  function reconcile() {
    if (!isEnabled.value) {
      clear()
      return
    }

    const activeIds = new Set(toValue(options.ids))

    for (const id of Array.from(timeouts.keys())) {
      if (!activeIds.has(id) || !options.hasPending(id)) {
        clear(id)
      }
    }

    for (const id of activeIds) {
      if (options.hasPending(id)) {
        schedule(id, 0)
      }
    }
  }

  watch([idsSignature, stateSignature, isEnabled], () => {
    reconcile()
  }, { immediate: options.immediate ?? true })

  watch(() => realtime.connectionStatus.value, () => {
    clear()
    reconcile()
  })

  onMounted(() => {
    canPoll.value = true
    realtimeUnsubscribe = realtime.subscribe((message) => {
      if (message.type !== 'JOB_UPDATED') return
      const payload = message.payload as { kind?: unknown, projectId?: unknown } | null
      if (payload?.kind !== 'PAGE_INDEX' || typeof payload.projectId !== 'string') return
      if (toValue(options.ids).includes(payload.projectId)) {
        void pollId(payload.projectId, true)
      }
    })
    reconcile()
  })

  onBeforeUnmount(() => {
    canPoll.value = false
    clear()
    realtimeUnsubscribe?.()
    realtimeUnsubscribe = null
  })

  return {
    clear,
    schedule,
    pollId,
    reconcile,
    isEnabled: computed(() => isEnabled.value)
  }
}
