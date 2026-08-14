import type { Ref } from 'vue'
import { wsKey } from '@/utils/fetch-keys'
import type { UploadFile, UploadSession } from '@/composables/use-chunked-upload'
import type { UploadSessionStatus, UploadUiFile, UploadUiFileStatus } from '@/stores/upload.store'
import { showApiErrorToast } from '@/utils/error-toast'

type ProjectPageLike = {
  id: string
  indexingStatus?: string
}

type UploadSessionRealtimeEvent = {
  sessionId: string
  status?: UploadSessionStatus
  processedFiles?: number
  failedFiles?: number
  totalFiles?: number
  processingCompletedItems?: number
  processingTotalItems?: number
  processingProgressPercent?: number
  processingCurrentFileName?: string | null
}

type UploadFileRealtimeEvent = {
  sessionId: string
  fileId: string
  fileName?: string
  status?: string
  chunksReceived?: number
  chunkCount?: number
  createdPageId?: string
  createdPageImageId?: string
  errorMessage?: string
  conflictType?: string
}

type UploadSessionFileResponse = {
  id: string
  originalFileName: string
  fileSize: number
  mimeType?: string | null
  status?: string
  chunkCount?: number
  chunksReceived?: number
  errorMessage?: string | null
  createdPageId?: string | null
  createdPageImageId?: string | null
  conflictType?: string | null
}

type UploadSessionDetailResponse = {
  id: string
  projectId: string
  workspaceId: string
  status: UploadSessionStatus
  totalFiles: number
  processedFiles: number
  failedFiles: number
  progressPercent: number
  processingCompletedItems: number
  processingTotalItems: number
  processingProgressPercent: number
  processingCurrentFileName?: string | null
  files: UploadSessionFileResponse[]
}

type UploadSessionSummaryResponse = {
  id: string
  projectId: string
  status: UploadSessionStatus
}

type PageCreatedOrUpdatedRealtimeEvent = {
  projectId: string
  pageId: string
  pageName?: string
  reason?: string
}

type UploadRealtimePayload = {
  streamId: string
  sequence: number
  sessionId: string
  workspaceId: string
  projectId: string
  session: UploadSessionRealtimeEvent
  files?: UploadFileRealtimeEvent[]
  pages?: PageCreatedOrUpdatedRealtimeEvent[]
}

type UploadSessionRuntime = {
  pollTimer: ReturnType<typeof setTimeout> | null
  pollInFlight: boolean
  realtimeStreamId: string | null
  realtimeSequence: number
}

type RefreshPagesOptions = {
  manual?: boolean
  clearUploadChanges?: boolean
}

export interface UseProjectUploadOrchestrationOptions<TPage extends ProjectPageLike = ProjectPageLike> {
  projectId: string
  workspaceId: Ref<string | undefined>
  projectName: Ref<string | undefined>
  pages: Readonly<Ref<TPage[] | null | undefined>>
  pagesPending: Ref<boolean>
  pagesError: Ref<unknown>
  refreshPagesFetch: () => Promise<unknown>
  refreshProject: () => Promise<unknown>
  refreshProjectStatus: () => Promise<unknown>
  onBeforeFinalize?: (session: UploadSession, files: UploadFile[]) => Promise<boolean>
  onIndexingPagesDetected?: () => void
}

const UPLOAD_PROCESSING_POLL_MS = 2000
const UPLOAD_REALTIME_AUDIT_MS = 60_000

function isActiveUploadStatus(status?: UploadSessionStatus): boolean {
  return status === 'PENDING' || status === 'UPLOADING' || status === 'PROCESSING'
}

function isTerminalUploadStatus(status?: UploadSessionStatus): status is Extract<UploadSessionStatus, 'COMPLETED' | 'FAILED' | 'CANCELLED'> {
  return status === 'COMPLETED' || status === 'FAILED' || status === 'CANCELLED'
}

function getSessionProgressPercent(
  status: UploadSessionStatus | undefined,
  processedFiles: number,
  failedFiles: number,
  totalFiles: number,
  fallbackProgressPercent = 0,
  processingProgressPercent = 0
): number {
  if (status === 'PROCESSING') {
    return processingProgressPercent
  }

  if (status === 'PENDING' || status === 'UPLOADING') {
    return fallbackProgressPercent
  }

  if (totalFiles > 0) {
    return Math.round(((processedFiles + failedFiles) / totalFiles) * 100)
  }

  return status && isTerminalUploadStatus(status)
    ? 100
    : fallbackProgressPercent
}

function getRecoveredUploadProgressPercent(files: UploadSessionFileResponse[]): number {
  const totalBytes = files.reduce((sum, file) => sum + Math.max(0, Number(file.fileSize || 0)), 0)
  if (totalBytes <= 0) return 0
  const uploadedBytes = files.reduce((sum, file) => {
    const chunkCount = Math.max(1, Number(file.chunkCount || 1))
    const chunksReceived = Math.min(chunkCount, Math.max(0, Number(file.chunksReceived || 0)))
    return sum + Math.max(0, Number(file.fileSize || 0)) * (chunksReceived / chunkCount)
  }, 0)
  return Math.round((uploadedBytes / totalBytes) * 100)
}

export function getUploadSessionRealtimeProgressPercent(event: UploadSessionRealtimeEvent): number | undefined {
  const shouldUpdateFromServer
    = event.status === 'PROCESSING'
      || event.status === 'COMPLETED'
      || event.status === 'FAILED'
      || event.status === 'CANCELLED'

  if (
    !shouldUpdateFromServer
    || typeof event.totalFiles !== 'number'
    || typeof event.processedFiles !== 'number'
    || typeof event.failedFiles !== 'number'
  ) {
    return undefined
  }

  return getSessionProgressPercent(
    event.status,
    event.processedFiles,
    event.failedFiles,
    event.totalFiles,
    0,
    event.processingProgressPercent
  )
}

export function shouldApplyUploadRealtimeSequence(
  currentStreamId: string | null,
  currentSequence: number,
  nextStreamId: string,
  nextSequence: number
): boolean {
  return currentStreamId !== nextStreamId || nextSequence > currentSequence
}

function hasIndexingPages(list: ProjectPageLike[] | null | undefined): boolean {
  return (list ?? []).some(page => page.indexingStatus === 'INDEXING')
}

function toUploadFileUiStatus(status?: string | null): UploadUiFileStatus {
  const normalized = (status ?? '').toLowerCase()
  if (normalized === 'pending' || normalized === 'uploading' || normalized === 'uploaded' || normalized === 'processing' || normalized === 'completed' || normalized === 'failed' || normalized === 'conflict' || normalized === 'skipped') {
    return normalized
  }
  return 'pending'
}

function toLocalUiFile(file: UploadFile): UploadUiFile {
  return {
    id: file.id,
    fileName: file.fileName,
    fileSize: file.fileSize,
    mimeType: file.mimeType,
    baseName: file.baseName,
    variant: file.variant,
    status: file.status,
    progress: file.progress,
    chunksReceived: file.chunksReceived,
    totalChunks: file.totalChunks,
    error: file.error,
    createdPageId: file.createdPageId,
    createdPageImageId: file.createdPageImageId,
    conflictType: file.conflictType,
    conflictResolution: file.conflictResolution
  }
}

function mapServerFileToRecoveredUiFile(file: UploadSessionFileResponse): UploadUiFile {
  const totalChunks = Math.max(1, Number(file.chunkCount || 1))
  const chunksReceived = Math.max(0, Number(file.chunksReceived || 0))
  const mimeType = file.mimeType || 'application/octet-stream'
  const fileName = file.originalFileName || 'upload-file'

  return {
    id: file.id,
    fileName,
    fileSize: Number(file.fileSize || 0),
    mimeType,
    baseName: fileName.includes('.') ? fileName.substring(0, fileName.indexOf('.')) : fileName,
    variant: fileName.includes('.') ? fileName.substring(fileName.indexOf('.') + 1) : '',
    status: toUploadFileUiStatus(file.status),
    progress: totalChunks > 0 ? Math.round((chunksReceived / totalChunks) * 100) : 0,
    chunksReceived,
    totalChunks,
    error: file.errorMessage || undefined,
    createdPageId: file.createdPageId || undefined,
    createdPageImageId: file.createdPageImageId || undefined,
    conflictType: file.conflictType || undefined
  }
}

export function useProjectUploadOrchestration<TPage extends ProjectPageLike>(options: UseProjectUploadOrchestrationOptions<TPage>) {
  const uploadStore = useUploadStore()
  const uploadSessionActions = useUploadSessionActions()
  const realtime = useRealtimeSocket()

  const isManualPagesRefresh = ref(false)
  const hasLoadedPagesOnce = ref(false)
  const changedUploadPageIds = ref<Set<string>>(new Set())
  const hasUnrefreshedUploadChanges = ref(false)
  const unrefreshedUploadPageCount = computed(() => changedUploadPageIds.value.size)
  const showPagesLoadingSpinner = computed(() => isManualPagesRefresh.value || (!hasLoadedPagesOnce.value && options.pagesPending.value))

  watch([options.pages, options.pagesError], ([nextPages, nextError]) => {
    if (nextPages !== null || nextError) {
      hasLoadedPagesOnce.value = true
    }
  }, { immediate: true })

  const currentUploadSessionId = ref<string | null>(null)
  const tempUploadSessionId = ref<string | null>(null)
  let realtimeUnsubscribe: (() => void) | null = null
  let restoreInFlight: Promise<void> | null = null
  let orchestrationMounted = false

  const uploadSessionRuntimes = ref<Map<string, UploadSessionRuntime>>(new Map())
  const terminalSessionsHandled = ref<Set<string>>(new Set())

  const {
    isUploading,
    session: uploadSession,
    error: _uploadError,
    addFiles,
    startUpload,
    cancelUpload,
    clearFiles
  } = useChunkedUpload({
    workspaceId: options.workspaceId,
    projectId: computed(() => options.projectId) as Ref<string | undefined>,
    onProgress: (session) => {
      const sessionId = currentUploadSessionId.value
      if (!session || !sessionId) return

      const existingUpload = uploadStore.activeUploads.get(sessionId)
      if (existingUpload && isTerminalUploadStatus(existingUpload.status)) {
        return
      }

      if (session.status === 'PROCESSING') {
        uploadStore.updateUploadProgress(sessionId, {
          status: 'PROCESSING',
          progressPercent: session.processingProgressPercent,
          processingCompletedItems: session.processingCompletedItems,
          processingTotalItems: session.processingTotalItems,
          processingProgressPercent: session.processingProgressPercent,
          ...(session.processingCurrentFileName ? { processingCurrentFileName: session.processingCurrentFileName } : {})
        })
        return
      }

      uploadStore.updateUploadProgress(sessionId, {
        status: 'UPLOADING',
        progressPercent: session.progressPercent
      })
    },
    onFileComplete: (file) => {
      const sessionId = currentUploadSessionId.value
      if (!sessionId || !file.id) return

      uploadStore.updateFileProgress(sessionId, file.id, {
        fileName: file.fileName,
        status: file.status,
        progress: file.progress,
        chunksReceived: file.chunksReceived
      })
    },
    onSessionComplete: async (session) => {
      const sessionId = currentUploadSessionId.value
      if (!session || !sessionId) return

      const existingUpload = uploadStore.activeUploads.get(sessionId)
      if (!existingUpload || !isTerminalUploadStatus(existingUpload.status)) {
        uploadStore.updateUploadProgress(sessionId, {
          status: 'PROCESSING',
          ...(existingUpload?.status !== 'PROCESSING'
            ? {
                progressPercent: 0,
                processingCompletedItems: 0,
                processingTotalItems: 0,
                processingProgressPercent: 0,
                processingCurrentFileName: undefined
              }
            : {})
        })
      }

      scheduleUploadSessionPoll(sessionId, UPLOAD_PROCESSING_POLL_MS)
      clearFiles()
    },
    onBeforeFinalize: (session, files) => options.onBeforeFinalize?.(session, files) ?? Promise.resolve(true),
    onError: (error, file) => {
      const sessionId = currentUploadSessionId.value
      if (uploadSessionActions.shouldSuppressUploadError(sessionId, error)) {
        return
      }

      if (sessionId) {
        uploadStore.completeUpload(sessionId, 'FAILED', error.message)
      }

      showApiErrorToast({
        title: 'Upload failed',
        error,
        fallback: file ? `Failed to upload ${file.fileName}: ${error.message}` : error.message
      })
    }
  })

  function ensureUploadSessionRuntime(sessionId: string): UploadSessionRuntime {
    const existing = uploadSessionRuntimes.value.get(sessionId)
    if (existing) return existing
    const runtime: UploadSessionRuntime = {
      pollTimer: null,
      pollInFlight: false,
      realtimeStreamId: null,
      realtimeSequence: 0
    }
    uploadSessionRuntimes.value.set(sessionId, runtime)
    return runtime
  }

  function clearUploadSessionPollTimer(sessionId: string) {
    const runtime = uploadSessionRuntimes.value.get(sessionId)
    if (!runtime?.pollTimer) return
    clearTimeout(runtime.pollTimer)
    runtime.pollTimer = null
  }

  function stopMonitoringUploadSession(sessionId: string, options: { removeRuntime?: boolean } = {}) {
    clearUploadSessionPollTimer(sessionId)
    const runtime = uploadSessionRuntimes.value.get(sessionId)
    if (runtime) {
      runtime.pollInFlight = false
      runtime.realtimeStreamId = null
      runtime.realtimeSequence = 0
    }
    if (options.removeRuntime !== false) {
      uploadSessionRuntimes.value.delete(sessionId)
    }
  }

  function stopAllUploadSessionMonitoring() {
    for (const sessionId of uploadSessionRuntimes.value.keys()) {
      stopMonitoringUploadSession(sessionId)
    }
  }

  function markUploadPagesChanged(pages: PageCreatedOrUpdatedRealtimeEvent[] = []) {
    hasUnrefreshedUploadChanges.value = true
    if (pages.length === 0) return

    const nextPageIds = new Set(changedUploadPageIds.value)
    for (const page of pages) {
      if (page.pageId) nextPageIds.add(page.pageId)
    }
    changedUploadPageIds.value = nextPageIds
  }

  function clearUploadPageChanges() {
    hasUnrefreshedUploadChanges.value = false
    changedUploadPageIds.value = new Set()
  }

  async function refreshPagesData(refreshOptions: RefreshPagesOptions = {}) {
    const manual = refreshOptions.manual === true
    const clearUploadChanges = manual || refreshOptions.clearUploadChanges === true

    if (manual) {
      isManualPagesRefresh.value = true
    }

    try {
      await options.refreshPagesFetch()
      if (clearUploadChanges && !options.pagesError.value) {
        clearUploadPageChanges()
        if (hasIndexingPages(options.pages.value)) {
          options.onIndexingPagesDetected?.()
        }
      }
    } finally {
      if (manual) {
        isManualPagesRefresh.value = false
      }
    }
  }

  async function syncProjectDataAfterUploadTerminal() {
    const workspaceId = options.workspaceId.value
    await Promise.allSettled([
      refreshPagesData({ clearUploadChanges: true }),
      options.refreshProject(),
      options.refreshProjectStatus(),
      workspaceId ? refreshNuxtData(wsKey(workspaceId, 'storage', 'quota')) : Promise.resolve()
    ])
  }

  function upsertUploadStoreSession(detail: UploadSessionDetailResponse) {
    const mappedFiles = (detail.files ?? []).map(mapServerFileToRecoveredUiFile)
    const recoveredUploadProgressPercent = getRecoveredUploadProgressPercent(detail.files ?? [])
    const existingUpload = uploadStore.activeUploads.get(detail.id)
    if (!existingUpload) {
      uploadStore.registerUpload(
        detail.id,
        detail.projectId,
        options.projectName.value || 'Project Upload',
        detail.workspaceId || (options.workspaceId.value as string),
        mappedFiles
      )
    }

    const preserveLocalUploadState = !!existingUpload
      && (detail.status === 'PENDING' || detail.status === 'UPLOADING')
      && currentUploadSessionId.value === detail.id

    uploadStore.updateUploadProgress(detail.id, {
      status: detail.status,
      totalFiles: detail.totalFiles,
      processedFiles: detail.processedFiles,
      failedFiles: detail.failedFiles,
      progressPercent: preserveLocalUploadState
        ? existingUpload.progressPercent
        : getSessionProgressPercent(
            detail.status,
            detail.processedFiles,
            detail.failedFiles,
            detail.totalFiles,
            detail.status === 'PENDING' || detail.status === 'UPLOADING'
              ? recoveredUploadProgressPercent
              : detail.progressPercent,
            detail.processingProgressPercent
          ),
      processingCompletedItems: detail.processingCompletedItems,
      processingTotalItems: detail.processingTotalItems,
      processingProgressPercent: detail.processingProgressPercent,
      processingCurrentFileName: detail.processingCurrentFileName || undefined,
      ...(!preserveLocalUploadState ? { files: mappedFiles } : {})
    })
  }

  async function handleTerminalUploadStatus(
    sessionId: string,
    status: Extract<UploadSessionStatus, 'COMPLETED' | 'FAILED' | 'CANCELLED'>,
    handlerOptions: { updateStore?: boolean } = {}
  ) {
    if (terminalSessionsHandled.value.has(sessionId)) return
    terminalSessionsHandled.value.add(sessionId)

    if (handlerOptions.updateStore !== false) {
      if (status === 'FAILED') {
        uploadStore.completeUpload(sessionId, 'FAILED', 'Processing failed')
      } else if (status === 'CANCELLED') {
        uploadStore.completeUpload(sessionId, 'CANCELLED')
      } else {
        uploadStore.completeUpload(sessionId, 'COMPLETED')
      }
    }

    if (currentUploadSessionId.value === sessionId) {
      currentUploadSessionId.value = null
    }

    uploadSessionActions.clearLocalAbortHandler(sessionId)
    stopMonitoringUploadSession(sessionId)
    const completedUpload = uploadStore.activeUploads.get(sessionId)
    if (completedUpload && completedUpload.processedFiles > 0) {
      markUploadPagesChanged()
    }
    await syncProjectDataAfterUploadTerminal()

    if (status === 'FAILED') {
      showApiErrorToast({
        title: 'Processing failed',
        error: new Error('Some files could not be processed'),
        fallback: 'Some files could not be processed'
      })
    }
  }

  function applyUploadSessionRealtimeEvent(event: UploadSessionRealtimeEvent) {
    if (!event?.sessionId) return

    const serverProgressPercent = getUploadSessionRealtimeProgressPercent(event)

    uploadStore.updateUploadProgress(event.sessionId, {
      ...(event.status ? { status: event.status } : {}),
      ...(typeof event.processedFiles === 'number' ? { processedFiles: event.processedFiles } : {}),
      ...(typeof event.failedFiles === 'number' ? { failedFiles: event.failedFiles } : {}),
      ...(typeof event.processingCompletedItems === 'number' ? { processingCompletedItems: event.processingCompletedItems } : {}),
      ...(typeof event.processingTotalItems === 'number' ? { processingTotalItems: event.processingTotalItems } : {}),
      ...(typeof event.processingProgressPercent === 'number'
        ? {
            processingProgressPercent: event.processingProgressPercent
          }
        : {}),
      ...(event.processingCurrentFileName !== undefined
        ? { processingCurrentFileName: event.processingCurrentFileName || undefined }
        : {}),
      ...(serverProgressPercent !== undefined
        ? { progressPercent: serverProgressPercent }
        : {})
    })

    if (event.status && isTerminalUploadStatus(event.status)) {
      void handleTerminalUploadStatus(event.sessionId, event.status)
    }
  }

  function applyUploadFileRealtimeEvent(event: UploadFileRealtimeEvent) {
    if (!event?.sessionId || !event?.fileId) return
    const status = event.status ? event.status.toLowerCase() as UploadUiFileStatus : undefined
    uploadStore.updateFileProgress(event.sessionId, event.fileId, {
      ...(status ? { status } : {}),
      ...(typeof event.chunksReceived === 'number' ? { chunksReceived: event.chunksReceived } : {}),
      ...(typeof event.chunkCount === 'number' ? { totalChunks: event.chunkCount } : {}),
      ...(event.fileName ? { fileName: event.fileName } : {}),
      ...(event.createdPageId ? { createdPageId: event.createdPageId } : {}),
      ...(event.createdPageImageId ? { createdPageImageId: event.createdPageImageId } : {}),
      ...(event.errorMessage ? { error: event.errorMessage } : {}),
      ...(event.conflictType ? { conflictType: event.conflictType } : {})
    })
  }

  function scheduleUploadSessionPoll(sessionId: string, delayMs?: number) {
    if (import.meta.server) return
    if (!sessionId) return
    const runtime = ensureUploadSessionRuntime(sessionId)
    if (runtime.pollTimer) return
    const effectiveDelay = delayMs ?? (realtime.connectionStatus.value === 'connected'
      ? UPLOAD_REALTIME_AUDIT_MS
      : UPLOAD_PROCESSING_POLL_MS)
    runtime.pollTimer = setTimeout(() => {
      runtime.pollTimer = null
      void pollBackendProcessing(sessionId)
    }, effectiveDelay)
  }

  function isUploadRealtimePayload(payload: unknown): payload is UploadRealtimePayload {
    if (!payload || typeof payload !== 'object') return false
    const candidate = payload as Partial<UploadRealtimePayload>
    return typeof candidate.streamId === 'string'
      && typeof candidate.sequence === 'number'
      && typeof candidate.sessionId === 'string'
      && typeof candidate.workspaceId === 'string'
      && typeof candidate.projectId === 'string'
      && !!candidate.session
      && typeof candidate.session === 'object'
      && candidate.session.sessionId === candidate.sessionId
  }

  function applyUploadRealtimePayload(payload: UploadRealtimePayload) {
    const workspaceId = options.workspaceId.value
    if (payload.projectId !== options.projectId || !workspaceId || payload.workspaceId !== workspaceId) {
      return
    }

    const runtime = ensureUploadSessionRuntime(payload.sessionId)
    if (!shouldApplyUploadRealtimeSequence(
      runtime.realtimeStreamId,
      runtime.realtimeSequence,
      payload.streamId,
      payload.sequence
    )) {
      return
    }
    runtime.realtimeStreamId = payload.streamId
    runtime.realtimeSequence = payload.sequence

    clearUploadSessionPollTimer(payload.sessionId)
    applyUploadSessionRealtimeEvent(payload.session)
    for (const fileEvent of payload.files ?? []) {
      applyUploadFileRealtimeEvent(fileEvent)
    }
    if ((payload.pages?.length ?? 0) > 0) markUploadPagesChanged(payload.pages)

    if (!isTerminalUploadStatus(payload.session.status)) {
      scheduleUploadSessionPoll(payload.sessionId)
    }
  }

  function handleRealtimeMessage(message: { type?: string, payload?: unknown }) {
    if (message.type !== 'UPLOAD_UPDATED' || !isUploadRealtimePayload(message.payload)) {
      return
    }
    applyUploadRealtimePayload(message.payload)
  }

  async function pollBackendProcessing(sessionId: string) {
    const workspaceId = options.workspaceId.value
    if (!workspaceId) return

    const runtime = ensureUploadSessionRuntime(sessionId)
    if (runtime.pollInFlight) {
      scheduleUploadSessionPoll(sessionId, UPLOAD_PROCESSING_POLL_MS)
      return
    }
    runtime.pollInFlight = true
    const realtimeStreamAtRequest = runtime.realtimeStreamId
    const realtimeSequenceAtRequest = runtime.realtimeSequence

    try {
      const status = await $fetch<UploadSessionDetailResponse>(
        `/api/workspaces/${workspaceId}/projects/${options.projectId}/upload-sessions/${sessionId}`
      )

      if (
        uploadSessionRuntimes.value.get(sessionId) !== runtime
        || runtime.realtimeStreamId !== realtimeStreamAtRequest
        || runtime.realtimeSequence !== realtimeSequenceAtRequest
      ) {
        if (uploadSessionRuntimes.value.get(sessionId) === runtime) {
          scheduleUploadSessionPoll(sessionId)
        }
        return
      }

      upsertUploadStoreSession(status)

      if (isTerminalUploadStatus(status.status)) {
        await handleTerminalUploadStatus(sessionId, status.status)
        return
      }

      if (realtime.connectionStatus.value !== 'connected' && status.status === 'PROCESSING') {
        markUploadPagesChanged()
      }
      scheduleUploadSessionPoll(sessionId)
    } catch {
      if (uploadSessionRuntimes.value.get(sessionId) !== runtime) {
        return
      }
      const upload = uploadStore.activeUploads.get(sessionId)
      if (uploadSessionActions.isCancellationInProgress(sessionId) || upload?.status === 'CANCELLED') {
        stopMonitoringUploadSession(sessionId)
        return
      }

      scheduleUploadSessionPoll(sessionId, UPLOAD_PROCESSING_POLL_MS)
    } finally {
      runtime.pollInFlight = false
    }
  }

  async function restoreProjectUploadSessions() {
    if (import.meta.server) return
    const workspaceId = options.workspaceId.value
    if (!workspaceId) return

    try {
      const sessions = await $fetch<UploadSessionSummaryResponse[]>('/api/upload-sessions')
      const relevantSessions = sessions.filter(session =>
        session.projectId === options.projectId
        && isActiveUploadStatus(session.status)
      )

      await Promise.all(relevantSessions.map(async (sessionSummary) => {
        try {
          const detail = await $fetch<UploadSessionDetailResponse>(
            `/api/workspaces/${workspaceId}/projects/${options.projectId}/upload-sessions/${sessionSummary.id}`
          )
          if (!orchestrationMounted) return

          upsertUploadStoreSession(detail)

          if (isActiveUploadStatus(detail.status)) {
            ensureUploadSessionRuntime(detail.id)
            scheduleUploadSessionPoll(detail.id)
          } else if (isTerminalUploadStatus(detail.status)) {
            await handleTerminalUploadStatus(detail.id, detail.status)
          }
        } catch {
          // Best effort recovery.
        }
      }))
    } catch {
      // Best effort recovery.
    }
  }

  function requestProjectUploadSessionRestore() {
    if (restoreInFlight) return restoreInFlight
    restoreInFlight = restoreProjectUploadSessions().finally(() => {
      restoreInFlight = null
    })
    return restoreInFlight
  }

  function resyncKnownUploadSessions(delayMs = 0) {
    for (const upload of uploadStore.uploadsArray) {
      if (
        upload.projectId !== options.projectId
        || upload.workspaceId !== options.workspaceId.value
        || !isActiveUploadStatus(upload.status)
      ) {
        continue
      }
      ensureUploadSessionRuntime(upload.sessionId)
    }

    for (const [sessionId] of uploadSessionRuntimes.value) {
      const upload = uploadStore.activeUploads.get(sessionId)
      if (!upload || !isActiveUploadStatus(upload.status)) continue
      clearUploadSessionPollTimer(sessionId)
      scheduleUploadSessionPoll(sessionId, delayMs)
    }
  }

  function startProjectUpload(selectedFiles: File[], baseNameOverrides?: Record<string, string>) {
    const uploadFilesList = addFiles(selectedFiles)
    if (baseNameOverrides) {
      for (const file of uploadFilesList) {
        const overrideBaseName = baseNameOverrides[file.fileName]
        if (overrideBaseName) {
          file.baseName = overrideBaseName
        }
      }
    }

    const tempSessionId = `pending-${Date.now()}`
    tempUploadSessionId.value = tempSessionId
    currentUploadSessionId.value = tempSessionId

    uploadStore.registerUpload(
      tempSessionId,
      options.projectId,
      options.projectName.value || 'Unknown Project',
      options.workspaceId.value as string,
      uploadFilesList.map(toLocalUiFile)
    )
    uploadSessionActions.registerLocalAbortHandler(tempSessionId, async () => {
      await cancelUpload()
      clearFiles()
    })

    void runProjectUpload()
  }

  async function runProjectUpload() {
    try {
      await startUpload()
    } catch (error) {
      if (uploadSessionActions.shouldSuppressUploadError(currentUploadSessionId.value, error)) {
        return
      }

      const message = error instanceof Error ? error.message : 'Failed to start upload'
      const activeSessionId = currentUploadSessionId.value
      if (activeSessionId) {
        uploadStore.removeUpload(activeSessionId)
      }
      currentUploadSessionId.value = null
      tempUploadSessionId.value = null
      showApiErrorToast({
        title: 'Upload failed',
        error,
        fallback: message
      })
    }
  }

  watch(() => uploadSession.value?.id, (newSessionId) => {
    if (!newSessionId || !tempUploadSessionId.value || tempUploadSessionId.value === newSessionId) return

    const tempSessionId = tempUploadSessionId.value
    uploadStore.replaceUploadSessionId(tempSessionId, newSessionId)
    uploadSessionActions.moveLocalAbortHandler(tempSessionId, newSessionId)

    currentUploadSessionId.value = newSessionId
    tempUploadSessionId.value = null
    ensureUploadSessionRuntime(newSessionId)
    scheduleUploadSessionPoll(newSessionId)
  })

  watch(
    () => uploadStore.uploadsArray.map(upload => `${upload.sessionId}:${upload.status}`).join('|'),
    () => {
      for (const [sessionId] of uploadSessionRuntimes.value) {
        const upload = uploadStore.activeUploads.get(sessionId)
        if (!upload || !isTerminalUploadStatus(upload.status)) {
          continue
        }
        void handleTerminalUploadStatus(sessionId, upload.status, { updateStore: false })
      }
    }
  )

  watch(realtime.connectionStatus, (status, previousStatus) => {
    if (import.meta.server || !orchestrationMounted) return
    if (status === 'connected') {
      resyncKnownUploadSessions(0)
      void requestProjectUploadSessionRestore()
    } else if (previousStatus === 'connected' || status === 'error' || status === 'disconnected') {
      resyncKnownUploadSessions(0)
    }
  })

  onMounted(() => {
    orchestrationMounted = true
    realtimeUnsubscribe = realtime.subscribe(handleRealtimeMessage)
    realtime.connect()
    void requestProjectUploadSessionRestore()
  })

  onBeforeUnmount(() => {
    orchestrationMounted = false
    realtimeUnsubscribe?.()
    realtimeUnsubscribe = null
    stopAllUploadSessionMonitoring()
  })

  return {
    isUploading,
    isManualPagesRefresh,
    hasUnrefreshedUploadChanges,
    unrefreshedUploadPageCount,
    showPagesLoadingSpinner,
    refreshPagesData,
    startProjectUpload
  }
}
