import type { Page, PageWorkflowAction, PageWorkflowEvent } from '@/types/project-page'
import PageModalRequestChanges from '@/components/page/modal/request-changes.vue'
import PageSlideoverWorkflowHistory from '@/components/page/slideover/workflow-history.vue'

export interface PageWorkflowChange {
  workspaceId: string
  projectId: string
  pageIds: string[]
}

const REALTIME_FLUSH_MS = 300

export function usePageWorkflow() {
  const overlay = useOverlay()
  const requestChangesModal = overlay.create(PageModalRequestChanges)
  const historySlideover = overlay.create(PageSlideoverWorkflowHistory)

  async function promptForChanges(pageCount = 1): Promise<{ comment: string } | null> {
    const result = await requestChangesModal.open({ pageCount }).result
    return result ?? null
  }

  function openHistory(projectId: string, pageId: string, pageName: string) {
    historySlideover.open({ projectId, pageId, pageName })
  }

  return { promptForChanges, openHistory }
}

export function transitionPages<T extends Pick<Page, 'id'> = Page>(
  projectId: string,
  pageIds: string[],
  action: PageWorkflowAction,
  comment?: string | null
): Promise<T[]> {
  return $fetch<T[]>(`/api/projects/${projectId}/pages/workflow-transitions`, {
    method: 'POST',
    body: { pageIds, action, comment: comment?.trim() || null }
  })
}

export function fetchPageWorkflowEvents(projectId: string, pageId: string): Promise<PageWorkflowEvent[]> {
  return $fetch<PageWorkflowEvent[]>(`/api/projects/${projectId}/pages/${pageId}/workflow-events`)
}

/** Merges bursts of realtime events, so a page finishing several Tasks causes one refresh. */
export function usePageWorkflowRealtime(onChange: (change: PageWorkflowChange) => void | Promise<void>) {
  const realtime = useRealtimeSocket()
  const pending = new Map<string, PageWorkflowChange>()
  let flushTimer: ReturnType<typeof setTimeout> | null = null
  let unsubscribe: (() => void) | null = null

  function flush() {
    flushTimer = null
    const changes = [...pending.values()]
    pending.clear()
    for (const change of changes) void onChange(change)
  }

  onMounted(() => {
    realtime.connect()
    unsubscribe = realtime.subscribe((message) => {
      if (message.type !== 'PAGE_WORKFLOW_CHANGED') return
      const payload = message.payload as Partial<PageWorkflowChange> | undefined
      if (!payload?.workspaceId || !payload.projectId || !Array.isArray(payload.pageIds)) return
      const existing = pending.get(payload.projectId)
      pending.set(payload.projectId, {
        workspaceId: payload.workspaceId,
        projectId: payload.projectId,
        pageIds: [...new Set([...(existing?.pageIds ?? []), ...payload.pageIds])]
      })
      flushTimer ??= setTimeout(flush, REALTIME_FLUSH_MS)
    })
  })

  onBeforeUnmount(() => {
    unsubscribe?.()
    if (flushTimer) clearTimeout(flushTimer)
  })
}
