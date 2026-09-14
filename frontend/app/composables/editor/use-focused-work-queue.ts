import { computed, onBeforeUnmount, onMounted, ref, watch, type ComputedRef } from 'vue'
import type { EditorQueue, EditorQueuePage } from '~/types'
import { useEditorSessionStore, type FocusedWorkSession } from '@/stores/editor/editor.session.store'

type FocusedWorkQueueOptions = {
  workspaceId: ComputedRef<string | null | undefined>
  currentPageId: ComputedRef<string | null>
  focusedWork: ComputedRef<FocusedWorkSession | null>
  openPage: (page: EditorQueuePage) => Promise<boolean>
  canLeaveCurrentPage?: () => Promise<boolean>
  closeCurrentPage?: () => Promise<boolean>
}

export function useFocusedWorkQueue(options: FocusedWorkQueueOptions) {
  const queue = ref<EditorQueue | null>(null)
  const isLoading = ref(false)
  const error = ref<string | null>(null)
  let refreshTimer: ReturnType<typeof setTimeout> | null = null
  let unsubscribeRealtime: (() => void) | null = null
  const realtime = useRealtimeSocket()

  const pages = computed(() => queue.value?.pages ?? [])
  const actionablePages = computed(() => pages.value.filter(page => !page.blocked))
  const remainingTaskCount = computed(() => queue.value?.openAssignedSubtaskCount ?? 0)
  const remainingPageCount = computed(() => actionablePages.value.length)
  const blockedPageCount = computed(() => queue.value?.blockedAssignedPageCount ?? 0)
  const completedTaskCount = computed(() => options.focusedWork.value?.completedSubtaskIds.length ?? 0)
  const isComplete = computed(() => queue.value !== null && remainingTaskCount.value === 0)

  async function refresh() {
    const workspaceId = options.workspaceId.value
    if (!workspaceId || !options.focusedWork.value) return
    isLoading.value = true
    try {
      queue.value = await $fetch<EditorQueue>(`/api/workspaces/${workspaceId}/tasks/assigned-to-me/queue`)
      error.value = null
    } catch (err: unknown) {
      error.value = err instanceof Error ? err.message : 'Could not load your work queue.'
    } finally {
      isLoading.value = false
    }
  }

  function debouncedRefresh() {
    if (refreshTimer) clearTimeout(refreshTimer)
    refreshTimer = setTimeout(() => {
      refreshTimer = null
      void refresh()
    }, 150)
  }

  function pageIndex(pageId: string | null) {
    return actionablePages.value.findIndex(page => page.pageId === pageId)
  }

  async function openFirstPage() {
    const first = actionablePages.value[0]
    if (first) await options.openPage(first)
  }

  async function openNextPage() {
    const currentIndex = pageIndex(options.currentPageId.value)
    const next = actionablePages.value[currentIndex < 0 ? 0 : currentIndex + 1]
    if (next) await options.openPage(next)
  }

  async function openPreviousPage() {
    const currentIndex = pageIndex(options.currentPageId.value)
    const previous = actionablePages.value[Math.max(0, currentIndex - 1)]
    if (previous && previous.pageId !== options.currentPageId.value) await options.openPage(previous)
  }

  async function reconcileCurrentPage(nextPages: EditorQueuePage[], previousPages: EditorQueuePage[]) {
    const currentPageId = options.currentPageId.value
    const currentPage = nextPages.find(page => page.pageId === currentPageId)
    if (!currentPageId || (currentPage && !currentPage.blocked)) return
    if (actionablePages.value.length > 0) {
      const previousActionable = previousPages.filter(page => !page.blocked)
      const previousIndex = previousActionable.findIndex(page => page.pageId === currentPageId)
      const nextPage = previousIndex >= 0
        ? previousActionable.slice(previousIndex + 1)
            .map(page => actionablePages.value.find(candidate => candidate.pageId === page.pageId))
            .find((page): page is EditorQueuePage => Boolean(page))
        : undefined
      const target = nextPage ?? actionablePages.value[0]
      if (target) await options.openPage(target)
    } else if (!options.canLeaveCurrentPage || await options.canLeaveCurrentPage()) {
      await options.closeCurrentPage?.()
    }
  }

  async function markCompleted(subtaskIds: string[]) {
    if (!subtaskIds.length) return
    const store = useEditorSessionStore()
    store.markFocusedSubtasksCompleted(subtaskIds)
    await refresh()
  }

  async function handleRealtimeMessage(message: { type?: string, payload?: unknown }) {
    if (message.type !== 'TASK_QUEUE_CHANGED') return
    const payload = message.payload as { workspaceId?: unknown } | undefined
    if (payload?.workspaceId !== options.workspaceId.value) return
    debouncedRefresh()
  }

  watch(queue, async (next, previous) => {
    if (!next || !previous) return
    await reconcileCurrentPage(next.pages, previous.pages)
  })

  watch([options.workspaceId, () => Boolean(options.focusedWork.value)], () => {
    if (options.focusedWork.value) void refresh()
  }, { immediate: true })

  watch(realtime.connectionStatus, (status) => {
    if (status === 'connected' && options.focusedWork.value) debouncedRefresh()
  })

  onMounted(() => {
    realtime.connect()
    unsubscribeRealtime = realtime.subscribe((message) => {
      void handleRealtimeMessage(message)
    })
    window.addEventListener('focus', debouncedRefresh)
  })

  onBeforeUnmount(() => {
    unsubscribeRealtime?.()
    if (refreshTimer) clearTimeout(refreshTimer)
    window.removeEventListener('focus', debouncedRefresh)
  })

  return {
    queue,
    pages,
    actionablePages,
    isLoading,
    error,
    remainingTaskCount,
    remainingPageCount,
    blockedPageCount,
    completedTaskCount,
    isComplete,
    refresh,
    openFirstPage,
    openNextPage,
    openPreviousPage,
    markCompleted
  }
}
