import { defineStore } from 'pinia'
import type { UploadSession, UploadFile } from '~/composables/use-chunked-upload'

export interface ActiveUpload {
  sessionId: string
  projectId: string
  projectName: string
  workspaceId: string
  status: UploadSession['status']
  totalFiles: number
  processedFiles: number
  failedFiles: number
  progressPercent: number
  files: UploadFile[]
  created: string
  error?: string
}

type CancelUploadHandler = (sessionId: string, upload: ActiveUpload) => Promise<boolean> | boolean

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
  const cancelUploadHandler = ref<CancelUploadHandler | null>(null)

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
    files: UploadFile[]
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

  function updateFileProgress(sessionId: string, fileId: string, updates: Partial<UploadFile>) {
    const upload = activeUploads.value.get(sessionId)
    if (upload) {
      const fileIndex = upload.files.findIndex(f => f.id === fileId || f.fileName === updates.fileName)
      if (fileIndex !== -1) {
        const updatedFiles = [...upload.files]
        const currentFile = updatedFiles[fileIndex]
        updatedFiles[fileIndex] = {
          ...currentFile,
          ...updates,
          // Keep a valid File instance even when updates are partial.
          file: currentFile.file
        }

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

  function setCancelUploadHandler(handler: CancelUploadHandler) {
    cancelUploadHandler.value = handler
  }

  function clearCancelUploadHandler(handler?: CancelUploadHandler) {
    if (!handler || cancelUploadHandler.value === handler) {
      cancelUploadHandler.value = null
    }
  }

  async function cancelUpload(sessionId: string): Promise<void> {
    const upload = activeUploads.value.get(sessionId)
    if (!upload) return
    if (!isActiveStatus(upload.status)) return
    if (isCancelling(sessionId)) return

    setCancelling(sessionId, true)
    try {
      let handled = false
      if (cancelUploadHandler.value) {
        handled = await cancelUploadHandler.value(sessionId, upload)
      }

      if (!handled) {
        await $fetch(`/api/workspaces/${upload.workspaceId}/projects/${upload.projectId}/upload-sessions/${sessionId}`, {
          method: 'DELETE'
        })
      }

      completeUpload(sessionId, 'CANCELLED')
    } catch (error) {
      setCancelling(sessionId, false)
      throw error
    }
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
    updateUploadProgress,
    updateFileProgress,
    completeUpload,
    removeUpload,
    clearCompletedUploads,
    toggleMinimized,
    hidePanel,
    showPanel,
    isCancelling,
    cancelUpload,
    setCancelUploadHandler,
    clearCancelUploadHandler
  }
})
