import type { MaybeRefOrGetter } from 'vue'

const ASSIGNMENT_EVENT_TYPES = new Set(['TASK_QUEUE_CHANGED', 'PAGE_WORKFLOW_CHANGED'])
const FLUSH_MS = 500

export function useAssignmentsRealtime(workspaceId: MaybeRefOrGetter<string | null | undefined>, onChange: () => void | Promise<void>) {
  const realtime = useRealtimeSocket()
  let flushTimer: ReturnType<typeof setTimeout> | null = null
  let unsubscribe: (() => void) | null = null

  onMounted(() => {
    realtime.connect()
    unsubscribe = realtime.subscribe((message) => {
      if (!message.type || !ASSIGNMENT_EVENT_TYPES.has(message.type)) return
      const payload = message.payload as { workspaceId?: unknown } | undefined
      if (!payload?.workspaceId || payload.workspaceId !== toValue(workspaceId)) return
      flushTimer ??= setTimeout(() => {
        flushTimer = null
        void onChange()
      }, FLUSH_MS)
    })
  })

  onBeforeUnmount(() => {
    unsubscribe?.()
    if (flushTimer) clearTimeout(flushTimer)
  })
}
