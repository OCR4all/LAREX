import type { UploadUiFile } from '@/stores/upload.store'
import { extractApiErrorMessage } from '@/utils/api-error'

type TrackedUploadStage = 'uploading' | 'processing'

type TrackedUploadFileInput = {
  file: File
  fileName?: string
}

type TrackedUploadBaseOptions = {
  title: string
  workspaceId: string
  projectId?: string
  files: TrackedUploadFileInput[]
  onProgress?: (progress: number | null) => void
  onStageChange?: (stage: TrackedUploadStage) => void
}

type TrackedFormUploadOptions = TrackedUploadBaseOptions & {
  url: string
  formData: FormData
}

type TrackedProcessingOptions<T> = TrackedUploadBaseOptions & {
  task: () => Promise<T>
}

let trackedUploadCounter = 0

function nextTrackedUploadId() {
  trackedUploadCounter += 1
  return `pending-upload-${Date.now()}-${trackedUploadCounter}`
}

function getFileBaseName(fileName: string) {
  const dotIndex = fileName.indexOf('.')
  return dotIndex === -1 ? fileName : fileName.substring(0, dotIndex)
}

function getFileVariant(fileName: string) {
  const dotIndex = fileName.indexOf('.')
  return dotIndex === -1 ? '' : fileName.substring(dotIndex + 1)
}

function toUploadUiFile(input: TrackedUploadFileInput, status: UploadUiFile['status']): UploadUiFile {
  const fileName = input.fileName || input.file.name
  return {
    source: 'local',
    file: input.file,
    id: fileName,
    fileName,
    fileSize: input.file.size,
    mimeType: input.file.type || 'application/octet-stream',
    baseName: getFileBaseName(fileName),
    variant: getFileVariant(fileName),
    status,
    progress: status === 'processing' ? 100 : 0,
    chunksReceived: status === 'processing' ? 100 : 0,
    totalChunks: 100
  }
}

export function useTrackedUpload() {
  const uploadStore = useUploadStore()
  const uploadSessionActions = useUploadSessionActions()

  function registerTrackedUpload(
    options: TrackedUploadBaseOptions,
    status: UploadUiFile['status'],
    uploadOptions: { cancelable: boolean, sessionStatus?: 'PENDING' | 'UPLOADING' | 'PROCESSING' } = { cancelable: false }
  ) {
    const sessionId = nextTrackedUploadId()
    uploadStore.registerUpload(
      sessionId,
      options.projectId || 'generic-upload',
      options.title,
      options.workspaceId,
      options.files.map(file => toUploadUiFile(file, status)),
      {
        status: uploadOptions.sessionStatus ?? (status === 'processing' ? 'PROCESSING' : 'PENDING'),
        progressPercent: status === 'processing' ? 100 : 0,
        cancelable: uploadOptions.cancelable
      }
    )
    return sessionId
  }

  async function uploadFormDataWithProgress<T>(options: TrackedFormUploadOptions): Promise<T> {
    const sessionId = registerTrackedUpload(options, 'pending', {
      cancelable: false,
      sessionStatus: 'UPLOADING'
    })

    return await new Promise<T>((resolve, reject) => {
      const xhr = new XMLHttpRequest()
      xhr.open('POST', options.url, true)
      xhr.responseType = 'json'

      uploadSessionActions.registerLocalAbortHandler(sessionId, () => {
        xhr.abort()
      })

      options.onStageChange?.('uploading')

      xhr.upload.onprogress = (event) => {
        if (!event.lengthComputable) {
          options.onProgress?.(null)
          return
        }

        const progress = Math.max(0, Math.min(100, Math.round((event.loaded / event.total) * 100)))
        options.onProgress?.(progress)
        uploadStore.updateUploadProgress(sessionId, {
          status: 'UPLOADING',
          progressPercent: progress
        })
        for (const file of options.files) {
          uploadStore.updateFileProgress(sessionId, file.fileName || file.file.name, {
            status: 'uploading',
            chunksReceived: progress,
            progress
          })
        }
      }

      xhr.upload.onloadend = () => {
        options.onStageChange?.('processing')
        options.onProgress?.(100)
        uploadStore.updateUploadProgress(sessionId, {
          status: 'PROCESSING',
          progressPercent: 100
        })
        for (const file of options.files) {
          uploadStore.updateFileProgress(sessionId, file.fileName || file.file.name, {
            status: 'processing',
            chunksReceived: 100,
            progress: 100
          })
        }
      }

      xhr.onerror = () => {
        const error = new Error('Upload failed')
        uploadStore.updateUploadProgress(sessionId, {
          failedFiles: options.files.length
        })
        uploadStore.completeUpload(sessionId, 'FAILED', error.message)
        uploadSessionActions.clearLocalAbortHandler(sessionId)
        reject(error)
      }

      xhr.onabort = () => {
        uploadStore.completeUpload(sessionId, 'CANCELLED')
        uploadSessionActions.clearLocalAbortHandler(sessionId)
        reject(new Error('Upload cancelled'))
      }

      xhr.onload = () => {
        uploadSessionActions.clearLocalAbortHandler(sessionId)
        const response = xhr.response
        if (xhr.status >= 200 && xhr.status < 300) {
          uploadStore.updateUploadProgress(sessionId, {
            processedFiles: options.files.length,
            progressPercent: 100
          })
          for (const file of options.files) {
            uploadStore.updateFileProgress(sessionId, file.fileName || file.file.name, {
              status: 'completed',
              chunksReceived: 100,
              progress: 100
            })
          }
          uploadStore.completeUpload(sessionId, 'COMPLETED')
          resolve(response as T)
          return
        }

        const message = extractApiErrorMessage(
          { data: response, statusCode: xhr.status },
          xhr.statusText || `Upload failed (${xhr.status})`
        )
        uploadStore.updateUploadProgress(sessionId, {
          failedFiles: options.files.length
        })
        for (const file of options.files) {
          uploadStore.updateFileProgress(sessionId, file.fileName || file.file.name, {
            status: 'failed',
            error: message
          })
        }
        uploadStore.completeUpload(sessionId, 'FAILED', message)
        reject(new Error(message || `Upload failed (${xhr.status})`))
      }

      xhr.send(options.formData)
    })
  }

  async function runTrackedProcessing<T>(options: TrackedProcessingOptions<T>): Promise<T> {
    const sessionId = registerTrackedUpload(options, 'processing', { cancelable: false })
    options.onStageChange?.('processing')
    options.onProgress?.(100)

    try {
      const result = await options.task()
      uploadStore.updateUploadProgress(sessionId, {
        processedFiles: options.files.length,
        progressPercent: 100
      })
      for (const file of options.files) {
        uploadStore.updateFileProgress(sessionId, file.fileName || file.file.name, {
          status: 'completed',
          chunksReceived: 100,
          progress: 100
        })
      }
      uploadStore.completeUpload(sessionId, 'COMPLETED')
      return result
    } catch (error) {
      const message = extractApiErrorMessage(error, 'Import failed')
      uploadStore.updateUploadProgress(sessionId, {
        failedFiles: options.files.length
      })
      for (const file of options.files) {
        uploadStore.updateFileProgress(sessionId, file.fileName || file.file.name, {
          status: 'failed',
          error: message
        })
      }
      uploadStore.completeUpload(sessionId, 'FAILED', message)
      throw error
    }
  }

  return {
    uploadFormDataWithProgress,
    runTrackedProcessing
  }
}
