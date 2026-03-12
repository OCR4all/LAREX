<script setup lang="ts">
import {
  LazyCodecSlideoverAction,
  LazyPageSlideoverEdit,
  LazyPageModalImages,
  LazyProjectModalConflictResolution,
  LazyProjectSlideoverPdfPrefix,
  LazyProjectSlideoverEdit,
  LazyProjectSlideoverExportTarget,
  LazyProjectSlideoverBulkDeletePages,
  LazyProjectSlideoverXmlEditor,
  LazyUiConfirmSlideover,
  LazyUiDeleteSlideover,
  LazyEditorVersionHistorySlideover,
  LazyShareSlideover } from '#components'
import type { DropdownMenuItem, BreadcrumbItem } from '@nuxt/ui'
import type { Subtask } from '~/types/index'
import { wsKey } from '@/utils/fetch-keys'
import { createSkeletonPageData, type PageResponse } from '@/services/editor/project-loader'
import { createPageXmlLabelSet } from '@/models/editor'
import type { LabelSet as ApiLabelSet } from '@/types/label-set'
import { useEditorStore } from '@/stores/editor/editor.store'
import type { ActiveUpload } from '@/stores/upload.store'
import { useChunkedUpload } from '@/composables/use-chunked-upload'
import { usePagePrefetch } from '@/composables/use-page-prefetch'
import type { Ref } from 'vue'
import type { CodecProjectScope, GenerateCodecFromSourcesResponse, ValidateCodecAgainstSourcesResponse } from '@/types/codec'
import UiColorTag from '@/components/ui/color-tag.vue'

const UBadge = resolveComponent('UBadge')
const UButton = resolveComponent('UButton')
const UDropdownMenu = resolveComponent('UDropdownMenu')
const NuxtTime = resolveComponent('NuxtTime')
const UPopover = resolveComponent('UPopover')

const route = useRoute()
const toast = useToast()
const workspace = useWorkspaceStore()
const editorStore = useEditorStore()
const uploadStore = useUploadStore()
const pagePrefetch = usePagePrefetch()
if (!workspace.hasFetched) {
  await workspace.fetchWorkspaces()
}
const selectedWorkspace = computed(() => workspace.selectedWorkspaceId)
const { allow } = useActionVisibility()

const projectId = route.params.id as string

const projectKey = computed(() => wsKey(selectedWorkspace.value as string, 'projects', projectId))
const projectPagesKey = computed(() => wsKey(selectedWorkspace.value as string, 'projects', projectId, 'pages'))
const projectStatusKey = computed(() => wsKey(selectedWorkspace.value as string, 'projects', projectId, 'status'))
const starredProjectsKey = computed(() => wsKey(selectedWorkspace.value as string, 'projects', 'starred'))
const libraryProjectsKey = computed(() => wsKey(selectedWorkspace.value as string, 'projects', 'list'))

type ProjectData = {
  id: string
  name: string
  description: string
  tags: string[]
  created: string
  updated: string
  pageCount: number
  isStarred: boolean
  storageUsedBytes: number
  storageUsedFormatted: string
  locked: boolean
  lockedReason: string | null
  codecId?: string | null
  labelSetId?: string | null
  capabilities?: {
    canEdit: boolean
    canShare: boolean
    canDelete: boolean
    canDeletePages: boolean
    canUpload: boolean
    canExportPackage: boolean
  }
}

type ResolvedTag = {
  id: string
  label: string
  color: string | null
}

const DEFAULT_CUSTOM_TAG_COLOR = '#2563eb'

type PageIndexingStatus = 'NOT_APPLICABLE' | 'UNINDEXED' | 'INDEXING' | 'INDEXED'

type Page = {
  id: string
  name: string
  description: string
  tags: string[]
  resolvedTags: ResolvedTag[] | null
  created: string
  updated: string
  xmlFileCount: number
  imageCount: number
  thumbnailUrl?: string | null
  indexingStatus?: PageIndexingStatus
}

const { data: project, error: projectError, pending: projectPending, refresh: refreshProject } = await useFetch<ProjectData>(() => `/api/workspaces/${selectedWorkspace.value}/projects/${projectId}`, {
  key: projectKey,
  watch: [selectedWorkspace]
})

function getErrorStatusCode(error: unknown): number | null {
  const candidate = error as {
    statusCode?: number | string
    status?: number | string
    data?: { statusCode?: number | string, status?: number | string }
    response?: { status?: number | string, _data?: { statusCode?: number | string, status?: number | string } }
  } | null

  const values = [
    candidate?.statusCode,
    candidate?.status,
    candidate?.data?.statusCode,
    candidate?.data?.status,
    candidate?.response?.status,
    candidate?.response?._data?.statusCode,
    candidate?.response?._data?.status
  ]

  for (const value of values) {
    if (value == null) continue
    const parsed = Number(value)
    if (Number.isFinite(parsed) && parsed > 0) {
      return parsed
    }
  }

  return null
}

function getErrorMessage(error: unknown, fallback: string): string {
  const candidate = error as {
    message?: unknown
    data?: { message?: unknown }
    response?: { _data?: { message?: unknown } }
  } | null

  const values = [
    candidate?.data?.message,
    candidate?.response?._data?.message,
    candidate?.message
  ]

  for (const value of values) {
    if (typeof value === 'string' && value.trim().length > 0) {
      return value
    }
  }

  return fallback
}

const projectErrorStatusCode = computed(() => getErrorStatusCode(projectError.value))
const isProjectNotFound = computed(() => projectErrorStatusCode.value === 404)
const projectLoadErrorMessage = computed(() => getErrorMessage(projectError.value, 'Failed to load project.'))
const projectCapabilities = useResourceCapabilities(project, 'project')

const { data: pages, error: pagesError, pending: pagesPending, refresh: refreshPagesFetch } = await useFetch<Page[]>(() => `/api/projects/${projectId}/pages`, {
  key: projectPagesKey
})

const isManualPagesRefresh = ref(false)
const hasLoadedPagesOnce = ref(false)
const showPagesLoadingSpinner = computed(() => isManualPagesRefresh.value || (!hasLoadedPagesOnce.value && pagesPending.value))

watch([pages, pagesError], ([nextPages, nextError]) => {
  if (nextPages !== null || nextError) {
    hasLoadedPagesOnce.value = true
  }
}, { immediate: true })

const pageIndexStatusPollTimeout = ref<ReturnType<typeof setTimeout> | null>(null)
const pageIndexStatusPollInFlight = ref(false)
const canPollPageIndexStatuses = ref(false)
const PAGE_INDEX_STATUS_POLL_MS = 3000

function hasIndexingPages(list: Page[] | null | undefined): boolean {
  return (list ?? []).some(page => page.indexingStatus === 'INDEXING')
}

function stopPageIndexStatusPolling() {
  if (pageIndexStatusPollTimeout.value) {
    clearTimeout(pageIndexStatusPollTimeout.value)
    pageIndexStatusPollTimeout.value = null
  }
}

function schedulePageIndexStatusPoll(delayMs = PAGE_INDEX_STATUS_POLL_MS) {
  if (import.meta.server || !canPollPageIndexStatuses.value) return
  if (pageIndexStatusPollTimeout.value || pageIndexStatusPollInFlight.value) return
  pageIndexStatusPollTimeout.value = setTimeout(() => {
    pageIndexStatusPollTimeout.value = null
    void pollPageIndexStatuses()
  }, delayMs)
}

async function pollPageIndexStatuses() {
  if (import.meta.server || !canPollPageIndexStatuses.value) return
  if (pageIndexStatusPollInFlight.value) return
  if (!hasIndexingPages(pages.value)) {
    stopPageIndexStatusPolling()
    return
  }

  pageIndexStatusPollInFlight.value = true
  let shouldContinuePolling = true
  try {
    const statuses = await $fetch<Record<string, PageIndexingStatus>>(`/api/projects/${projectId}/pages/index-statuses`)
    if (pages.value) {
      pages.value = pages.value.map((page) => {
        const nextStatus = statuses[page.id]
        if (!nextStatus || nextStatus === page.indexingStatus) return page
        return { ...page, indexingStatus: nextStatus }
      })
    }
  } catch (error) {
    const statusCode = Number(
      (error as { statusCode?: number, response?: { status?: number } })?.statusCode
      ?? (error as { response?: { status?: number } })?.response?.status
      ?? 0
    )
    if (statusCode === 401 || statusCode === 403) {
      shouldContinuePolling = false
      return
    }
  } finally {
    pageIndexStatusPollInFlight.value = false
    if (shouldContinuePolling && hasIndexingPages(pages.value)) {
      schedulePageIndexStatusPoll()
    } else {
      stopPageIndexStatusPolling()
    }
  }
}

const subtaskSummaryKey = computed(() => wsKey(selectedWorkspace.value as string, 'projects', projectId, 'subtask-summary'))
const { data: subtaskSummary, refresh: _refreshSubtaskSummary } = await useFetch<Record<string, number>>(
  () => `/api/projects/${projectId}/pages/subtask-summary`,
  {
    key: subtaskSummaryKey,
    default: () => ({})
  }
)

const openSubtasksKey = computed(() => wsKey(selectedWorkspace.value as string, 'projects', projectId, 'open-subtasks'))
const { data: openSubtasksByPage } = await useFetch<Record<string, Subtask[]>>(
  () => `/api/projects/${projectId}/pages/subtasks/open`,
  {
    key: openSubtasksKey,
    default: () => ({})
  }
)

const items = computed<BreadcrumbItem[]>(() => [
  {
    label: 'Library',
    to: '/'
  },
  {
    label: project.value?.name || 'Project'
  }
])

const { data: projectStatus, refresh: refreshProjectStatus } = await useFetch<{ hasUnresolvedConflicts: boolean, isBlocked: boolean }>(() => `/api/workspaces/${selectedWorkspace.value}/projects/${projectId}/status`, {
  key: projectStatusKey,
  watch: [selectedWorkspace]
})

const isStarring = ref(false)

async function toggleStar() {
  if (isStarring.value || !project.value) return

  try {
    isStarring.value = true
    const response = await $fetch<{ starred: boolean, message: string }>(`/api/projects/${projectId}/star/toggle`, {
      method: 'PUT'
    })

    project.value.isStarred = response.starred

    await refreshNuxtData(starredProjectsKey.value)

    toast.add({
      title: response.message,
      color: response.starred ? 'success' : 'info',
      icon: response.starred ? 'i-lucide-star' : 'i-lucide-star-off'
    })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : ''
    toast.add({
      title: 'Error toggling star',
      description: message || 'Failed to update star status',
      color: 'error',
      icon: 'i-lucide-alert-circle'
    })
  } finally {
    isStarring.value = false
  }
}

const fileInput = ref<HTMLInputElement>()

const currentUploadSessionId = ref<string | null>(null)
const tempUploadSessionId = ref<string | null>(null)
let pageRefreshDebounceTimer: ReturnType<typeof setTimeout> | null = null

type UploadSessionStatus = 'PENDING' | 'UPLOADING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'

type UploadSessionSseEvent = {
  sessionId: string
  status?: UploadSessionStatus
  processedFiles?: number
  failedFiles?: number
  totalFiles?: number
  processedBytes?: number
  totalBytes?: number
  message?: string
  uploadingActive?: boolean
  processingActive?: boolean
  finalized?: boolean
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

type UploadFileUiStatus = 'pending' | 'uploading' | 'uploaded' | 'processing' | 'completed' | 'failed' | 'conflict' | 'skipped'

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
  pageId: string
  pageName?: string
  reason?: string
}

type UploadSessionRuntime = {
  eventSource: EventSource | null
  eventsConnected: boolean
  pollTimer: ReturnType<typeof setTimeout> | null
  pollInFlight: boolean
  pollCount: number
}

const uploadSessionRuntimes = ref<Map<string, UploadSessionRuntime>>(new Map())
const UPLOAD_PROCESSING_POLL_MS = 2000
const UPLOAD_PROCESSING_MAX_POLLS = 300

function isActiveUploadStatus(status?: UploadSessionStatus): boolean {
  return status === 'PENDING' || status === 'UPLOADING' || status === 'PROCESSING'
}

function isTerminalUploadStatus(status?: UploadSessionStatus): status is Extract<UploadSessionStatus, 'COMPLETED' | 'FAILED' | 'CANCELLED'> {
  return status === 'COMPLETED' || status === 'FAILED' || status === 'CANCELLED'
}

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

async function invalidateProjectPagesCache() {
  try {
    await $fetch(`/api/projects/${projectId}/pages/invalidate-cache`, { method: 'POST' })
  } catch {
    // Best-effort cache busting; continue with refresh even if unavailable.
  }
}

async function refreshPagesData(options: { manual?: boolean, invalidateCache?: boolean } = {}) {
  const manual = options.manual === true
  const invalidateCache = options.invalidateCache === true

  if (manual) {
    isManualPagesRefresh.value = true
  }

  try {
    if (invalidateCache) {
      await invalidateProjectPagesCache()
    }

    if (manual) {
      await refreshPagesFetch()
      return
    }

    const refreshedPages = await $fetch<Page[]>(`/api/projects/${projectId}/pages`)
    pages.value = refreshedPages
  } finally {
    if (manual) {
      isManualPagesRefresh.value = false
    }
  }
}

async function syncProjectDataAfterUploadTerminal() {
  await Promise.allSettled([
    refreshPagesData({ invalidateCache: true }),
    refreshProject(),
    refreshProjectStatus(),
    refreshNuxtData(wsKey(selectedWorkspace.value as string, 'storage', 'quota'))
  ])

  if (hasIndexingPages(pages.value)) {
    schedulePageIndexStatusPoll(0)
  }
}

function scheduleUploadPageRefresh() {
  if (pageRefreshDebounceTimer) return
  pageRefreshDebounceTimer = setTimeout(() => {
    pageRefreshDebounceTimer = null
    void Promise.allSettled([
      refreshPagesData({ invalidateCache: true }),
      refreshProject(),
      refreshProjectStatus()
    ])
  }, 800)
}

function scheduleUploadSessionPoll(sessionId: string, delayMs = UPLOAD_PROCESSING_POLL_MS, options: { force?: boolean } = {}) {
  if (import.meta.server) return
  if (!sessionId) return
  const runtime = ensureUploadSessionRuntime(sessionId)
  const force = options.force === true
  if (!force && runtime.eventSource && runtime.eventsConnected) return
  if (runtime.pollTimer || runtime.pollInFlight) return
  runtime.pollTimer = setTimeout(() => {
    runtime.pollTimer = null
    void pollBackendProcessing(sessionId)
  }, delayMs)
}

function toUploadFileUiStatus(status?: string | null): UploadFileUiStatus {
  const normalized = (status ?? '').toLowerCase()
  if (normalized === 'pending' || normalized === 'uploading' || normalized === 'uploaded' || normalized === 'processing' || normalized === 'completed' || normalized === 'failed' || normalized === 'conflict' || normalized === 'skipped') {
    return normalized
  }
  return 'pending'
}

function mapServerFileToUploadFile(file: UploadSessionFileResponse) {
  const totalChunks = Math.max(1, Number(file.chunkCount || 1))
  const chunksReceived = Math.max(0, Number(file.chunksReceived || 0))
  const mimeType = file.mimeType || 'application/octet-stream'
  const fileName = file.originalFileName || 'upload-file'
  return {
    id: file.id,
    file: new File([], fileName, { type: mimeType }),
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

function upsertUploadStoreSession(detail: UploadSessionDetailResponse) {
  const mappedFiles = (detail.files ?? []).map(file => mapServerFileToUploadFile(file))
  const existingUpload = uploadStore.activeUploads.get(detail.id)
  if (!existingUpload) {
    uploadStore.registerUpload(
      detail.id,
      detail.projectId,
      project.value?.name || 'Project Upload',
      detail.workspaceId || (selectedWorkspace.value as string),
      mappedFiles
    )
  }

  uploadStore.updateUploadProgress(detail.id, {
    status: detail.status,
    totalFiles: detail.totalFiles,
    processedFiles: detail.processedFiles,
    failedFiles: detail.failedFiles,
    progressPercent: detail.totalFiles > 0
      ? Math.round(((detail.processedFiles + detail.failedFiles) / detail.totalFiles) * 100)
      : Math.max(detail.progressPercent, 100),
    files: mappedFiles
  })
}

async function handleTerminalUploadStatus(
  sessionId: string,
  status: Extract<UploadSessionStatus, 'COMPLETED' | 'FAILED' | 'CANCELLED'>
) {
  if (status === 'FAILED') {
    uploadStore.completeUpload(sessionId, 'FAILED', 'Processing failed')
  } else if (status === 'CANCELLED') {
    uploadStore.completeUpload(sessionId, 'CANCELLED')
  } else {
    uploadStore.completeUpload(sessionId, 'COMPLETED')
  }

  if (currentUploadSessionId.value === sessionId) {
    currentUploadSessionId.value = null
  }

  stopMonitoringUploadSession(sessionId)
  await syncProjectDataAfterUploadTerminal()

  if (status === 'FAILED') {
    toast.add({
      title: 'Processing failed',
      description: 'Some files could not be processed',
      color: 'error',
      icon: 'i-lucide-alert-circle'
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
          progressPercent: event.totalFiles > 0
            ? Math.round(((event.processedFiles + event.failedFiles) / event.totalFiles) * 100)
            : 100
        }
      : {})
  })

  if (event.status && isTerminalUploadStatus(event.status)) {
    void handleTerminalUploadStatus(event.sessionId, event.status)
  }
}

function applyUploadFileSseEvent(event: UploadFileSseEvent) {
  if (!event?.sessionId || !event?.fileId) return
  const status = event.status ? event.status.toLowerCase() as UploadFileUiStatus : undefined
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

function connectUploadEventSource(sessionId: string) {
  if (import.meta.server) return
  if (!sessionId || !selectedWorkspace.value) return
  const runtime = ensureUploadSessionRuntime(sessionId)
  if (runtime.eventSource) return

  const es = new EventSource(`/api/workspaces/${selectedWorkspace.value}/projects/${projectId}/upload-sessions/${sessionId}/events`)
  runtime.eventSource = es

  es.addEventListener('upload-session-state', (raw) => {
    runtime.eventsConnected = true
    runtime.pollCount = 0
    clearUploadSessionPollTimer(sessionId)
    try {
      applyUploadSessionSseEvent(JSON.parse((raw as MessageEvent).data))
    } catch {
      // Ignore malformed SSE payloads.
    }
  })

  es.addEventListener('upload-file-state', (raw) => {
    try {
      applyUploadFileSseEvent(JSON.parse((raw as MessageEvent).data))
    } catch {
      // Ignore malformed SSE payloads.
    }
  })

  es.addEventListener('page-created-or-updated', (raw) => {
    try {
      const payload = JSON.parse((raw as MessageEvent).data) as PageCreatedOrUpdatedSseEvent
      if (payload.projectId === projectId) {
        scheduleUploadPageRefresh()
      }
    } catch {
      // Ignore malformed SSE payloads.
    }
  })

  es.addEventListener('page-index-state', () => {
    schedulePageIndexStatusPoll(0)
  })

  es.onerror = () => {
    closeUploadEventSource(sessionId)
    const upload = uploadStore.activeUploads.get(sessionId)
    if (upload && isActiveUploadStatus(upload.status)) {
      scheduleUploadSessionPoll(sessionId, 0, { force: true })
    }
  }
}

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
  workspaceId: selectedWorkspace as Ref<string | undefined>,
  projectId: computed(() => projectId) as Ref<string | undefined>,
  onProgress: (session) => {
    const sessionId = currentUploadSessionId.value
    if (session && sessionId) {
      const existingUpload = uploadStore.activeUploads.get(sessionId)
      if (existingUpload && (existingUpload.status === 'PROCESSING' || existingUpload.status === 'COMPLETED' || existingUpload.status === 'FAILED' || existingUpload.status === 'CANCELLED')) {
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
    }
  },
  onFileComplete: (file) => {
    const sessionId = currentUploadSessionId.value
    if (sessionId && file.id) {
      uploadStore.updateFileProgress(sessionId, file.id, {
        status: file.status,
        progress: file.progress,
        chunksReceived: file.chunksReceived
      })
    }
  },
  onSessionComplete: async (session) => {
    const sessionId = currentUploadSessionId.value
    if (session && sessionId) {
      const existingUpload = uploadStore.activeUploads.get(sessionId)
      if (existingUpload?.status !== 'FAILED' && existingUpload?.status !== 'CANCELLED') {
        uploadStore.updateUploadProgress(sessionId, {
          status: 'PROCESSING',
          processedFiles: existingUpload?.totalFiles ?? session.totalFiles,
          progressPercent: 100
        })
      }

      connectUploadEventSource(sessionId)
      scheduleUploadSessionPoll(sessionId, UPLOAD_PROCESSING_POLL_MS)
    }

    clearFiles()
  },
  onError: (error, file) => {
    const sessionId = currentUploadSessionId.value
    const existingUpload = sessionId ? uploadStore.activeUploads.get(sessionId) : null
    const message = error.message.toLowerCase()
    const cancelLikeError = message.includes('aborted') || message.includes('abort') || message.includes('session is not active')
    if (cancelLikeError) {
      return
    }

    if (sessionId && (uploadStore.isCancelling(sessionId) || existingUpload?.status === 'CANCELLED')) {
      return
    }

    if (sessionId) {
      uploadStore.completeUpload(sessionId, 'FAILED', error.message)
    }

    toast.add({
      title: 'Upload failed',
      description: file ? `Failed to upload ${file.fileName}: ${error.message}` : error.message,
      color: 'error',
      icon: 'i-lucide-alert-circle'
    })
  }
})

watch(() => uploadSession.value?.id, (newSessionId) => {
  if (newSessionId && tempUploadSessionId.value && tempUploadSessionId.value !== newSessionId) {
    const tempId = tempUploadSessionId.value
    const existingUpload = uploadStore.activeUploads.get(tempId)
    if (existingUpload) {
      uploadStore.removeUpload(tempId)
      uploadStore.registerUpload(
        newSessionId,
        existingUpload.projectId,
        existingUpload.projectName,
        existingUpload.workspaceId,
        existingUpload.files
      )
    }
    currentUploadSessionId.value = newSessionId
    tempUploadSessionId.value = null
    connectUploadEventSource(newSessionId)
    scheduleUploadSessionPoll(newSessionId, UPLOAD_PROCESSING_POLL_MS)
  }
})

async function handleProjectUploadCancellation(sessionId: string, upload: ActiveUpload): Promise<boolean> {
  if (upload.projectId !== projectId) {
    return false
  }

  if (uploadSession.value?.id === sessionId || currentUploadSessionId.value === sessionId) {
    void cancelUpload().catch(() => {})
    clearFiles()
  } else {
    await $fetch(`/api/workspaces/${upload.workspaceId}/projects/${upload.projectId}/upload-sessions/${sessionId}`, {
      method: 'DELETE'
    })
  }

  stopMonitoringUploadSession(sessionId)
  if (currentUploadSessionId.value === sessionId) {
    currentUploadSessionId.value = null
    tempUploadSessionId.value = null
  }
  void syncProjectDataAfterUploadTerminal()
  return true
}

async function restoreProjectUploadSessions() {
  if (import.meta.server || !selectedWorkspace.value) return

  try {
    const sessions = await $fetch<UploadSessionSummaryResponse[]>('/api/upload-sessions')
    const relevantSessions = sessions.filter(session =>
      session.projectId === projectId
      && isActiveUploadStatus(session.status)
    )

    await Promise.all(relevantSessions.map(async (sessionSummary) => {
      try {
        const detail = await $fetch<UploadSessionDetailResponse>(
          `/api/workspaces/${selectedWorkspace.value}/projects/${projectId}/upload-sessions/${sessionSummary.id}`
        )

        upsertUploadStoreSession(detail)

        if (isActiveUploadStatus(detail.status)) {
          connectUploadEventSource(detail.id)
          scheduleUploadSessionPoll(detail.id, UPLOAD_PROCESSING_POLL_MS)
        } else if (isTerminalUploadStatus(detail.status)) {
          await handleTerminalUploadStatus(detail.id, detail.status)
        }
      } catch {
        // Ignore sessions that disappear during recovery.
      }
    }))
  } catch {
    // Recovery is best-effort.
  }
}

watch(() => hasIndexingPages(pages.value), (hasIndexing) => {
  if (import.meta.server || !canPollPageIndexStatuses.value) return
  if (hasIndexing) {
    schedulePageIndexStatusPoll(0)
  } else {
    stopPageIndexStatusPolling()
  }
}, { immediate: true })

onMounted(() => {
  canPollPageIndexStatuses.value = true
  uploadStore.setCancelUploadHandler(handleProjectUploadCancellation)

  if (hasIndexingPages(pages.value)) {
    schedulePageIndexStatusPoll(0)
  }

  void restoreProjectUploadSessions()
})

onBeforeUnmount(() => {
  canPollPageIndexStatuses.value = false
  stopPageIndexStatusPolling()
  uploadStore.clearCancelUploadHandler(handleProjectUploadCancellation)
  stopAllUploadSessionMonitoring()
  if (pageRefreshDebounceTimer) {
    clearTimeout(pageRefreshDebounceTimer)
    pageRefreshDebounceTimer = null
  }
})

async function handleFileUpload(files: FileList | null) {
  if (!files || files.length === 0) {
    toast.add({
      title: 'No files selected',
      description: 'Please select files to upload (images, XML files, and/or PDFs)',
      color: 'warning',
      icon: 'i-lucide-alert-triangle'
    })
    return
  }

  const fileArray = Array.from(files)
  const pdfFiles = fileArray.filter(f => (f.type === 'application/pdf') || f.name.toLowerCase().endsWith('.pdf'))
  let pdfPrefixesByFileName: Record<string, string> | null = null

  if (pdfFiles.length > 0) {
    const infos = pdfFiles.map((f) => {
      const dotIndex = f.name.indexOf('.')
      const defaultPrefix = dotIndex >= 0 ? f.name.substring(0, dotIndex) : f.name
      return { fileName: f.name, defaultPrefix }
    })

    const instance = pdfPrefixSlideover.open({ files: infos })
    pdfPrefixesByFileName = await instance.result

    if (!pdfPrefixesByFileName) {
      if (fileInput.value) fileInput.value.value = ''
      return
    }
  }

  const uploadFilesList = addFiles(fileArray)
  if (pdfPrefixesByFileName) {
    for (const f of uploadFilesList) {
      const prefix = pdfPrefixesByFileName[f.fileName]
      if (prefix) f.baseName = prefix
    }
  }

  const tempSessionId = 'pending-' + Date.now()
  tempUploadSessionId.value = tempSessionId
  currentUploadSessionId.value = tempSessionId

  uploadStore.registerUpload(
    tempSessionId,
    projectId,
    project.value?.name || 'Unknown Project',
    selectedWorkspace.value as string,
    uploadFilesList
  )

  try {
    await startUpload()
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : ''
    if (currentUploadSessionId.value) {
      uploadStore.removeUpload(currentUploadSessionId.value)
      currentUploadSessionId.value = null
    }

    toast.add({
      title: 'Upload failed',
      description: message || 'Failed to start upload',
      color: 'error',
      icon: 'i-lucide-alert-circle'
    })
  }

  if (fileInput.value) {
    fileInput.value.value = ''
  }
}

async function pollBackendProcessing(sessionId: string) {
  if (!selectedWorkspace.value) return

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
      `/api/workspaces/${selectedWorkspace.value}/projects/${projectId}/upload-sessions/${sessionId}`
    )

    uploadStore.updateUploadProgress(sessionId, {
      status: status.status,
      processedFiles: status.processedFiles,
      failedFiles: status.failedFiles,
      progressPercent: status.totalFiles > 0
        ? Math.round(((status.processedFiles + status.failedFiles) / status.totalFiles) * 100)
        : 100
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
      await refreshPagesData({ invalidateCache: true })
    }

    if (runtime.pollCount >= UPLOAD_PROCESSING_MAX_POLLS) {
      uploadStore.completeUpload(sessionId, 'FAILED', 'Processing timeout')
      stopMonitoringUploadSession(sessionId)
      return
    }

    scheduleUploadSessionPoll(sessionId, UPLOAD_PROCESSING_POLL_MS)
  } catch {
    const upload = uploadStore.activeUploads.get(sessionId)
    if (uploadStore.isCancelling(sessionId) || upload?.status === 'CANCELLED') {
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

async function viewConflicts() {
  try {
    const conflicts = await $fetch<Array<Record<string, unknown>>>(`/api/workspaces/${selectedWorkspace.value}/projects/${projectId}/conflicts`)

    if (conflicts.length === 0) {
      toast.add({
        title: 'No conflicts found',
        description: 'All upload conflicts have been resolved',
        color: 'info',
        icon: 'i-lucide-info'
      })
      return
    }

    conflictResolutionModal.open({
      projectId,
      conflicts: conflicts as any,
      uploadResult: {
        totalPagesCreated: 0,
        totalPagesUpdated: 0,
        totalImagesProcessed: 0,
        totalXmlFilesProcessed: 0,
        pages: []
      }
    })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : ''
    toast.add({
      title: 'Failed to load conflicts',
      description: message || 'An error occurred',
      color: 'error',
      icon: 'i-lucide-alert-circle'
    })
  }
}

const isLoadingEditor = ref(false)

async function loadProjectLabelSet() {
  const fallback = () => editorStore.setLabelSet(createPageXmlLabelSet())
  const labelSetId = project.value?.labelSetId
  if (!labelSetId) {
    fallback()
    return
  }
  try {
    const labelSet = await $fetch<ApiLabelSet>(`/api/workspaces/${selectedWorkspace.value}/label-sets/${labelSetId}`)
    editorStore.setLabelSetFromApi(labelSet)
  } catch {
    fallback()
  }
}

/**
 * Prefetch data when user hovers over the "Open in Editor" button.
 * This warms the cache so the editor opens faster.
 */
let prefetchTimeout: ReturnType<typeof setTimeout> | null = null
function handleEditorButtonHover() {
  if (prefetchTimeout) clearTimeout(prefetchTimeout)

  prefetchTimeout = setTimeout(() => {
    if (!pages.value || pages.value.length === 0) return

    const pageIdsToLoad = selectedPageIds.value.size > 0
      ? Array.from(selectedPageIds.value)
      : pages.value.slice(0, 5).map(p => p.id) // First 5 pages if none selected

    pagePrefetch.prefetchForEditor(projectId, pageIdsToLoad)
  }, 200) // 200ms debounce
}

function handleEditorButtonLeave() {
  if (prefetchTimeout) {
    clearTimeout(prefetchTimeout)
    prefetchTimeout = null
  }
}

async function handleOpenInEditor() {
  if (!pages.value || pages.value.length === 0) return

  try {
    isLoadingEditor.value = true

    const pagesToUse = selectedPageIds.value.size > 0
      ? pages.value.filter(p => selectedPageIds.value.has(p.id))
      : pages.value

    const pageResponses: PageResponse[] = pagesToUse.map(page => ({
      id: page.id,
      name: page.name,
      thumbnailUrl: page.thumbnailUrl ?? undefined,
      tags: page.tags ?? [],
      resolvedTags: page.resolvedTags ?? null,
      imageCount: page.imageCount,
      xmlFileCount: page.xmlFileCount,
      indexingStatus: page.indexingStatus
    }))

    const skeletonPages = createSkeletonPageData(pageResponses, {
      projectId,
      projectName: project.value?.name
    })

    editorStore.setPagesWithSession(skeletonPages, projectId, selectedWorkspace.value ?? null)
    await loadProjectLabelSet()

    await navigateTo('/editor')
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : ''
    toast.add({
      title: 'Failed to open editor',
      description: message || 'An error occurred',
      color: 'error',
      icon: 'i-lucide-alert-circle'
    })
  } finally {
    isLoadingEditor.value = false
  }
}

const overlay = useOverlay()

const pageEditSlideover = overlay.create(LazyPageSlideoverEdit)
const pageDeleteSlideover = overlay.create(LazyUiDeleteSlideover)
const pageImagesModal = overlay.create(LazyPageModalImages)
const conflictResolutionModal = overlay.create(LazyProjectModalConflictResolution)
const pdfPrefixSlideover = overlay.create(LazyProjectSlideoverPdfPrefix)
const exportTargetSlideover = overlay.create(LazyProjectSlideoverExportTarget)
const confirmSlideover = overlay.create(LazyUiConfirmSlideover)

const projectEditSlideover = overlay.create(LazyProjectSlideoverEdit, {
  async onUpdated(updatedProject: ProjectData) {
    project.value = updatedProject
    await refreshProject()
  }
} as any)
const projectDeleteSlideover = overlay.create(LazyUiDeleteSlideover)
const projectShareSlideover = overlay.create(LazyShareSlideover, {
  async onTransferred() {
    await refreshProject()
  }
} as any)
const codecActionSlideover = overlay.create(LazyCodecSlideoverAction)
const bulkDeletePagesSlideover = overlay.create(LazyProjectSlideoverBulkDeletePages)
const versionHistorySlideover = overlay.create(LazyEditorVersionHistorySlideover)
const xmlEditorSlideover = overlay.create(LazyProjectSlideoverXmlEditor)

const router = useRouter()
const isDeletingProject = ref(false)

async function goToLibrary() {
  await router.push('/')
}

const projectNotFoundActions = computed(() => [
  {
    icon: 'i-lucide-arrow-left',
    label: 'Back to library',
    color: 'neutral' as const,
    variant: 'solid' as const,
    onClick: goToLibrary
  },
  {
    icon: 'i-lucide-refresh-cw',
    label: 'Try again',
    color: 'neutral' as const,
    variant: 'ghost' as const,
    onClick: refreshProject
  }
])

async function handleDeleteProject() {
  if (!allow(projectCapabilities.value.canDelete)) return
  if (!project.value || isDeletingProject.value) return

  const instance = projectDeleteSlideover.open({
    name: project.value.name,
    entityType: 'Project',
    warningDetails: [
      `${project.value.pageCount} ${project.value.pageCount === 1 ? 'page' : 'pages'}`,
      'All associated images and XML files',
      'All project history and annotations'
    ]
  })
  const confirmed = await instance.result
  if (!confirmed) return

  const workspaceId = selectedWorkspace.value
  if (!workspaceId) {
    toast.add({
      title: 'Delete Failed',
      description: 'No workspace selected',
      color: 'error'
    })
    return
  }

  const projectName = project.value.name
  const deletingProjectId = projectId
  const progressToast = toast.add({
    title: 'Deleting Project',
    description: projectName,
    color: 'neutral',
    icon: 'i-lucide-loader-circle',
    ui: { icon: 'animate-spin' },
    close: false,
    progress: false,
    duration: 0
  })
  const { data: libraryProjects } = useNuxtData<ProjectData[]>(libraryProjectsKey.value)
  const previousLibraryProjects = libraryProjects.value ? [...libraryProjects.value] : null

  if (libraryProjects.value) {
    libraryProjects.value = libraryProjects.value.filter(item => item.id !== deletingProjectId)
  }

  isDeletingProject.value = true

  try {
    await router.push('/')
  } catch {
    // Continue deletion even when navigation is interrupted.
  }

  try {
    await $fetch(`/api/workspaces/${workspaceId}/projects/${deletingProjectId}`, { method: 'DELETE' })
    toast.add({
      title: 'Project Deleted',
      description: `"${projectName}" has been permanently deleted`,
      color: 'success',
      icon: 'i-lucide-trash-2'
    })

    void refreshNuxtData(wsKey(workspaceId, 'projects', 'list'))
  } catch (error: unknown) {
    if (previousLibraryProjects) {
      libraryProjects.value = previousLibraryProjects
    }

    const message = error instanceof Error ? error.message : ''
    toast.add({
      title: 'Delete Failed',
      description: message || 'Failed to delete project',
      color: 'error'
    })

    void refreshNuxtData(wsKey(workspaceId, 'projects', 'list'))
  } finally {
    toast.remove(progressToast.id)
    isDeletingProject.value = false
  }
}

async function exportProjectPackage() {
  if (!selectedWorkspace.value || !project.value) return

  const targetPageXmlVersion = await requestPageXmlExportTarget('package')
  if (!targetPageXmlVersion) return

  const payload = hasSelection.value
    ? { pageIds: Array.from(selectedPageIds.value), targetPageXmlVersion }
    : { pageIds: null, targetPageXmlVersion }
  const fallbackName = `${project.value.name.replace(/\\s+/g, '-').toLowerCase()}.larex-project.zip`

  try {
    const response = await fetch(`/api/workspaces/${selectedWorkspace.value}/projects/${projectId}/export-package`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })

    if (!response.ok) {
      throw new Error(`Export failed (${response.status})`)
    }

    const blob = await response.blob()
    const contentDisposition = response.headers.get('content-disposition')
    const match = contentDisposition?.match(/filename\*?=(?:UTF-8''|"?)([^";]+)/i)
    const fileName = match ? decodeURIComponent(match[1]!) : fallbackName

    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = fileName
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)

    toast.add({
      title: 'Project package exported',
      color: 'success',
      icon: 'i-lucide-download'
    })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Failed to export project package'
    toast.add({
      title: 'Export failed',
      description: message,
      color: 'error'
    })
  }
}

async function exportPageXml(page: Page) {
  try {
    const xmlFiles = await $fetch<{ id: string }[]>(`/api/projects/${projectId}/pages/${page.id}/xml`)
    if (!xmlFiles?.length) {
      toast.add({
        title: 'No XML files',
        description: 'This page has no XML files to export.',
        color: 'warning'
      })
      return
    }

    const xmlId = xmlFiles[0]!.id
    const targetPageXmlVersion = await requestPageXmlExportTarget('page')
    if (!targetPageXmlVersion) return
    const query = new URLSearchParams({ targetPageXmlVersion })

    const a = document.createElement('a')
    a.href = `/api/projects/${projectId}/pages/xml/${xmlId}/export?${query.toString()}`
    a.download = `${page.name}.xml`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)

    if (xmlFiles.length > 1) {
      toast.add({
        title: 'Multiple XML files found',
        description: 'Exported the first XML variant for this page.',
        color: 'info'
      })
    }
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Failed to export XML'
    toast.add({
      title: 'Export failed',
      description: message,
      color: 'error'
    })
  }
}

const PAGE_XML_PRIMARY_VERSION = '2019-07-15'

async function requestPageXmlExportTarget(exportType: 'page' | 'package'): Promise<string | null> {
  const selector = exportTargetSlideover.open({
    title: exportType === 'page' ? 'Export XML' : 'Export Project Package',
    description: exportType === 'page'
      ? 'Choose the PAGE XML target schema version for this file export.'
      : 'Choose the PAGE XML target schema version for XML files included in the package.',
    initialTargetVersion: PAGE_XML_PRIMARY_VERSION,
    confirmLabel: exportType === 'page' ? 'Export XML' : 'Export Package'
  })

  const selectedVersion = await selector.result as string | null
  if (!selectedVersion) {
    return null
  }

  if (selectedVersion === PAGE_XML_PRIMARY_VERSION) {
    return selectedVersion
  }

  const confirmation = confirmSlideover.open({
    title: 'Confirm Legacy PAGE XML Export',
    message: 'Exporting to an older PAGE XML schema may drop PAGE 2019-only data. Continue anyway?',
    confirmLabel: 'Export anyway',
    confirmColor: 'warning',
    confirmIcon: 'i-lucide-triangle-alert'
  })

  const confirmed = await confirmation.result as boolean
  return confirmed ? selectedVersion : null
}

const actionItems = computed<DropdownMenuItem[]>(() => {
  const items: DropdownMenuItem[] = [
    {
      label: hasSelection.value ? 'Generate codec (selected pages)' : 'Generate codec (all pages)',
      icon: 'i-lucide-wand-sparkles',
      disabled: (pages.value?.length ?? 0) === 0,
      onSelect: () => {
        void openCodecGenerateSlideover()
      }
    },
    {
      label: hasSelection.value ? 'Validate codec (selected pages)' : 'Validate codec (all pages)',
      icon: 'i-lucide-badge-check',
      disabled: (pages.value?.length ?? 0) === 0,
      onSelect: () => {
        void openCodecValidateSlideover()
      }
    },
    {
      label: hasSelection.value ? 'Export package (selected pages)' : 'Export package (full project)',
      icon: 'i-lucide-file-archive',
      disabled: (pages.value?.length ?? 0) === 0 || !allow(projectCapabilities.value.canExportPackage),
      onSelect: () => {
        void exportProjectPackage()
      }
    }
  ]

  if (allow(projectCapabilities.value.canEdit)) {
    items.push({
      label: 'Edit project',
      icon: 'i-lucide-edit',
      disabled: project.value?.locked,
      onSelect: () => project.value && projectEditSlideover.open({ project: project.value as any })
    })
  }

  if (allow(projectCapabilities.value.canShare)) {
    items.push({
      label: 'Share project',
      icon: 'i-lucide-share-2',
      disabled: project.value?.locked,
      onSelect: () => project.value && projectShareSlideover.open({
        resourceId: project.value.id,
        resourceName: project.value.name,
        resourceType: 'PROJECT',
        currentWorkspaceId: selectedWorkspace.value ?? ''
      })
    })
  }

  if (allow(projectCapabilities.value.canDelete)) {
    items.push({
      label: 'Delete project',
      icon: 'i-lucide-trash',
      color: 'error' as const,
      disabled: project.value?.locked || isDeletingProject.value,
      onSelect: handleDeleteProject
    })
  }

  return items
})

const autoCreatedDescription = 'Auto-created from bulk upload'

const getPageDescription = (page: Page) => {
  const description = page.description?.trim()
  if (!description || description === autoCreatedDescription) return ''
  return page.description
}

const pagesSafe = computed(() => (pages.value ?? []).map((page) => {
  const description = getPageDescription(page)
  if (description === page.description) return page
  return { ...page, description }
}))
const {
  sort,
  globalFilter,
  columnFilters,
  tagFilterOperator,
  filteredAndSortedData: filteredAndSortedPages,
  setColumnFilter,
  clearColumnFilter,
  resetAllFilters
} = useTableFilters(pagesSafe, { column: 'name', direction: 'asc' })

const selectedPageIds = ref<Set<string>>(new Set())
const hasSelection = computed(() => selectedPageIds.value.size > 0)

function togglePageSelection(pageId: string) {
  const newSet = new Set(selectedPageIds.value)
  if (newSet.has(pageId)) {
    newSet.delete(pageId)
  } else {
    newSet.add(pageId)
  }
  selectedPageIds.value = newSet
}

function toggleAllPages() {
  if (selectedPageIds.value.size === filteredPages.value.length) {
    selectedPageIds.value = new Set()
  } else {
    selectedPageIds.value = new Set(filteredPages.value.map(p => p.id))
  }
}

function clearSelection() {
  selectedPageIds.value = new Set()
}

const page = ref(1)
const itemsPerPage = ref(25)
const totalItems = computed(() => filteredPages.value.length)
const totalPagesCount = computed(() => Math.ceil(totalItems.value / itemsPerPage.value))
const paginatedPages = computed(() => {
  const start = (page.value - 1) * itemsPerPage.value
  return filteredPages.value.slice(start, start + itemsPerPage.value)
})

async function openBulkDeleteSlideover() {
  if (!allow(projectCapabilities.value.canDeletePages)) return
  if (!hasSelection.value) return

  const selectedPages = Array.from(selectedPageIds.value).map((pageId) => {
    return {
      id: pageId,
      name: pages.value?.find(p => p.id === pageId)?.name || pageId
    }
  })

  const instance = bulkDeletePagesSlideover.open({
    projectId,
    projectName: project.value?.name ?? 'Project',
    pages: selectedPages
  })
  const result = await instance.result
  if (!result) return

  clearSelection()
  await Promise.all([
    refreshPagesData(),
    refreshProject()
  ])
}

const codecSources = computed<CodecProjectScope[]>(() => [{
  projectId,
  pageIds: hasSelection.value ? Array.from(selectedPageIds.value) : []
}])

async function openCodecGenerateSlideover() {
  if (!selectedWorkspace.value) return

  const instance = codecActionSlideover.open({
    mode: 'generate',
    workspaceId: selectedWorkspace.value,
    sources: codecSources.value
  })
  const result = await instance.result as GenerateCodecFromSourcesResponse | null
  if (!result) return

  await Promise.all([
    refreshNuxtData(wsKey(selectedWorkspace.value, 'codecs', 'list')),
    refreshProject()
  ])
}

async function openCodecValidateSlideover() {
  if (!selectedWorkspace.value || !project.value?.codecId) {
    toast.add({
      title: 'No project codec configured',
      description: 'Assign a codec to this project first, then run validation.',
      color: 'warning'
    })
    return
  }

  const instance = codecActionSlideover.open({
    mode: 'validate',
    workspaceId: selectedWorkspace.value,
    sources: codecSources.value,
    defaultCodecId: project.value.codecId
  })
  await instance.result as ValidateCodecAgainstSourcesResponse | null
}

const xmlStatusFilter = ref<'all' | 'has_xml' | 'no_xml'>('all')

const xmlStatusOptions = [
  { value: 'all', label: 'All Pages' },
  { value: 'has_xml', label: 'With XML' },
  { value: 'no_xml', label: 'Without XML' }
]

const filteredPages = computed(() => {
  let result = filteredAndSortedPages.value
  if (xmlStatusFilter.value !== 'all') {
    result = result.filter((page) => {
      const hasXml = page.xmlFileCount > 0
      return xmlStatusFilter.value === 'has_xml' ? hasXml : !hasXml
    })
  }
  return result
})

watch([globalFilter, columnFilters, xmlStatusFilter], () => {
  page.value = 1
}, { deep: true })

const resetFilters = () => {
  resetAllFilters()
  xmlStatusFilter.value = 'all'
}

const uniqueTags = computed(() => {
  if (!pages.value) return []
  const tagCounts = new Map<string, number>()
  pages.value.forEach((page) => {
    page.tags.forEach((tag) => {
      tagCounts.set(tag, (tagCounts.get(tag) || 0) + 1)
    })
  })
  return Array.from(tagCounts.entries())
    .sort((a, b) => a[0].localeCompare(b[0]))
    .map(([tag, count]) => ({ label: tag, value: tag, count }))
})

const selectedTags = computed({
  get: () => {
    const tags = columnFilters.value['tags']
    return Array.isArray(tags) ? tags : []
  },
  set: (value: string[]) => {
    if (value.length === 0) {
      clearColumnFilter('tags')
    } else {
      setColumnFilter('tags', value)
    }
  }
})

const tagOperatorOptions = [
  { label: 'Match any (OR)', value: 'or' },
  { label: 'Match all (AND)', value: 'and' }
]

function renderIndexingStatusBadge(status?: PageIndexingStatus) {
  const value = status ?? 'NOT_APPLICABLE'
  if (value === 'INDEXED') {
    return h(UBadge, { color: 'success', variant: 'soft', size: 'sm' }, () => 'Yes')
  }
  if (value === 'INDEXING') {
    return h(UBadge, { color: 'warning', variant: 'soft', size: 'sm' }, () => [
      h('span', { class: 'inline-block w-1.5 h-1.5 rounded-full bg-amber-500 mr-1 animate-pulse' }),
      'Indexing'
    ])
  }
  if (value === 'UNINDEXED') {
    return h(UBadge, { color: 'error', variant: 'soft', size: 'sm' }, () => 'No')
  }
  return h(UBadge, { color: 'neutral', variant: 'soft', size: 'sm' }, () => 'Empty')
}

const pageColumns = [
  {
    id: 'select',
    header: () => h('input', {
      type: 'checkbox',
      checked: selectedPageIds.value.size === filteredPages.value.length && filteredPages.value.length > 0,
      indeterminate: selectedPageIds.value.size > 0 && selectedPageIds.value.size < filteredPages.value.length,
      onChange: toggleAllPages,
      class: 'rounded-sm border-gray-300 text-primary-600 focus:ring-primary-500'
    }),
    cell: ({ row }: { row: { original: Page } }) => h('input', {
      type: 'checkbox',
      checked: selectedPageIds.value.has(row.original.id),
      onChange: () => togglePageSelection(row.original.id),
      class: 'rounded-sm border-gray-300 text-primary-600 focus:ring-primary-500'
    })
  },
  {
    accessorKey: 'name',
    header: () => h('div', { class: 'flex items-center gap-2' }, [
      h('span', 'Name'),
      h(UButton, {
        icon: sort.value.column === 'name'
          ? (sort.value.direction === 'asc' ? 'i-lucide-arrow-up' : 'i-lucide-arrow-down')
          : 'i-lucide-arrow-up-down',
        size: 'xs',
        variant: 'ghost',
        color: sort.value.column === 'name' ? 'primary' : 'neutral',
        onClick: () => {
          if (sort.value.column === 'name') {
            sort.value.direction = sort.value.direction === 'asc' ? 'desc' : 'asc'
          } else {
            sort.value = { column: 'name', direction: 'asc' }
          }
        }
      })
    ]),
    cell: ({ row }: { row: { original: Page } }) => h('p', { class: 'font-medium truncate' }, row.original.name)
  },
  {
    accessorKey: 'description',
    header: 'Description',
    cell: ({ row }: { row: { original: Page } }) => {
      const description = getPageDescription(row.original)
      return description ? h('p', { class: 'text-sm text-muted truncate' }, description) : null
    }
  },
  {
    accessorKey: 'tags',
    header: 'Tags',
    cell: ({ row }: { row: { original: Page } }) => {
      const resolvedTags = row.original.resolvedTags as ResolvedTag[] | null
      const tags = row.original.tags as string[]
      if (!tags || tags.length === 0) return null

      const displayTags = resolvedTags && resolvedTags.length > 0
        ? resolvedTags.map(rt => ({ label: rt.label || rt.id, color: rt.color || DEFAULT_CUSTOM_TAG_COLOR }))
        : tags.map(tagId => ({ label: tagId, color: DEFAULT_CUSTOM_TAG_COLOR }))

      const renderTagBadge = (tag: { label: string, color: string }, index: number) => h(
        UiColorTag,
        {
          key: `${tag.label}-${index}`,
          color: tag.color,
          variant: 'subtle',
          size: 'sm'
        },
        () => tag.label
      )

      if (tags.length <= 2) {
        return h('div', { class: 'flex flex-wrap gap-1' },
          displayTags.map((tag, index) => renderTagBadge(tag, index))
        )
      }

      const visibleTags = displayTags.slice(0, 2)
      const hiddenTags = displayTags.slice(2)

      return h('div', { class: 'flex flex-wrap gap-1' }, [
        ...visibleTags.map((tag, index) => renderTagBadge(tag, index)),
        h(UBadge, { color: 'primary', variant: 'subtle', size: 'sm' }, () => `+${hiddenTags.length}`)
      ])
    }
  },
  {
    accessorKey: 'imageCount',
    header: () => h('div', { class: 'flex items-center justify-end gap-2' }, [
      h('span', 'Images'),
      h(UButton, {
        icon: sort.value.column === 'imageCount'
          ? (sort.value.direction === 'asc' ? 'i-lucide-arrow-up' : 'i-lucide-arrow-down')
          : 'i-lucide-arrow-up-down',
        size: 'xs',
        variant: 'ghost',
        color: sort.value.column === 'imageCount' ? 'primary' : 'neutral',
        onClick: () => {
          if (sort.value.column === 'imageCount') {
            sort.value.direction = sort.value.direction === 'asc' ? 'desc' : 'asc'
          } else {
            sort.value = { column: 'imageCount', direction: 'desc' }
          }
        }
      })
    ]),
    cell: ({ row }: { row: { original: Page } }) => h('div', { class: 'text-right font-medium' }, row.original.imageCount)
  },
  {
    accessorKey: 'xmlFileCount',
    header: 'XML',
    cell: ({ row }: { row: { original: Page } }) => h(UBadge, {
      color: row.original.xmlFileCount > 0 ? 'success' : 'neutral',
      variant: 'soft',
      size: 'sm'
    }, () => row.original.xmlFileCount > 0 ? 'Yes' : 'No')
  },
  {
    accessorKey: 'indexingStatus',
    header: 'Indexed',
    cell: ({ row }: { row: { original: Page } }) => renderIndexingStatusBadge(row.original.indexingStatus)
  },
  {
    id: 'mySubtasks',
    header: 'My Tasks',
    cell: ({ row }: { row: { original: Page } }) => {
      const count = subtaskSummary.value?.[row.original.id] || 0
      if (count === 0) return null
      const subtasks = openSubtasksByPage.value?.[row.original.id] || []
      return h(UPopover, { mode: 'hover' }, {
        default: () => h(UBadge, {
          color: 'warning',
          variant: 'soft',
          size: 'sm'
        }, () => `${count} open`),
        content: () => h('div', { class: 'p-3 w-64' }, [
          h('p', { class: 'text-xs text-muted mb-2' }, `Open tasks (${count})`),
          subtasks.length > 0
            ? h('ul', { class: 'space-y-2' }, subtasks.map(subtask =>
                h('li', { key: subtask.id, class: 'text-sm' }, [
                  h('div', { class: 'font-medium truncate' }, subtask.title),
                  (subtask.description || subtask.taskDescription)
                    ? h('p', { class: 'text-xs text-muted mt-0.5 line-clamp-2' }, String(subtask.description || subtask.taskDescription))
                    : null
                ])
              ))
            : h('p', { class: 'text-xs text-muted' }, 'Loading subtasks...')
        ])
      })
    }
  },
  {
    accessorKey: 'updated',
    header: () => h('div', { class: 'flex items-center gap-2' }, [
      h('span', 'Updated'),
      h(UButton, {
        icon: sort.value.column === 'updated'
          ? (sort.value.direction === 'asc' ? 'i-lucide-arrow-up' : 'i-lucide-arrow-down')
          : 'i-lucide-arrow-up-down',
        size: 'xs',
        variant: 'ghost',
        color: sort.value.column === 'updated' ? 'primary' : 'neutral',
        onClick: () => {
          if (sort.value.column === 'updated') {
            sort.value.direction = sort.value.direction === 'asc' ? 'desc' : 'asc'
          } else {
            sort.value = { column: 'updated', direction: 'desc' }
          }
        }
      })
    ]),
    cell: ({ row }: { row: { original: Page } }) => h(NuxtTime, { datetime: row.original.updated })
  },
  {
    id: 'actions',
    cell: ({ row }: { row: { original: Page } }) => h(UDropdownMenu, {
      content: { align: 'end' },
      items: getPageRowItems(row.original)
    }, () => h(UButton, { icon: 'i-lucide-ellipsis-vertical', color: 'neutral', variant: 'ghost' }))
  }
]

function getPageRowItems(page: Page) {
  const items: any[] = [
    { label: 'Edit', icon: 'i-lucide-edit', disabled: project.value?.locked || !allow(projectCapabilities.value.canEdit), onSelect: () => openEditModal(page) },
    { label: 'View Images', icon: 'i-lucide-images', disabled: page.imageCount === 0, onSelect: () => openImageModal(page) },
    { label: 'View/Edit XML', icon: 'i-lucide-file-pen-line', disabled: page.xmlFileCount === 0, onSelect: () => openXmlEditor(page) },
    { label: 'Export XML', icon: 'i-lucide-file-code-2', disabled: page.xmlFileCount === 0, onSelect: () => exportPageXml(page) },
    { label: 'Version History', icon: 'i-lucide-history', disabled: page.xmlFileCount === 0, onSelect: () => openVersionHistory(page) }
  ]

  if (allow(projectCapabilities.value.canDeletePages)) {
    items.push({ type: 'separator' })
    items.push({ label: 'Delete', icon: 'i-lucide-trash', color: 'error', disabled: project.value?.locked, onSelect: () => openDeleteModal(page) })
  }

  return items
}

const contextMenuPage = ref<Page | null>(null)
const contextMenuItems = computed(() => {
  if (!contextMenuPage.value) return []
  return getPageRowItems(contextMenuPage.value)
})

function handlePageRowContextMenu(_event: Event, row: { original: Record<string, unknown> }) {
  contextMenuPage.value = row.original as unknown as Page
}

function openEditModal(page: Page) {
  pageEditSlideover.open({
    projectId,
    page
  })
}

async function openDeleteModal(page: Page) {
  if (!allow(projectCapabilities.value.canDeletePages)) return
  const instance = pageDeleteSlideover.open({
    name: page.name,
    entityType: 'Page',
    warningDetails: [
      `${page.imageCount} ${page.imageCount === 1 ? 'image' : 'images'}`,
      'All associated XML files and annotations'
    ]
  })
  const confirmed = await instance.result
  if (confirmed) {
    try {
      await $fetch(`/api/projects/${projectId}/pages/${page.id}`, { method: 'DELETE' })
      toast.add({
        title: 'Page deleted',
        description: `"${page.name}" has been deleted`,
        color: 'success',
        icon: 'i-lucide-trash-2'
      })
      await refreshPagesData()
      await refreshProject()
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : ''
      toast.add({
        title: 'Failed to delete page',
        description: message || 'An error occurred',
        color: 'error'
      })
    }
  }
}

function openImageModal(page: Page) {
  const pageIndex = pages.value?.findIndex(p => p.id === page.id) ?? 0
  const pagesBasic = (pages.value ?? []).map(p => ({ id: p.id, name: p.name }))

  pageImagesModal.open({
    projectId,
    pages: pagesBasic,
    initialPageIndex: pageIndex
  })
}

async function openVersionHistory(page: Page) {
  try {
    const xmlFiles = await $fetch<{ id: string }[]>(`/api/projects/${projectId}/pages/${page.id}/xml`)
    const firstXml = xmlFiles.at(0)
    if (!firstXml) {
      toast.add({
        title: 'No XML files',
        description: 'This page has no XML annotation files.',
        color: 'warning'
      })
      return
    }
    versionHistorySlideover.open({
      projectId,
      pageId: page.id,
      xmlId: firstXml.id
    })
  } catch {
    toast.add({
      title: 'Error',
      description: 'Failed to load XML files for this page.',
      color: 'error'
    })
  }
}

async function openXmlEditor(page: Page) {
  try {
    const xmlFiles = await $fetch<Array<{ id: string, schema: string }>>(`/api/projects/${projectId}/pages/${page.id}/xml`)
    if (!xmlFiles?.length) {
      toast.add({
        title: 'No XML files',
        description: 'This page has no XML annotation files.',
        color: 'warning'
      })
      return
    }

    const pageXml = xmlFiles.find(xml => xml.schema === 'PAGE_XML')
    if (!pageXml) {
      toast.add({
        title: 'No PAGE XML',
        description: 'This page has no PAGE XML file to edit.',
        color: 'warning'
      })
      return
    }

    xmlEditorSlideover.open({
      projectId,
      pageId: page.id,
      xmlId: pageXml.id,
      pageName: page.name
    })
  } catch {
    toast.add({
      title: 'Error',
      description: 'Failed to load PAGE XML for this page.',
      color: 'error'
    })
  }
}

useHead({
  title: computed(() => project.value?.name ? `${project.value.name} - LAREX` : 'Project - LAREX')
})
</script>

<template>
  <UDashboardPanel :id="projectId">
    <template #header>
      <UDashboardNavbar>
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>

        <template #title>
          <UBreadcrumb :items="items">
            <template #separator>
              <span class="mx-2 text-muted">/</span>
            </template>
          </UBreadcrumb>
        </template>

        <template #right>
          <div class="flex items-center gap-2">
            <div v-if="project?.storageUsedFormatted" class="flex items-center gap-1 text-xs text-muted mr-2">
              <UIcon name="i-lucide-hard-drive" class="w-3 h-3" />
              <span>{{ project.storageUsedFormatted }}</span>
            </div>

            <UButton
              v-if="project"
              :icon="project.isStarred ? 'i-prime-star-fill' : 'i-prime-star'"
              color="neutral"
              variant="ghost"
              :loading="isStarring"
              :class="project.isStarred ? 'text-yellow-500 dark:text-yellow-400' : 'text-primary'"
              :aria-label="project.isStarred ? 'Unstar project' : 'Star project'"
              @click="toggleStar"
            />

            <UFieldGroup>
              <UButton
                icon="i-lucide-pencil"
                color="neutral"
                variant="outline"
                :loading="isLoadingEditor"
                :disabled="!pages || pages.length === 0 || project?.locked"
                @click="handleOpenInEditor"
                @mouseenter="handleEditorButtonHover"
                @mouseleave="handleEditorButtonLeave"
                @focus="handleEditorButtonHover"
                @blur="handleEditorButtonLeave"
              >
                Open in Editor
              </UButton>

              <UButton
                v-if="allow(projectCapabilities.canUpload)"
                icon="i-lucide-upload"
                color="neutral"
                variant="outline"
                :loading="isUploading"
                :disabled="projectStatus?.isBlocked || project?.locked"
                @click="() => fileInput?.click()"
              >
                Upload Files
              </UButton>

              <UDropdownMenu :items="actionItems" :content="{ align: 'end' }">
                <UButton
                  color="neutral"
                  variant="outline"
                  icon="i-lucide-chevron-down"
                />
              </UDropdownMenu>
            </UFieldGroup>

            <input
              ref="fileInput"
              type="file"
              multiple
              accept="image/*,.xml,application/pdf,.pdf"
              class="hidden"
              @change="(e) => handleFileUpload((e.target as HTMLInputElement).files)"
            >
          </div>
        </template>
      </UDashboardNavbar>

      <UDashboardToolbar v-if="project">
        <template #left>
          <UInput
            v-model="globalFilter"
            icon="i-lucide-search"
            placeholder="Search pages..."
            class="w-64"
            size="md"
          >
            <template v-if="globalFilter" #trailing>
              <UButton
                color="neutral"
                variant="link"
                icon="i-lucide-x"
                :padded="false"
                @click="globalFilter = ''"
              />
            </template>
          </UInput>

          <USelectMenu
            v-model="selectedTags"
            :items="uniqueTags"
            value-key="value"
            placeholder="Filter by tag"
            multiple
            class="w-48"
          >
            <template #leading>
              <UIcon name="i-lucide-tag" />
            </template>
            <template #item="{ item }">
              <div class="flex items-center justify-between w-full gap-2">
                <div class="flex items-center gap-2">
                  <UIcon
                    v-if="selectedTags.includes(item.value)"
                    name="i-lucide-check"
                    class="text-primary w-4 h-4"
                  />
                  <span v-else class="w-4 h-4" />
                  <span>{{ item.label }}</span>
                </div>
                <UBadge variant="solid" color="neutral" size="xs">
                  {{ item.count }}
                </UBadge>
              </div>
            </template>
          </USelectMenu>

          <USelectMenu
            v-if="selectedTags.length > 1"
            v-model="tagFilterOperator"
            :items="tagOperatorOptions"
            value-key="value"
            class="w-36"
          >
            <template #leading>
              <UIcon name="i-lucide-git-merge" />
            </template>
          </USelectMenu>

          <USelectMenu
            v-model="xmlStatusFilter"
            :items="xmlStatusOptions"
            value-key="value"
            class="w-40"
          >
            <template #leading>
              <UIcon name="i-lucide-file-text" />
            </template>
          </USelectMenu>
        </template>
        <template #right>
          <UButton
            icon="i-lucide-refresh-cw"
            color="neutral"
            variant="ghost"
            size="sm"
            :loading="isManualPagesRefresh"
            @click="refreshPagesData({ manual: true })"
          >
            Refresh
          </UButton>

          <UButton
            icon="i-lucide-x"
            color="neutral"
            variant="ghost"
            size="sm"
            @click="resetFilters"
          >
            Clear Filters
          </UButton>
        </template>
      </UDashboardToolbar>
    </template>

    <template #body>
      <div v-if="projectError" class="mb-4">
        <UEmpty
          v-if="isProjectNotFound"
          variant="naked"
          class="py-4"
          :actions="projectNotFoundActions"
          icon="i-lucide-folder-x"
          title="Project not found"
          description="This project may have been deleted before you opened this link, or you no longer have access to it."
        />

        <UAlert
          v-else
          color="error"
          variant="subtle"
          icon="i-lucide-alert-circle"
          title="Error loading project"
          :description="projectLoadErrorMessage"
        />
      </div>

      <div v-else-if="projectPending" class="flex items-center justify-center py-8">
        <UIcon name="i-lucide-loader" class="animate-spin text-gray-500" />
        <span class="ml-2 text-sm text-gray-600 dark:text-gray-400">Loading project...</span>
      </div>

      <div v-else-if="project" class="space-y-6">
        <div v-if="project.locked" class="bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800 rounded-sm p-4">
          <div class="flex items-start">
            <UIcon name="i-lucide-lock" class="text-amber-500 mt-0.5 mr-3" />
            <div>
              <h4 class="font-medium text-amber-800 dark:text-amber-200">
                Project Locked
              </h4>
              <p class="text-sm text-amber-700 dark:text-amber-300 mt-1">
                {{ project.lockedReason || 'This project is locked and cannot be edited.' }}
              </p>
            </div>
          </div>
        </div>

        <div v-if="projectStatus?.hasUnresolvedConflicts" class="bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800 rounded-sm p-4">
          <div class="flex items-start justify-between">
            <div class="flex items-start">
              <UIcon name="i-lucide-alert-triangle" class="text-amber-500 mt-0.5 mr-3" />
              <div>
                <h4 class="font-medium text-amber-800 dark:text-amber-200">
                  Upload Conflicts Detected
                </h4>
                <p class="text-sm text-amber-700 dark:text-amber-300 mt-1">
                  This project has unresolved file upload conflicts. Some functionality is blocked until conflicts are resolved.
                </p>
              </div>
            </div>
            <UButton
              color="warning"
              variant="solid"
              size="sm"
              @click="viewConflicts"
            >
              Resolve Conflicts
            </UButton>
          </div>
        </div>

        <div class="space-y-4">
          <div class="flex items-center justify-between">
            <h2 class="text-lg font-semibold">
              Pages
            </h2>
            <div class="flex items-center gap-2">
              <UBadge
                v-if="hasSelection"
                variant="soft"
                color="primary"
                size="sm"
              >
                {{ selectedPageIds.size }} selected
              </UBadge>
              <UButton
                v-if="allow(projectCapabilities.canDeletePages)"
                icon="i-lucide-trash-2"
                color="error"
                variant="soft"
                size="sm"
                :disabled="!hasSelection"
                aria-label="Delete selected pages"
                @click="openBulkDeleteSlideover"
              />
            </div>
          </div>

          <div v-if="globalFilter || selectedTags.length > 0 || xmlStatusFilter !== 'all'" class="flex items-center gap-2 flex-wrap">
            <span class="text-xs text-gray-500">Active filters:</span>
            <UBadge
              v-if="globalFilter"
              color="neutral"
              variant="soft"
              size="sm"
              class="cursor-pointer"
              @click="globalFilter = ''"
            >
              Search: {{ globalFilter }} ×
            </UBadge>
            <UBadge
              v-for="tag in selectedTags"
              :key="tag"
              color="neutral"
              variant="soft"
              size="sm"
              class="cursor-pointer"
              @click="selectedTags = selectedTags.filter(t => t !== tag)"
            >
              Tag: {{ tag }} ×
            </UBadge>
            <UBadge
              v-if="xmlStatusFilter !== 'all'"
              color="neutral"
              variant="soft"
              size="sm"
              class="cursor-pointer"
              @click="xmlStatusFilter = 'all'"
            >
              XML: {{ xmlStatusOptions.find(o => o.value === xmlStatusFilter)?.label }} ×
            </UBadge>
          </div>
        </div>

        <div v-if="pagesError" class="py-8 text-center">
          <div class="flex items-center justify-center gap-2 text-red-600 dark:text-red-400">
            <UIcon name="i-lucide-alert-circle" />
            <p class="text-sm">
              <strong>Error loading pages:</strong> {{ pagesError.message || pagesError }}
            </p>
          </div>
        </div>

        <div v-else-if="showPagesLoadingSpinner" class="py-8 text-center">
          <div class="flex items-center justify-center">
            <UIcon name="i-lucide-loader" class="animate-spin text-gray-500" />
            <span class="ml-2 text-sm text-gray-600 dark:text-gray-400">Loading pages...</span>
          </div>
        </div>

        <div v-else-if="pages && pages.length === 0" class="py-12 text-center">
          <UIcon name="i-lucide-file-text" class="mx-auto text-4xl text-gray-400 mb-4" />
          <p class="text-gray-600 dark:text-gray-400 mb-4">
            No pages found in this project.
          </p>
          <div class="text-sm text-gray-500 dark:text-gray-500 space-y-2 max-w-md mx-auto">
            <p><strong>Upload Files:</strong> Upload images and XML files to create pages automatically.</p>
            <p>Files are grouped by basename (everything before the first dot) to create organized pages.</p>
          </div>
        </div>

        <div v-else-if="pages && filteredPages.length === 0" class="py-12 text-center">
          <UIcon name="i-lucide-filter-x" class="mx-auto text-4xl text-gray-400 mb-4" />
          <p class="text-gray-600 dark:text-gray-400 mb-4">
            No pages match your filters.
          </p>
          <UButton
            color="neutral"
            variant="ghost"
            size="sm"
            @click="resetFilters"
          >
            Clear all filters
          </UButton>
        </div>

        <div v-else-if="pages">
          <UContextMenu :items="contextMenuItems as any">
            <UTable
              v-if="paginatedPages.length > 0"
              :columns="pageColumns"
              :data="paginatedPages"
              :loading="isManualPagesRefresh"
              class="flex-1"
              :ui="{
                base: 'table-fixed border-separate border-spacing-0',
                thead: '[&>tr]:bg-elevated/50 [&>tr]:after:content-none',
                tbody: '[&>tr]:last:[&>td]:border-b-0',
                th: 'py-2 first:rounded-l-lg last:rounded-r-lg border-y border-default first:border-l last:border-r',
                td: 'border-b border-default',
                separator: 'h-0'
              }"
              @contextmenu="handlePageRowContextMenu"
            />
          </UContextMenu>

          <div v-if="totalPagesCount > 1" class="flex justify-between items-center p-4 border-t border-gray-200 dark:border-gray-800">
            <div class="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-400">
              <span>Showing {{ (page - 1) * itemsPerPage + 1 }} to {{ Math.min(page * itemsPerPage, totalItems) }} of {{ totalItems }} pages</span>
            </div>

            <div class="flex items-center gap-4">
              <USelect
                v-model="itemsPerPage"
                :items="[10, 25, 50, 100]"
                class="w-32"
                size="sm"
              />

              <UPagination
                v-model:page="page"
                :total="totalItems"
                :items-per-page="itemsPerPage"
                show-edges
                :sibling-count="1"
              />
            </div>
          </div>
        </div>
      </div>
    </template>
  </UDashboardPanel>
</template>
