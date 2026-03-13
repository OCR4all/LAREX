import type { UploadSessionStatus } from '@/stores/upload.store'

type LocalAbortHandler = () => Promise<void> | void

const localAbortHandlers = new Map<string, LocalAbortHandler>()
const cancellationTokens = new Map<string, symbol>()

function isActiveUploadStatus(status: UploadSessionStatus): boolean {
  return status === 'PENDING' || status === 'UPLOADING' || status === 'PROCESSING'
}

function isClientTemporarySessionId(sessionId: string): boolean {
  return sessionId.startsWith('pending-')
}

function toErrorMessage(error: unknown): string {
  if (error instanceof Error) return error.message
  return String(error ?? '')
}

function isCancelLikeMessage(message: string): boolean {
  const normalized = message.toLowerCase()
  return normalized.includes('abort')
    || normalized.includes('aborted')
    || normalized.includes('cancelled')
    || normalized.includes('session is not active')
}

export function useUploadSessionActions() {
  const uploadStore = useUploadStore()

  function registerLocalAbortHandler(sessionId: string, handler: LocalAbortHandler) {
    if (!sessionId) return
    localAbortHandlers.set(sessionId, handler)
  }

  function clearLocalAbortHandler(sessionId: string) {
    if (!sessionId) return
    localAbortHandlers.delete(sessionId)
  }

  function moveLocalAbortHandler(previousSessionId: string, nextSessionId: string) {
    if (!previousSessionId || !nextSessionId || previousSessionId === nextSessionId) return
    const handler = localAbortHandlers.get(previousSessionId)
    if (!handler) return
    localAbortHandlers.set(nextSessionId, handler)
    localAbortHandlers.delete(previousSessionId)
  }

  function isCancellationInProgress(sessionId: string | null | undefined): boolean {
    if (!sessionId) return false
    return cancellationTokens.has(sessionId) || uploadStore.isCancelling(sessionId)
  }

  function shouldSuppressUploadError(sessionId: string | null | undefined, error: unknown): boolean {
    if (!sessionId) return false
    const upload = uploadStore.activeUploads.get(sessionId)
    if (!upload) return false

    if (isCancellationInProgress(sessionId)) return true
    if (upload.status !== 'CANCELLED') return false
    return isCancelLikeMessage(toErrorMessage(error))
  }

  async function cancelUploadBySessionId(sessionId: string): Promise<void> {
    const upload = uploadStore.activeUploads.get(sessionId)
    if (!upload) return
    if (!isActiveUploadStatus(upload.status)) return
    if (uploadStore.isCancelling(sessionId)) return

    const cancellationToken = Symbol(sessionId)
    cancellationTokens.set(sessionId, cancellationToken)
    uploadStore.setCancelling(sessionId, true)

    try {
      const abortHandler = localAbortHandlers.get(sessionId)
      if (abortHandler) {
        await abortHandler()
      }

      if (!isClientTemporarySessionId(sessionId)) {
        await $fetch(`/api/workspaces/${upload.workspaceId}/projects/${upload.projectId}/upload-sessions/${sessionId}`, {
          method: 'DELETE'
        })
      }

      if (cancellationTokens.get(sessionId) !== cancellationToken) return
      uploadStore.completeUpload(sessionId, 'CANCELLED')
    } catch (error) {
      if (cancellationTokens.get(sessionId) === cancellationToken) {
        uploadStore.setCancelling(sessionId, false)
      }
      throw error
    } finally {
      if (cancellationTokens.get(sessionId) === cancellationToken) {
        cancellationTokens.delete(sessionId)
      }
      clearLocalAbortHandler(sessionId)
    }
  }

  return {
    registerLocalAbortHandler,
    clearLocalAbortHandler,
    moveLocalAbortHandler,
    isCancellationInProgress,
    shouldSuppressUploadError,
    cancelUploadBySessionId
  }
}
