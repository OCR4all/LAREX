import { computed, onScopeDispose, ref, shallowRef, watch } from 'vue'
import type { PageMovePolicy, PageMovePreview, PageMoveRequest, PageMoveResult } from '@/types/page-move'

type PageMoveTransport = {
  preview: (request: PageMoveRequest) => Promise<PageMovePreview>
  move: (request: PageMoveRequest) => Promise<PageMovePreview>
}

function errorMessage(error: unknown) {
  const candidate = error as { data?: { message?: string }, message?: string } | null
  return candidate?.data?.message || candidate?.message || 'Could not move pages. Please try again.'
}

export function usePageMove(pageIds: string[], transport: PageMoveTransport) {
  const destinationProjectId = ref<string>()
  const conflictPolicy = ref<PageMovePolicy>('SKIP')
  const prefix = ref('')
  const suffix = ref('')
  const preview = shallowRef<PageMovePreview | null>(null)
  const previewPending = ref(false)
  const moving = ref(false)
  const error = ref('')
  let revision = 0
  let timer: ReturnType<typeof setTimeout> | undefined

  const request = computed<PageMoveRequest>(() => ({
    pageIds,
    destinationProjectId: destinationProjectId.value || '',
    conflictPolicy: conflictPolicy.value,
    prefix: conflictPolicy.value === 'RENAME' ? prefix.value : '',
    suffix: conflictPolicy.value === 'RENAME' ? suffix.value : ''
  }))
  const canMove = computed(() => !!preview.value
    && preview.value.movedCount > 0
    && preview.value.blockers.length === 0
    && preview.value.items.every(item => item.blockers.length === 0)
    && !previewPending.value && !moving.value)

  async function refreshPreview({ preserveError = false } = {}) {
    clearTimeout(timer)
    const currentRevision = ++revision
    preview.value = null
    if (!request.value.destinationProjectId) {
      previewPending.value = false
      return
    }
    previewPending.value = true
    try {
      const result = await transport.preview({ ...request.value })
      if (currentRevision === revision) {
        preview.value = result
        if (!preserveError) error.value = ''
      }
    } catch (cause) {
      if (currentRevision === revision) error.value = errorMessage(cause)
    } finally {
      if (currentRevision === revision) previewPending.value = false
    }
  }

  watch(request, () => {
    ++revision
    clearTimeout(timer)
    preview.value = null
    error.value = ''
    previewPending.value = !!request.value.destinationProjectId
    if (request.value.destinationProjectId) timer = setTimeout(() => void refreshPreview(), 250)
  }, { flush: 'sync' })

  onScopeDispose(() => {
    ++revision
    clearTimeout(timer)
  })

  async function move(): Promise<PageMoveResult | null> {
    if (!canMove.value || !preview.value) return null
    const body = { ...request.value, fingerprint: preview.value.fingerprint }
    moving.value = true
    error.value = ''
    try {
      const result = await transport.move(body)
      return { ...result, destinationProjectId: body.destinationProjectId }
    } catch (cause) {
      error.value = errorMessage(cause)
      await refreshPreview({ preserveError: true })
      return null
    } finally {
      moving.value = false
    }
  }

  return {
    destinationProjectId, conflictPolicy, prefix, suffix,
    preview, previewPending, moving, error, canMove, refreshPreview, move
  }
}
