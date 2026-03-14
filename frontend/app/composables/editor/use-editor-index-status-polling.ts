import { computed } from 'vue'
import type { PageIndexingStatus } from '@/stores/editor/types'
import { useIndexStatusPolling } from '@/composables/use-index-status-polling'
import { useEditorStore } from '@/stores/editor/editor.store'
import { useEditorSessionStore } from '@/stores/editor/editor.session.store'

export function useEditorIndexStatusPolling() {
  const editorStore = useEditorStore()
  const sessionStore = useEditorSessionStore()

  function hasIndexingPagesInProject(projectId: string): boolean {
    return editorStore.getProjectPages(projectId).some(page => page.indexingStatus === 'INDEXING')
  }

  return useIndexStatusPolling({
    ids: computed(() => [...sessionStore.openedProjectIds]),
    intervalMs: 5000,
    signature: computed(() => {
      return sessionStore.openedProjectIds
        .map(projectId => `${projectId}:${hasIndexingPagesInProject(projectId) ? 1 : 0}`)
        .join('|')
    }),
    hasPending: hasIndexingPagesInProject,
    poll: async (projectId) => {
      try {
        const statuses = await $fetch<Record<string, PageIndexingStatus>>(`/api/projects/${projectId}/pages/index-statuses`)
        editorStore.patchPageIndexingStatuses(projectId, statuses)
      } catch (error) {
        const statusCode = Number(
          (error as { statusCode?: number, response?: { status?: number } })?.statusCode
          ?? (error as { response?: { status?: number } })?.response?.status
          ?? 0
        )

        if (statusCode === 401 || statusCode === 403) {
          return false
        } else {
          console.warn(`[Editor] Failed to poll page index statuses for project ${projectId}:`, error)
        }
      }
    }
  })
}
