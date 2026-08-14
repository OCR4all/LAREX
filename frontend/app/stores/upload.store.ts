import { defineStore } from 'pinia'

export type UploadSessionStatus = 'PENDING' | 'UPLOADING' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
export type UploadUiFileStatus = 'pending' | 'uploading' | 'uploaded' | 'processing' | 'completed' | 'failed' | 'conflict' | 'skipped'

interface UploadUiFileBase {
  id?: string
  fileName: string
  fileSize: number
  mimeType: string
  baseName: string
  variant: string
  status: UploadUiFileStatus
  progress: number
  chunksReceived: number
  totalChunks: number
  error?: string
  createdPageId?: string
  createdPageImageId?: string
  conflictType?: string
  conflictResolution?: string
}

export type UploadUiFile = UploadUiFileBase

export interface ActiveUpload {
  sessionId: string
  projectId: string
  projectName: string
  workspaceId: string
  status: UploadSessionStatus
  cancelable?: boolean
  totalFiles: number
  uploadedFiles: number
  processedFiles: number
  failedFiles: number
  progressPercent: number
  processingCompletedItems: number
  processingTotalItems: number
  processingProgressPercent: number
  processingCurrentFileName?: string
  files: UploadUiFile[]
  created: string
  error?: string
}

function isTerminalStatus(status: ActiveUpload['status']): boolean {
  return status === 'COMPLETED' || status === 'FAILED' || status === 'CANCELLED'
}

function isActiveStatus(status: ActiveUpload['status']): boolean {
  return status === 'PENDING' || status === 'UPLOADING' || status === 'PROCESSING'
}

type RegisterUploadOptions = Partial<Pick<ActiveUpload, 'status' | 'processedFiles' | 'failedFiles' | 'progressPercent' | 'processingCompletedItems' | 'processingTotalItems' | 'processingProgressPercent' | 'processingCurrentFileName' | 'created' | 'error' | 'cancelable'>>

export const useUploadStore = defineStore('upload', () => {
  const activeUploads = shallowRef<Map<string, ActiveUpload>>(new Map())
  const showProgressPanel = ref(false)
  const minimized = ref(false)
  const cancellingSessionIds = ref<Set<string>>(new Set())
  const dismissedSessionIds = ref<Set<string>>(new Set())
  const uploadedFileIdsBySession = new Map<string, Set<string>>()
  const trackedFileIndexesBySession = new Map<string, Map<string, number>>()

  const MAX_TRACKED_FILE_DETAILS = 20

  function fileKey(file: Pick<UploadUiFile, 'id' | 'fileName'>): string {
    return file.id || file.fileName
  }

  function isUploadedFile(file: UploadUiFile): boolean {
    if (file.totalChunks > 0 && file.chunksReceived >= file.totalChunks) return true
    return file.status === 'uploaded'
      || file.status === 'processing'
      || file.status === 'completed'
      || file.status === 'failed'
      || file.status === 'conflict'
      || file.status === 'skipped'
  }

  function copyUploadUiFile(file: UploadUiFile): UploadUiFile {
    const { file: _file, source: _source, ...metadata } = file as UploadUiFile & {
      file?: File
      source?: string
    }
    return { ...metadata }
  }

  function rebuildTrackedFileIndex(sessionId: string, files: UploadUiFile[]) {
    const indexes = new Map<string, number>()
    files.forEach((file, index) => {
      indexes.set(file.fileName, index)
      if (file.id) indexes.set(file.id, index)
    })
    trackedFileIndexesBySession.set(sessionId, indexes)
  }

  function triggerUploads() {
    triggerRef(activeUploads)
  }

  const hasActiveUploads = computed(() => {
    return Array.from(activeUploads.value.values()).some(
      u => isActiveStatus(u.status)
    )
  })

  const totalActiveUploads = computed(() => {
    return Array.from(activeUploads.value.values()).filter(
      u => isActiveStatus(u.status)
    ).length
  })

  const overallProgress = computed(() => {
    const active = Array.from(activeUploads.value.values()).filter(
      u => isActiveStatus(u.status)
    )
    if (active.length === 0) return 0

    const totalProgress = active.reduce((sum, u) => sum + u.progressPercent, 0)
    return Math.round(totalProgress / active.length)
  })

  function registerUpload(
    sessionId: string,
    projectId: string,
    projectName: string,
    workspaceId: string,
    files: UploadUiFile[],
    options: RegisterUploadOptions = {}
  ) {
    if (dismissedSessionIds.value.has(sessionId)) {
      return
    }

    const nextDismissed = new Set(dismissedSessionIds.value)
    nextDismissed.delete(sessionId)
    dismissedSessionIds.value = nextDismissed

    const uploadedFileIds = new Set(files.filter(isUploadedFile).map(fileKey))
    const filesCopy = files.slice(0, MAX_TRACKED_FILE_DETAILS).map(copyUploadUiFile)
    uploadedFileIdsBySession.set(sessionId, uploadedFileIds)
    rebuildTrackedFileIndex(sessionId, filesCopy)

    activeUploads.value.set(sessionId, {
      sessionId,
      projectId,
      projectName,
      workspaceId,
      status: options.status ?? 'PENDING',
      cancelable: options.cancelable ?? true,
      totalFiles: files.length,
      uploadedFiles: uploadedFileIds.size,
      processedFiles: options.processedFiles ?? 0,
      failedFiles: options.failedFiles ?? 0,
      progressPercent: options.progressPercent ?? 0,
      processingCompletedItems: options.processingCompletedItems ?? 0,
      processingTotalItems: options.processingTotalItems ?? 0,
      processingProgressPercent: options.processingProgressPercent ?? 0,
      ...(options.processingCurrentFileName ? { processingCurrentFileName: options.processingCurrentFileName } : {}),
      files: filesCopy,
      created: options.created ?? new Date().toISOString(),
      ...(options.error ? { error: options.error } : {})
    })
    showProgressPanel.value = true
    minimized.value = false
    triggerUploads()
  }

  function updateUploadProgress(
    sessionId: string,
    updates: Partial<Omit<ActiveUpload, 'sessionId' | 'projectId' | 'workspaceId' | 'created'>>
  ) {
    if (
      dismissedSessionIds.value.has(sessionId)
      && updates.status
      && isTerminalStatus(updates.status)
      && !activeUploads.value.has(sessionId)
    ) {
      return
    }

    const upload = activeUploads.value.get(sessionId)
    if (upload) {
      const mergedUpdates = { ...updates }
      if (mergedUpdates.status && isTerminalStatus(upload.status) && mergedUpdates.status !== upload.status) {
        delete mergedUpdates.status
      }
      if (typeof mergedUpdates.processedFiles === 'number') {
        mergedUpdates.processedFiles = Math.max(upload.processedFiles, mergedUpdates.processedFiles)
      }
      if (typeof mergedUpdates.failedFiles === 'number') {
        mergedUpdates.failedFiles = Math.max(upload.failedFiles, mergedUpdates.failedFiles)
      }
      if (typeof mergedUpdates.uploadedFiles === 'number') {
        mergedUpdates.uploadedFiles = Math.max(upload.uploadedFiles, mergedUpdates.uploadedFiles)
      }
      if (mergedUpdates.files) {
        const allFiles = mergedUpdates.files
        const uploadedFileIds = uploadedFileIdsBySession.get(sessionId) ?? new Set<string>()
        for (const file of allFiles) {
          if (isUploadedFile(file)) uploadedFileIds.add(fileKey(file))
        }
        uploadedFileIdsBySession.set(sessionId, uploadedFileIds)
        mergedUpdates.uploadedFiles = Math.max(upload.uploadedFiles, uploadedFileIds.size)
        mergedUpdates.files = allFiles.slice(0, MAX_TRACKED_FILE_DETAILS).map(copyUploadUiFile)
        rebuildTrackedFileIndex(sessionId, mergedUpdates.files)
      }
      if (mergedUpdates.status === 'PROCESSING' || mergedUpdates.status === 'COMPLETED') {
        mergedUpdates.uploadedFiles = Math.max(mergedUpdates.uploadedFiles ?? 0, upload.totalFiles)
      }
      const updatedUpload = { ...upload, ...mergedUpdates }
      activeUploads.value.set(sessionId, updatedUpload)
      triggerUploads()
    }
  }

  function replaceUploadSessionId(previousSessionId: string, nextSessionId: string) {
    if (!previousSessionId || !nextSessionId || previousSessionId === nextSessionId) return
    const upload = activeUploads.value.get(previousSessionId)
    if (!upload || activeUploads.value.has(nextSessionId)) return

    activeUploads.value.delete(previousSessionId)
    activeUploads.value.set(nextSessionId, {
      ...upload,
      sessionId: nextSessionId
    })
    const uploadedFileIds = uploadedFileIdsBySession.get(previousSessionId)
    if (uploadedFileIds) {
      uploadedFileIdsBySession.delete(previousSessionId)
      uploadedFileIdsBySession.set(nextSessionId, uploadedFileIds)
    }
    const trackedFileIndexes = trackedFileIndexesBySession.get(previousSessionId)
    if (trackedFileIndexes) {
      trackedFileIndexesBySession.delete(previousSessionId)
      trackedFileIndexesBySession.set(nextSessionId, trackedFileIndexes)
    }

    if (isCancelling(previousSessionId)) {
      setCancelling(previousSessionId, false)
      setCancelling(nextSessionId, true)
    }
    triggerUploads()
  }

  function updateFileProgress(sessionId: string, fileId: string, updates: Partial<UploadUiFile>) {
    const upload = activeUploads.value.get(sessionId)
    if (upload) {
      const uploadedFileIds = uploadedFileIdsBySession.get(sessionId) ?? new Set<string>()
      const statusCandidate = { ...updates, id: fileId, fileName: updates.fileName || fileId } as UploadUiFile
      if (isUploadedFile(statusCandidate)) {
        uploadedFileIds.add(fileId || updates.fileName || '')
        uploadedFileIdsBySession.set(sessionId, uploadedFileIds)
        upload.uploadedFiles = Math.max(upload.uploadedFiles, uploadedFileIds.size)
      }

      const indexes = trackedFileIndexesBySession.get(sessionId)
      const fileIndex = indexes?.get(fileId) ?? (updates.fileName ? indexes?.get(updates.fileName) : undefined) ?? -1
      if (fileIndex !== -1) {
        const currentFile = upload.files[fileIndex]
        if (currentFile) {
          const { file: _file, source: _source, ...safeUpdates } = updates as Partial<UploadUiFile> & {
            file?: File
            source?: string
          }
          Object.assign(currentFile, safeUpdates)
        }
      }
      triggerUploads()
    }
  }

  function completeUpload(sessionId: string, status: 'COMPLETED' | 'FAILED' | 'CANCELLED', error?: string) {
    if (dismissedSessionIds.value.has(sessionId) && !activeUploads.value.has(sessionId)) {
      return
    }

    const upload = activeUploads.value.get(sessionId)
    if (upload) {
      if (isTerminalStatus(upload.status) && upload.status !== status) {
        return
      }
      const updatedUpload = {
        ...upload,
        status,
        progressPercent: status === 'COMPLETED' ? 100 : upload.progressPercent,
        uploadedFiles: status === 'COMPLETED' ? upload.totalFiles : upload.uploadedFiles,
        ...(error ? { error } : {})
      }
      activeUploads.value.set(sessionId, updatedUpload)
      triggerUploads()
    }
    setCancelling(sessionId, false)
  }

  function removeUpload(sessionId: string) {
    acknowledgeUpload(sessionId)
    activeUploads.value.delete(sessionId)
    uploadedFileIdsBySession.delete(sessionId)
    trackedFileIndexesBySession.delete(sessionId)
    setCancelling(sessionId, false)
    if (activeUploads.value.size === 0) {
      showProgressPanel.value = false
    }
    triggerUploads()
  }

  function clearCompletedUploads() {
    const toRemove: string[] = []
    for (const [id, upload] of activeUploads.value) {
      if (upload.status === 'COMPLETED' || upload.status === 'FAILED' || upload.status === 'CANCELLED') {
        acknowledgeUpload(id)
        toRemove.push(id)
      }
    }
    for (const id of toRemove) {
      activeUploads.value.delete(id)
      uploadedFileIdsBySession.delete(id)
      trackedFileIndexesBySession.delete(id)
      setCancelling(id, false)
    }
    if (activeUploads.value.size === 0) {
      showProgressPanel.value = false
    }
    triggerUploads()
  }

  function acknowledgeUpload(sessionId: string) {
    const upload = activeUploads.value.get(sessionId)
    if (!upload || !isTerminalStatus(upload.status)) return
    const next = new Set(dismissedSessionIds.value)
    next.add(sessionId)
    dismissedSessionIds.value = next
  }

  function toggleMinimized() {
    minimized.value = !minimized.value
  }

  function hidePanel() {
    showProgressPanel.value = false
  }

  function showPanel() {
    showProgressPanel.value = true
    minimized.value = false
  }

  function isCancelling(sessionId: string): boolean {
    return cancellingSessionIds.value.has(sessionId)
  }

  function setCancelling(sessionId: string, value: boolean) {
    const next = new Set(cancellingSessionIds.value)
    if (value) {
      next.add(sessionId)
    } else {
      next.delete(sessionId)
    }
    cancellingSessionIds.value = next
  }

  const uploadsArray = computed(() => Array.from(activeUploads.value.values()))

  return {
    activeUploads,
    showProgressPanel,
    minimized,
    cancellingSessionIds,
    dismissedSessionIds,

    hasActiveUploads,
    totalActiveUploads,
    overallProgress,
    uploadsArray,

    registerUpload,
    replaceUploadSessionId,
    updateUploadProgress,
    updateFileProgress,
    completeUpload,
    removeUpload,
    clearCompletedUploads,
    acknowledgeUpload,
    toggleMinimized,
    hidePanel,
    showPanel,
    isCancelling,
    setCancelling
  }
})
