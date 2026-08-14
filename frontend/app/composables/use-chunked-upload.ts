import type { Ref } from 'vue'
import { extractApiErrorMessage } from '@/utils/api-error'

export interface UploadFile {
  id?: string
  file: File | null
  fileName: string
  fileSize: number
  mimeType: string
  baseName: string
  variant: string
  status: 'pending' | 'uploading' | 'uploaded' | 'processing' | 'completed' | 'failed' | 'conflict' | 'skipped'
  progress: number
  chunksReceived: number
  totalChunks: number
  error?: string
  createdPageId?: string
  createdPageImageId?: string
  conflictType?: string
  conflictResolution?: string
}

export interface UploadSession {
  id: string
  projectId: string
  workspaceId: string
  status: 'PENDING' | 'UPLOADING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  totalFiles: number
  processedFiles: number
  failedFiles: number
  totalBytes: number
  processedBytes: number
  progressPercent: number
  processingCompletedItems: number
  processingTotalItems: number
  processingProgressPercent: number
  processingCurrentFileName?: string
  files: UploadFile[]
  errorMessage?: string
  created: string
  updated: string
  completedAt?: string
}

type UploadSessionServerFile = UploadFile & {
  originalFileName?: string
  chunkCount?: number
}

export interface UseChunkedUploadOptions {
  workspaceId: Ref<string | undefined>
  projectId: Ref<string | undefined>
  chunkSizeBytes?: number
  maxConcurrentChunks?: number
  maxRetries?: number
  onProgress?: (session: UploadSession) => void
  onFileComplete?: (file: UploadFile) => void
  onSessionComplete?: (session: UploadSession) => void
  onBeforeFinalize?: (session: UploadSession, files: UploadFile[]) => Promise<boolean>
  onError?: (error: Error, file?: UploadFile) => void
}

const DEFAULT_CHUNK_SIZE = 5 * 1024 * 1024 // 5MB
const DEFAULT_MAX_CONCURRENT = 3
const DEFAULT_MAX_RETRIES = 3

export async function runSettledWorkerPool<T>(
  items: readonly T[],
  concurrency: number,
  task: (item: T) => Promise<void>,
  shouldStop: () => boolean = () => false
): Promise<void> {
  let nextItemIndex = 0
  const workerCount = Math.min(Math.max(1, concurrency), items.length)
  const worker = async () => {
    while (!shouldStop()) {
      const item = items[nextItemIndex]
      nextItemIndex += 1
      if (item === undefined) return
      try {
        await task(item)
      } catch {
        // Match Promise.allSettled semantics while retaining only workerCount promises.
      }
    }
  }
  await Promise.all(Array.from({ length: workerCount }, () => worker()))
}

export function useChunkedUpload(options: UseChunkedUploadOptions) {
  const {
    workspaceId,
    projectId,
    chunkSizeBytes = DEFAULT_CHUNK_SIZE,
    maxConcurrentChunks = DEFAULT_MAX_CONCURRENT,
    maxRetries = DEFAULT_MAX_RETRIES,
    onProgress,
    onFileComplete,
    onSessionComplete,
    onBeforeFinalize,
    onError
  } = options

  const session = ref<UploadSession | null>(null)
  const files = shallowRef<UploadFile[]>([])
  const isUploading = ref(false)
  const isPaused = ref(false)
  const error = ref<string | null>(null)

  let abortController: AbortController | null = null
  let totalUploadBytes = 0
  let completedUploadBytes = 0
  let progressTimer: ReturnType<typeof setTimeout> | null = null

  function parseFileName(fileName: string): { baseName: string, variant: string } {
    const dotIndex = fileName.indexOf('.')
    if (dotIndex === -1) {
      return { baseName: fileName, variant: '' }
    }
    return {
      baseName: fileName.substring(0, dotIndex),
      variant: fileName.substring(dotIndex + 1)
    }
  }

  function addFiles(newFiles: File[]) {
    const uploadFiles: UploadFile[] = newFiles.map((file) => {
      const { baseName, variant } = parseFileName(file.name)
      return {
        file: markRaw(file),
        fileName: file.name,
        fileSize: file.size,
        mimeType: file.type || 'application/octet-stream',
        baseName,
        variant,
        status: 'pending',
        progress: 0,
        chunksReceived: 0,
        totalChunks: Math.ceil(file.size / chunkSizeBytes)
      }
    })
    files.value = [...files.value, ...uploadFiles]
    return uploadFiles
  }

  function removeFile(fileName: string) {
    const removed = files.value.find(file => file.fileName === fileName)
    if (removed) removed.file = null
    files.value = files.value.filter(f => f.fileName !== fileName)
  }

  function clearFiles() {
    for (const file of files.value) file.file = null
    files.value = []
    session.value = null
    error.value = null
    clearProgressTimer()
    totalUploadBytes = 0
    completedUploadBytes = 0
  }

  async function createSession(): Promise<UploadSession> {
    if (!workspaceId.value || !projectId.value) {
      throw new Error('Workspace ID and Project ID are required')
    }

    if (files.value.length === 0) {
      throw new Error('No files to upload')
    }

    const fileMetadata = files.value.map(f => ({
      fileName: f.fileName,
      fileSize: f.fileSize,
      mimeType: f.mimeType,
      baseName: f.baseName,
      variant: f.variant
    }))

    const response = await $fetch<UploadSession>(
      `/api/workspaces/${workspaceId.value}/projects/${projectId.value}/upload-sessions`,
      {
        method: 'POST',
        body: { files: fileMetadata }
      }
    )

    if (response.files && Array.isArray(response.files)) {
      const localFilesByName = new Map(files.value.map(file => [file.fileName, file]))
      for (const serverFile of response.files as UploadSessionServerFile[]) {
        const serverFileName = serverFile.originalFileName || serverFile.fileName
        const localFile = localFilesByName.get(serverFileName)
        if (localFile) {
          localFile.id = serverFile.id
          localFile.totalChunks = serverFile.chunkCount || serverFile.totalChunks
        }
      }
    }

    // The server file list is only needed to attach IDs to local entries. Keeping
    // a second full 1,500-file response tree alive for the entire upload doubles
    // the metadata retained by the long-running upload task.
    session.value = { ...response, files: [] }
    return session.value
  }

  async function uploadChunkWithRetry(
    sessionId: string,
    fileId: string,
    chunkIndex: number,
    totalChunks: number,
    chunkData: Blob,
    retryCount = 0
  ): Promise<boolean> {
    try {
      const formData = new FormData()
      formData.append('file', chunkData)

      await $fetch(
        `/api/upload-proxy/workspaces/${workspaceId.value}/projects/${projectId.value}/upload-sessions/${sessionId}/files/${fileId}/chunks`,
        {
          method: 'POST',
          body: formData,
          params: {
            chunkIndex,
            totalChunks
          },
          signal: abortController?.signal
        }
      )

      return true
    } catch (err) {
      if (retryCount < maxRetries && !isPaused.value) {
        const delay = Math.min(1000 * Math.pow(2, retryCount), 10000)
        await new Promise(resolve => setTimeout(resolve, delay))
        return uploadChunkWithRetry(sessionId, fileId, chunkIndex, totalChunks, chunkData, retryCount + 1)
      }
      throw err
    }
  }

  async function uploadFile(uploadFile: UploadFile): Promise<void> {
    if (!session.value) {
      const err = new Error(`Session not initialized for file: ${uploadFile.fileName}`)
      uploadFile.status = 'failed'
      uploadFile.error = err.message
      throw err
    }

    if (!uploadFile.id) {
      const err = new Error(`File ID missing for: ${uploadFile.fileName}`)
      uploadFile.status = 'failed'
      uploadFile.error = err.message
      throw err
    }
    uploadFile.status = 'uploading'

    const file = uploadFile.file
    if (!file) {
      const err = new Error(`Local file is no longer available: ${uploadFile.fileName}`)
      uploadFile.status = 'failed'
      uploadFile.error = err.message
      throw err
    }
    const totalChunks = uploadFile.totalChunks

    for (let chunkIndex = uploadFile.chunksReceived; chunkIndex < totalChunks; chunkIndex++) {
      if (isPaused.value) {
        return
      }

      const start = chunkIndex * chunkSizeBytes
      const end = Math.min(start + chunkSizeBytes, file.size)
      const chunk = file.slice(start, end)

      try {
        await uploadChunkWithRetry(
          session.value.id,
          uploadFile.id,
          chunkIndex,
          totalChunks,
          chunk
        )

        uploadFile.chunksReceived = chunkIndex + 1
        uploadFile.progress = Math.round((uploadFile.chunksReceived / totalChunks) * 100)

        updateSessionProgress(chunk.size)
      } catch (err) {
        uploadFile.status = 'failed'
        uploadFile.file = null
        const message = extractApiErrorMessage(err, 'Upload failed')
        uploadFile.error = message
        onError?.(new Error(message), uploadFile)
        throw err
      }
    }

    uploadFile.status = 'uploaded'
    uploadFile.file = null
    onFileComplete?.(uploadFile)
  }

  async function uploadFilesWithConcurrency(): Promise<void> {
    const pendingFiles = files.value.filter(f => f.status === 'pending' || f.status === 'uploading')
    await runSettledWorkerPool(pendingFiles, maxConcurrentChunks, uploadFile, () => isPaused.value)
  }

  function clearProgressTimer() {
    if (!progressTimer) return
    clearTimeout(progressTimer)
    progressTimer = null
  }

  function emitProgressNow() {
    clearProgressTimer()
    if (session.value) onProgress?.(session.value)
  }

  function scheduleProgressUpdate() {
    if (progressTimer) return
    progressTimer = setTimeout(() => {
      progressTimer = null
      if (session.value) onProgress?.(session.value)
    }, 100)
  }

  function initializeProgressCounters() {
    totalUploadBytes = files.value.reduce((sum, file) => sum + file.fileSize, 0)
    completedUploadBytes = files.value.reduce((sum, file) => {
      if (file.totalChunks <= 0) return sum
      const completedRatio = Math.min(1, Math.max(0, file.chunksReceived / file.totalChunks))
      return sum + file.fileSize * completedRatio
    }, 0)
  }

  function updateSessionProgress(uploadedBytes: number) {
    if (!session.value) return

    completedUploadBytes = Math.min(totalUploadBytes, completedUploadBytes + Math.max(0, uploadedBytes))
    session.value.processedBytes = completedUploadBytes
    session.value.progressPercent = totalUploadBytes > 0
      ? Math.round((completedUploadBytes / totalUploadBytes) * 100)
      : 0
    scheduleProgressUpdate()
  }

  async function finalizeSession(conflictResolutions?: Array<{ fileId: string, resolution: string }>): Promise<void> {
    if (!session.value || !workspaceId.value || !projectId.value) {
      throw new Error('Session not initialized')
    }

    session.value.status = 'PROCESSING'
    emitProgressNow()

    try {
      await $fetch(
        `/api/workspaces/${workspaceId.value}/projects/${projectId.value}/upload-sessions/${session.value.id}/finalize`,
        {
          method: 'POST',
          body: conflictResolutions ? { conflictResolutions } : {}
        }
      )
    } catch (err) {
      session.value.status = 'UPLOADING'
      throw err
    }
  }

  async function startUpload(): Promise<void> {
    if (isUploading.value) return
    if (files.value.length === 0) {
      error.value = 'No files to upload'
      return
    }

    isUploading.value = true
    isPaused.value = false
    error.value = null
    abortController = new AbortController()

    try {
      if (!session.value) {
        await createSession()
      }

      initializeProgressCounters()

      await uploadFilesWithConcurrency()

      const allUploaded = files.value.every((f) => {
        if (f.status === 'failed' || f.status === 'skipped' || f.status === 'conflict') {
          return true
        }
        if (f.status === 'uploaded' || f.status === 'processing' || f.status === 'completed') {
          return true
        }
        return f.chunksReceived >= f.totalChunks
      })

      if (allUploaded && !isPaused.value) {
        const shouldFinalize = await (onBeforeFinalize?.(session.value!, files.value) ?? true)
        if (shouldFinalize && !isPaused.value) {
          await finalizeSession()
          onSessionComplete?.(session.value!)
        }
      }
    } catch (err) {
      const message = extractApiErrorMessage(err, 'Upload failed')
      error.value = message
      onError?.(new Error(message))
    } finally {
      isUploading.value = false
    }
  }

  function pauseUpload() {
    isPaused.value = true
  }

  async function resumeUpload() {
    if (!isPaused.value) return
    isPaused.value = false
    await startUpload()
  }

  async function cancelUpload() {
    isPaused.value = true
    abortController?.abort()

    for (const file of files.value) file.file = null
    clearProgressTimer()

    session.value = null
    isUploading.value = false
  }

  async function getSessionStatus(sessionId: string): Promise<UploadSession | null> {
    if (!workspaceId.value || !projectId.value) return null

    try {
      const response = await $fetch<UploadSession>(
        `/api/workspaces/${workspaceId.value}/projects/${projectId.value}/upload-sessions/${sessionId}`
      )
      return response
    } catch {
      return null
    }
  }

  const totalFiles = computed(() => files.value.length)
  const completedFiles = computed(() => files.value.filter(f => f.status === 'completed').length)
  const failedFiles = computed(() => files.value.filter(f => f.status === 'failed').length)
  const overallProgress = computed(() => session.value?.progressPercent ?? 0)

  return {
    session: readonly(session),
    files: readonly(files),
    isUploading: readonly(isUploading),
    isPaused: readonly(isPaused),
    error: readonly(error),

    totalFiles,
    completedFiles,
    failedFiles,
    overallProgress,

    addFiles,
    removeFile,
    clearFiles,
    startUpload,
    pauseUpload,
    resumeUpload,
    cancelUpload,
    finalizeSession,
    getSessionStatus
  }
}
