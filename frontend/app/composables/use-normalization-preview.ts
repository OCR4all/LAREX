import type {
  NormalizationPreviewSummary,
  NormalizePreview,
  NormalizeSourcesRequest,
  NormalizeSourcesResponse
} from '@/types/normalization-profile'

const PREVIEW_PAGE_BATCH_SIZE = 20

export function getNormalizationPreviewKey(preview: NormalizePreview): string {
  return [
    preview.pageId,
    preview.textLineId ?? preview.regionId ?? 'row',
    preview.variantIndex ?? 'primary',
    preview.originalText
  ].join('::')
}

export function useNormalizationPreview() {
  const rows = shallowRef<NormalizePreview[]>([])
  const summary = shallowRef<NormalizationPreviewSummary | null>(null)
  const isLoading = ref(false)
  const isComplete = ref(false)
  let activeRun: AbortController | null = null

  function cancel() {
    activeRun?.abort()
    activeRun = null
    isLoading.value = false
  }

  function reset() {
    cancel()
    rows.value = []
    summary.value = null
    isComplete.value = false
  }

  function addResponse(response: NormalizeSourcesResponse) {
    const previous = summary.value
    rows.value = rows.value.concat(response.previews)
    summary.value = {
      analyzedPageCount: (previous?.analyzedPageCount ?? 0) + response.analyzedPageCount,
      analyzedRowCount: (previous?.analyzedRowCount ?? 0) + response.analyzedRowCount,
      changedRowCount: (previous?.changedRowCount ?? 0) + response.changedRowCount,
      changedPageCount: (previous?.changedPageCount ?? 0) + response.changedPageCount,
      scannedPageCount: response.nextPageOffset ?? response.totalPageCount,
      totalPageCount: response.totalPageCount
    }
  }

  async function load(url: string, body: NormalizeSourcesRequest) {
    reset()
    const run = new AbortController()
    activeRun = run
    isLoading.value = true

    try {
      let pageOffset: number | null = 0
      while (pageOffset !== null) {
        const response: NormalizeSourcesResponse = await $fetch<NormalizeSourcesResponse>(url, {
          method: 'POST',
          body: { ...body, pageOffset, pageLimit: PREVIEW_PAGE_BATCH_SIZE },
          signal: run.signal
        })
        if (run.signal.aborted) return
        addResponse(response)
        pageOffset = response.nextPageOffset ?? null
      }
      isComplete.value = true
    } catch (error) {
      if (!run.signal.aborted) throw error
    } finally {
      if (activeRun === run) {
        activeRun = null
        isLoading.value = false
      }
    }
  }

  function removeRow(row: NormalizePreview) {
    const remaining = rows.value.filter(candidate => candidate !== row)
    if (remaining.length === rows.value.length) return

    rows.value = remaining
    if (summary.value) {
      const pageStillChanged = remaining.some(candidate => candidate.pageId === row.pageId)
      summary.value = {
        ...summary.value,
        changedRowCount: summary.value.changedRowCount - 1,
        changedPageCount: summary.value.changedPageCount - (pageStillChanged ? 0 : 1)
      }
    }
  }

  onScopeDispose(cancel)

  return { rows, summary, isLoading, isComplete, load, cancel, reset, removeRow }
}
