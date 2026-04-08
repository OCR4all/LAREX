import type {
  CollaborationLeaseOwner,
  CollaborationPageSummary,
  CollaborationProjectSummaryState
} from '@/types/collaboration'

let pageSummaryRealtimeUnsubscribe: (() => void) | null = null
const pendingProjectSummaryFetches = new Map<string, Promise<void>>()

type PageSummaryUpdateMessage = {
  projectId?: string
  pageId?: string
  summary?: CollaborationPageSummary | null
}

function toProjectSummaryState(summaries: CollaborationPageSummary[]): Record<string, CollaborationPageSummary> {
  return Object.fromEntries(summaries.map(summary => [summary.pageId, summary]))
}

export type CollaborationProjectEditorSummary = {
  editor: CollaborationLeaseOwner
  isLive: boolean
  pageIds: string[]
}

export function useCollaborationPageSummary() {
  const realtime = useRealtimeSocket()
  const summaries = useState<CollaborationProjectSummaryState>('collaboration.page-summaries', () => ({}))
  const fetchedProjects = useState<Record<string, boolean>>('collaboration.page-summaries.fetched', () => ({}))

  const applyPageSummaryUpdate = (message: PageSummaryUpdateMessage) => {
    if (!message.projectId || !message.pageId) {
      return
    }

    summaries.value = {
      ...summaries.value,
      [message.projectId]: message.summary
        ? {
            ...(summaries.value[message.projectId] ?? {}),
            [message.pageId]: message.summary
          }
        : Object.fromEntries(
            Object.entries(summaries.value[message.projectId] ?? {})
              .filter(([pageId]) => pageId !== message.pageId)
          )
    }
  }

  const ensureProjectSummary = async (projectId: string | null | undefined, force = false) => {
    if (import.meta.server || !projectId) {
      return
    }

    if (!force && fetchedProjects.value[projectId]) {
      return
    }

    const existingFetch = pendingProjectSummaryFetches.get(projectId)
    if (existingFetch) {
      await existingFetch
      return
    }

    const fetchPromise = (async () => {
      try {
        const data = await $fetch<CollaborationPageSummary[]>(`/api/projects/${projectId}/collaboration/pages`)
        summaries.value = {
          ...summaries.value,
          [projectId]: toProjectSummaryState(data ?? [])
        }
        fetchedProjects.value = {
          ...fetchedProjects.value,
          [projectId]: true
        }
      } catch (error) {
        console.warn('[collaboration-page-summary] Failed to load project summaries:', error)
      } finally {
        pendingProjectSummaryFetches.delete(projectId)
      }
    })()

    pendingProjectSummaryFetches.set(projectId, fetchPromise)
    await fetchPromise
  }

  const getPageSummary = (pageId: string | null | undefined, projectId: string | null | undefined): CollaborationPageSummary | null => {
    if (!pageId || !projectId) {
      return null
    }

    return summaries.value[projectId]?.[pageId] ?? null
  }

  const getProjectEditors = (projectId: string | null | undefined): CollaborationProjectEditorSummary[] => {
    if (!projectId) {
      return []
    }

    const projectSummaries = Object.values(summaries.value[projectId] ?? {})
    const byEditor = new Map<string, CollaborationProjectEditorSummary>()

    for (const summary of projectSummaries) {
      if (!summary.editor?.user.id) {
        continue
      }

      const existing = byEditor.get(summary.editor.user.id)
      if (!existing) {
        byEditor.set(summary.editor.user.id, {
          editor: summary.editor,
          isLive: summary.isLive,
          pageIds: [summary.pageId]
        })
        continue
      }

      existing.isLive ||= summary.isLive
      if (!existing.pageIds.includes(summary.pageId)) {
        existing.pageIds.push(summary.pageId)
      }
    }

    return Array.from(byEditor.values())
      .sort((left, right) => {
        if (left.isLive !== right.isLive) {
          return left.isLive ? -1 : 1
        }
        return left.editor.user.displayName.localeCompare(right.editor.user.displayName)
      })
  }

  if (import.meta.client && !pageSummaryRealtimeUnsubscribe) {
    pageSummaryRealtimeUnsubscribe = realtime.subscribe((message) => {
      if (message.type !== 'COLLAB_PAGE_SUMMARY_UPDATED') {
        return
      }

      applyPageSummaryUpdate(message.payload as PageSummaryUpdateMessage)
    })
    realtime.connect()
  }

  return {
    summaries: readonly(summaries),
    ensureProjectSummary,
    getPageSummary,
    getProjectEditors
  }
}
