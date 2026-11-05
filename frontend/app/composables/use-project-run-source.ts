import { ref, watch } from 'vue'
import type { Ref } from 'vue'
import { extractApiErrorMessage } from '@/utils/api-error'
import type { DatasetSummary, ProjectDatasetResponse } from '@/types/dataset'

export interface DatasetRunPageSelection {
  projectId: string
  pageIds: string[]
}

/** Carries selected pages from a project to the run form, where they are too many for a URL. */
export function useDatasetRunPageSelection() {
  return useState<DatasetRunPageSelection | null>('dataset-run-page-selection', () => null)
}

export function useProjectRunSource(options: {
  workspaceId: Ref<string | null | undefined>
  initialProjectIds?: string[]
}) {
  const projectIds = ref<string[]>(options.initialProjectIds ?? [])
  const dataset = ref<DatasetSummary | null>(null)
  const pagesWithoutXml = ref(0)
  const syncing = ref(false)
  const error = ref<string>()
  let requestSequence = 0

  async function sync() {
    const sequence = ++requestSequence
    error.value = undefined
    if (!options.workspaceId.value || !projectIds.value.length) {
      dataset.value = null
      pagesWithoutXml.value = 0
      syncing.value = false
      return
    }
    syncing.value = true
    try {
      const response = await $fetch<ProjectDatasetResponse>(`/api/workspaces/${options.workspaceId.value}/project-datasets`, {
        method: 'POST',
        body: { projectIds: projectIds.value }
      })
      if (sequence !== requestSequence) return
      dataset.value = response.dataset
      pagesWithoutXml.value = response.pagesWithoutXml
    } catch (requestError: unknown) {
      if (sequence !== requestSequence) return
      dataset.value = null
      pagesWithoutXml.value = 0
      error.value = extractApiErrorMessage(requestError, 'Could not prepare the pages of these projects.')
    } finally {
      if (sequence === requestSequence) syncing.value = false
    }
  }

  watch(projectIds, () => {
    void sync()
  })

  return { projectIds, dataset, pagesWithoutXml, syncing, error, sync }
}
