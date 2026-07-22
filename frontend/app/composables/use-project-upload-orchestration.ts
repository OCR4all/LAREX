import type { Ref } from 'vue'
import { wsKey } from '@/utils/fetch-keys'
import type { UploadFile } from '@/composables/use-chunked-upload'
import type { UploadSessionStatus, UploadUiFile, UploadUiFileStatus } from '@/stores/upload.store'
import { showApiErrorToast } from '@/utils/error-toast'

type ProjectPageLike = {
  id: string
  indexingStatus?: string
}

type UploadSessionSseEvent = {
  sessionId: string
  status?: UploadSessionStatus
  processedFiles?: number
  failedFiles?: number
  totalFiles?: number
}

type UploadFileSseEvent = {
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
  files: UploadSessionFileResponse[]
}

type UploadSessionSummaryResponse = {
  id: string
  projectId: string
  status: UploadSessionStatus
}

type PageCreatedOrUpdatedSseEvent = {
  projectId: string
}

type UploadSessionRuntime = {
  eventSource: EventSource | null
  eventsConnected: boolean
  pollTimer: ReturnType<typeof setTimeout> | null
  pollInFlight: boolean
  pollCount: number
}

type RefreshPagesOptions = {
  manual?: boolean
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
  onIndexingPagesDetected?: () => void
}

const UPLOAD_PROCESSING_POLL_MS = 2000
const UPLOAD_PROCESSING_MAX_POLLS = 300

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
  fallbackProgressPercent = 0
): number {
  if (status === 'PROCESSING') {
    return 100
  }

  if (totalFiles > 0) {
    return Math.round(((processedFiles + failedFiles) / totalFiles) * 100)
  }

  return status && isTerminalUploadStatus(status)
    ? 100
    : fallbackProgressPercent
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
    source: 'local',
    file: file.file,
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
    source: 'recovered',
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

  const isManualPagesRefresh = ref(false)
  const hasLoadedPagesOnce = ref(false)
  const showPagesLoadingSpinner = computed(() => isManualPagesRefresh.value || (!hasLoadedPagesOnce.value && options.pagesPending.value))

  watch([options.pages, options.pagesError], ([nextPages, nextError]) => {
    if (nextPages !== null || nextError) {
      hasLoadedPagesOnce.value = true
    }
  }, { immediate: true })

  const currentUploadSessionId = ref<string | null>(null)
  const tempUploadSessionId = ref<string | null>(null)
  let pageRefreshDebounceTimer: ReturnType<typeof setTimeout> | null = null

  const uploadSessionRuntimes = ref<Map<string, UploadSessionRuntime>>(new Map())
  const terminalSessionsHandled = ref<Set<string>>(new Set())

  const {
    isUploading,
    files: uploadFiles,
    session: uploadSession,
    error: _uploadError,
    addFiles,
    startUpload,
    cancelUpload,
    clearFiles,
    overallProgress: _overallProgress
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
          progressPercent: 100
        })
        return
      }

      const localFiles = uploadFiles.value
      const totalChunks = localFiles.reduce((sum, f) => sum + f.totalChunks, 0)
      const completedChunks = localFiles.reduce((sum, f) => sum + f.chunksReceived, 0)
      const calculatedProgress = totalChunks > 0 ? Math.round((completedChunks / totalChunks) * 100) : 0

      uploadStore.updateUploadProgress(sessionId, {
        status: 'UPLOADING',
        progressPercent: calculatedProgress
      })
    },
    onFileComplete: (file) => {
      const sessionId = currentUploadSessionId.value
      if (!sessionId || !file.id) return

      uploadStore.updateFileProgress(sessionId, file.id, {
        status: file.status,
        progress: file.progress,
        chunksReceived: file.chunksReceived
      })
    },
    onSessionComplete: async (session) => {
      const sessionId = currentUploadSessionId.value
      if (!session || !sessionId) return

      const existingUpload = uploadStore.activeUploads.get(sessionId)
      if (existingUpload?.status !== 'FAILED' && existingUpload?.status !== 'CANCELLED') {
        uploadStore.updateUploadProgress(sessionId, {
          status: 'PROCESSING',
          progressPercent: 100
        })
      }

      connectUploadEventSource(sessionId)
      scheduleUploadSessionPoll(sessionId, UPLOAD_PROCESSING_POLL_MS)
      clearFiles()
    },
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
      eventSource: null,
      eventsConnected: false,
      pollTimer: null,
      pollInFlight: false,
      pollCount: 0
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

  function closeUploadEventSource(sessionId: string) {
    const runtime = uploadSessionRuntimes.value.get(sessionId)
    if (!runtime) return
    if (runtime.eventSource) {
      runtime.eventSource.close()
      runtime.eventSource = null
    }
    runtime.eventsConnected = false
  }

  function stopMonitoringUploadSession(sessionId: string, options: { removeRuntime?: boolean } = {}) {
    clearUploadSessionPollTimer(sessionId)
    closeUploadEventSource(sessionId)
    const runtime = uploadSessionRuntimes.value.get(sessionId)
    if (runtime) {
      runtime.pollInFlight = false
      runtime.pollCount = 0
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

  async function refreshPagesData(refreshOptions: RefreshPagesOptions = {}) {
    const manual = refreshOptions.manual === true

    if (manual) {
      isManualPagesRefresh.value = true
    }

    try {
      await options.refreshPagesFetch()
    } finally {
      if (manual) {
        isManualPagesRefresh.value = false
      }
    }
  }

  async function syncProjectDataAfterUploadTerminal() {
    const workspaceId = options.workspaceId.value
    await Promise.allSettled([
      refreshPagesData(),
      options.refreshProject(),
      options.refreshProjectStatus(),
      workspaceId ? refreshNuxtData(wsKey(workspaceId, 'storage', 'quota')) : Promise.resolve()
    ])

    if (hasIndexingPages(options.pages.value)) {
      options.onIndexingPagesDetected?.()
    }
  }

  function scheduleUploadPageRefresh() {
    if (pageRefreshDebounceTimer) return
    pageRefreshDebounceTimer = setTimeout(() => {
      pageRefreshDebounceTimer = null
      void Promise.allSettled([
        refreshPagesData(),
        options.refreshProject(),
        options.refreshProjectStatus()
      ])
    }, 800)
  }

  function upsertUploadStoreSession(detail: UploadSessionDetailResponse) {
    const mappedFiles = (detail.files ?? []).map(mapServerFileToRecoveredUiFile)
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

    uploadStore.updateUploadProgress(detail.id, {
      status: detail.status,
      totalFiles: detail.totalFiles,
      processedFiles: detail.processedFiles,
      failedFiles: detail.failedFiles,
      progressPercent: getSessionProgressPercent(
        detail.status,
        detail.processedFiles,
        detail.failedFiles,
        detail.totalFiles,
        detail.progressPercent
      ),
      files: mappedFiles
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
    await syncProjectDataAfterUploadTerminal()

    if (status === 'FAILED') {
      showApiErrorToast({
        title: 'Processing failed',
        error: new Error('Some files could not be processed'),
        fallback: 'Some files could not be processed'
      })
    }
  }

  function applyUploadSessionSseEvent(event: UploadSessionSseEvent) {
    if (!event?.sessionId) return

    const shouldUpdateProgressFromServer
      = event.status === 'PROCESSING'
        || event.status === 'COMPLETED'
        || event.status === 'FAILED'
        || event.status === 'CANCELLED'

    uploadStore.updateUploadProgress(event.sessionId, {
      ...(event.status ? { status: event.status } : {}),
      ...(typeof event.processedFiles === 'number' ? { processedFiles: event.processedFiles } : {}),
      ...(typeof event.failedFiles === 'number' ? { failedFiles: event.failedFiles } : {}),
      ...(shouldUpdateProgressFromServer
        && typeof event.totalFiles === 'number'
        && typeof event.processedFiles === 'number'
        && typeof event.failedFiles === 'number'
        ? {
            progressPercent: getSessionProgressPercent(
              event.status,
              event.processedFiles,
              event.failedFiles,
              event.totalFiles
            )
          }
        : {})
    })

    if (event.status && isTerminalUploadStatus(event.status)) {
      void handleTerminalUploadStatus(event.sessionId, event.status)
    }
  }

  function applyUploadFileSseEvent(event: UploadFileSseEvent) {
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

  function scheduleUploadSessionPoll(sessionId: string, delayMs = UPLOAD_PROCESSING_POLL_MS, runtimeOptions: { force?: boolean } = {}) {
    if (import.meta.server) return
    if (!sessionId) return
    const runtime = ensureUploadSessionRuntime(sessionId)
    const force = runtimeOptions.force === true
    if (!force && runtime.eventSource && runtime.eventsConnected) return
    if (runtime.pollTimer || runtime.pollInFlight) return
    runtime.pollTimer = setTimeout(() => {
      runtime.pollTimer = null
      void pollBackendProcessing(sessionId)
    }, delayMs)
  }

  function connectUploadEventSource(sessionId: string) {
    if (import.meta.server) return
    const workspaceId = options.workspaceId.value
    if (!sessionId || !workspaceId) return
    const runtime = ensureUploadSessionRuntime(sessionId)
    if (runtime.eventSource) return

    const es = new EventSource(`/api/workspaces/${workspaceId}/projects/${options.projectId}/upload-sessions/${sessionId}/events`)
    runtime.eventSource = es

    es.addEventListener('upload-session-state', (raw) => {
      runtime.eventsConnected = true
      runtime.pollCount = 0
      clearUploadSessionPollTimer(sessionId)
      try {
        applyUploadSessionSseEvent(JSON.parse((raw as MessageEvent).data))
      } catch {
        // Ignore malformed payloads.
      }
    })

    es.addEventListener('upload-file-state', (raw) => {
      try {
        applyUploadFileSseEvent(JSON.parse((raw as MessageEvent).data))
      } catch {
        // Ignore malformed payloads.
      }
    })

    es.addEventListener('page-created-or-updated', (raw) => {
      try {
        const payload = JSON.parse((raw as MessageEvent).data) as PageCreatedOrUpdatedSseEvent
        if (payload.projectId === options.projectId) {
          scheduleUploadPageRefresh()
        }
      } catch {
        // Ignore malformed payloads.
      }
    })

    es.addEventListener('page-index-state', () => {
      options.onIndexingPagesDetected?.()
    })

    es.onerror = () => {
      closeUploadEventSource(sessionId)
      const upload = uploadStore.activeUploads.get(sessionId)
      if (upload && isActiveUploadStatus(upload.status)) {
        scheduleUploadSessionPoll(sessionId, 0, { force: true })
      }
    }
  }

  async function pollBackendProcessing(sessionId: string) {
    const workspaceId = options.workspaceId.value
    if (!workspaceId) return

    const runtime = ensureUploadSessionRuntime(sessionId)
    if (runtime.pollInFlight) return
    runtime.pollInFlight = true

    try {
      const status = await $fetch<{
        status: UploadSessionStatus
        processedFiles: number
        failedFiles: number
        totalFiles: number
      }>(
        `/api/workspaces/${workspaceId}/projects/${options.projectId}/upload-sessions/${sessionId}`
      )

      uploadStore.updateUploadProgress(sessionId, {
        status: status.status,
        processedFiles: status.processedFiles,
        failedFiles: status.failedFiles,
        progressPercent: getSessionProgressPercent(
          status.status,
          status.processedFiles,
          status.failedFiles,
          status.totalFiles
        )
      })

      if (isTerminalUploadStatus(status.status)) {
        await handleTerminalUploadStatus(sessionId, status.status)
        return
      }

      if (runtime.eventSource && runtime.eventsConnected) {
        return
      }

      runtime.pollCount += 1
      if (runtime.pollCount % 5 === 0) {
        await refreshPagesData()
      }

      if (runtime.pollCount >= UPLOAD_PROCESSING_MAX_POLLS) {
        uploadStore.completeUpload(sessionId, 'FAILED', 'Processing timeout')
        stopMonitoringUploadSession(sessionId)
        return
      }

      scheduleUploadSessionPoll(sessionId, UPLOAD_PROCESSING_POLL_MS)
    } catch {
      const upload = uploadStore.activeUploads.get(sessionId)
      if (uploadSessionActions.isCancellationInProgress(sessionId) || upload?.status === 'CANCELLED') {
        stopMonitoringUploadSession(sessionId)
        return
      }

      if (runtime.eventSource && runtime.eventsConnected) {
        return
      }

      runtime.pollCount += 1
      if (runtime.pollCount >= UPLOAD_PROCESSING_MAX_POLLS) {
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

          upsertUploadStoreSession(detail)

          if (isActiveUploadStatus(detail.status)) {
            connectUploadEventSource(detail.id)
            scheduleUploadSessionPoll(detail.id, UPLOAD_PROCESSING_POLL_MS)
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

  async function startProjectUpload(selectedFiles: File[], baseNameOverrides?: Record<string, string>) {
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
    connectUploadEventSource(newSessionId)
    scheduleUploadSessionPoll(newSessionId, UPLOAD_PROCESSING_POLL_MS)
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

  onMounted(() => {
    void restoreProjectUploadSessions()
  })

  onBeforeUnmount(() => {
    stopAllUploadSessionMonitoring()
    if (pageRefreshDebounceTimer) {
      clearTimeout(pageRefreshDebounceTimer)
      pageRefreshDebounceTimer = null
    }
  })

  return {
    isUploading,
    isManualPagesRefresh,
    showPagesLoadingSpinner,
    refreshPagesData,
    startProjectUpload
  }
}
