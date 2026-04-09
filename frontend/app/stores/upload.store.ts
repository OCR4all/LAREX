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

export interface LocalUploadUiFile extends UploadUiFileBase {
  source: 'local'
  file: File
}

export interface RecoveredUploadUiFile extends UploadUiFileBase {
  source: 'recovered'
  file?: undefined
}

export type UploadUiFile = LocalUploadUiFile | RecoveredUploadUiFile

export interface ActiveUpload {
  sessionId: string
  projectId: string
  projectName: string
  workspaceId: string
  status: UploadSessionStatus
  totalFiles: number
  processedFiles: number
  failedFiles: number
  progressPercent: number
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

export const useUploadStore = defineStore('upload', () => {
  const activeUploads = ref<Map<string, ActiveUpload>>(new Map())
  const showProgressPanel = ref(false)
  const minimized = ref(false)
  const cancellingSessionIds = ref<Set<string>>(new Set())

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
    files: UploadUiFile[]
  ) {
    const filesCopy = files.map(f => ({ ...f }))

    activeUploads.value.set(sessionId, {
      sessionId,
      projectId,
      projectName,
      workspaceId,
      status: 'PENDING',
      totalFiles: filesCopy.length,
      processedFiles: 0,
      failedFiles: 0,
      progressPercent: 0,
      files: filesCopy,
      created: new Date().toISOString()
    })
    showProgressPanel.value = true
    minimized.value = false
  }

  function updateUploadProgress(
    sessionId: string,
    updates: Partial<Omit<ActiveUpload, 'sessionId' | 'projectId' | 'workspaceId' | 'created'>>
  ) {
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
      const updatedUpload = { ...upload, ...mergedUpdates }
      activeUploads.value.set(sessionId, updatedUpload)
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

    if (isCancelling(previousSessionId)) {
      setCancelling(previousSessionId, false)
      setCancelling(nextSessionId, true)
    }
  }

  function updateFileProgress(sessionId: string, fileId: string, updates: Partial<UploadUiFile>) {
    const upload = activeUploads.value.get(sessionId)
    if (upload) {
      const fileIndex = upload.files.findIndex(f => f.id === fileId || f.fileName === updates.fileName)
      if (fileIndex !== -1) {
        const updatedFiles = [...upload.files]
        const currentFile = updatedFiles[fileIndex]
        if (!currentFile) return

        let mergedFile: UploadUiFile
        if (currentFile.source === 'local') {
          mergedFile = {
            ...currentFile,
            ...updates,
            source: 'local',
            file: currentFile.file
          }
        } else {
          const { file: _ignoredFile, ...updatesWithoutFile } = updates
          mergedFile = {
            ...currentFile,
            ...updatesWithoutFile,
            source: 'recovered'
          }
        }
        updatedFiles[fileIndex] = mergedFile

        const totalChunks = updatedFiles.reduce((sum, f) => sum + f.totalChunks, 0)
        const completedChunks = updatedFiles.reduce((sum, f) => sum + f.chunksReceived, 0)
        const progressPercent = totalChunks > 0 ? Math.round((completedChunks / totalChunks) * 100) : 0

        const processedFiles = Math.max(
          upload.processedFiles,
          updatedFiles.filter(f => f.status === 'completed').length
        )
        const failedFiles = Math.max(
          upload.failedFiles,
          updatedFiles.filter(f => f.status === 'failed').length
        )

        const updatedUpload = {
          ...upload,
          files: updatedFiles,
          progressPercent,
          processedFiles,
          failedFiles
        }
        activeUploads.value.set(sessionId, updatedUpload)
      }
    }
  }

  function completeUpload(sessionId: string, status: 'COMPLETED' | 'FAILED' | 'CANCELLED', error?: string) {
    const upload = activeUploads.value.get(sessionId)
    if (upload) {
      if (isTerminalStatus(upload.status) && upload.status !== status) {
        return
      }
      const updatedUpload = {
        ...upload,
        status,
        progressPercent: status === 'COMPLETED' ? 100 : upload.progressPercent,
        ...(error ? { error } : {})
      }
      activeUploads.value.set(sessionId, updatedUpload)
    }
    setCancelling(sessionId, false)
  }

  function removeUpload(sessionId: string) {
    activeUploads.value.delete(sessionId)
    setCancelling(sessionId, false)
    if (activeUploads.value.size === 0) {
      showProgressPanel.value = false
    }
  }

  function clearCompletedUploads() {
    const toRemove: string[] = []
    for (const [id, upload] of activeUploads.value) {
      if (upload.status === 'COMPLETED' || upload.status === 'FAILED' || upload.status === 'CANCELLED') {
        toRemove.push(id)
      }
    }
    for (const id of toRemove) {
      activeUploads.value.delete(id)
      setCancelling(id, false)
    }
    if (activeUploads.value.size === 0) {
      showProgressPanel.value = false
    }
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
    toggleMinimized,
    hidePanel,
    showPanel,
    isCancelling,
    setCancelling
  }
})
