import type { BackgroundJob } from '@/stores/background-jobs.store'
import { sanitizeDownloadFileName } from '@/utils/download-file-names'

type BackgroundJobUpdate = Partial<Pick<BackgroundJob, 'subtitle' | 'statusLabel' | 'progressPercent' | 'icon' | 'detail'>>

export type BackgroundJobControls = {
  update: (updates: BackgroundJobUpdate) => void
}

type RunBackgroundJobOptions<T> = {
  title: string
  subtitle?: string
  statusLabel?: string
  completedLabel?: string
  icon?: string
  retryable?: boolean
  task: (controls: BackgroundJobControls) => Promise<T>
}

type SaveFilePicker = (options?: { suggestedName?: string }) => Promise<SaveFileHandleLike>

type SaveFileHandleLike = {
  createWritable: () => Promise<WritableFileLike>
}

type WritableFileLike = {
  write: (data: Blob | ArrayBuffer | Uint8Array) => Promise<void>
  close: () => Promise<void>
  abort?: (reason?: unknown) => Promise<void>
}

export type PreparedDownloadTarget = {
  kind: 'browser' | 'file-system'
  saveBlob: (blob: Blob, fileName: string, controls?: BackgroundJobControls) => Promise<void>
  saveResponse: (response: Response, fallbackName: string, controls?: BackgroundJobControls) => Promise<void>
}

export function useBackgroundDownloads() {
  const backgroundJobsStore = useBackgroundJobsStore()

  async function prepareDownload(suggestedName: string): Promise<PreparedDownloadTarget | null> {
    return prepareDownloadTarget(suggestedName)
  }

  async function runBackgroundJob<T>(options: RunBackgroundJobOptions<T>): Promise<T> {
    const jobId = backgroundJobsStore.startJob({
      title: options.title,
      subtitle: options.subtitle,
      statusLabel: options.statusLabel ?? 'Working',
      icon: options.icon
    })

    const controls: BackgroundJobControls = {
      update: updates => backgroundJobsStore.updateJob(jobId, updates)
    }
    if (options.retryable !== false) {
      backgroundJobsStore.setRetryHandler(jobId, async () => {
        backgroundJobsStore.removeJob(jobId)
        await runBackgroundJob(options)
      })
    }

    try {
      const result = await options.task(controls)
      backgroundJobsStore.completeJob(jobId, {
        statusLabel: options.completedLabel ?? 'Completed',
        progressPercent: 100
      })
      return result
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : 'Background job failed'
      backgroundJobsStore.failJob(jobId, message)
      throw error
    }
  }

  async function downloadBlobResponse(
    response: Response,
    fallbackName: string,
    controls?: BackgroundJobControls,
    target?: PreparedDownloadTarget
  ) {
    if (target) {
      await target.saveResponse(response, fallbackName, controls)
      return
    }

    const fileName = getResponseFileName(response, fallbackName)
    updateDownloadStart(controls, fileName)
    const blob = await readResponseBlob(response, controls)
    triggerBlobDownload(blob, fileName)
  }

  async function downloadBlob(
    blob: Blob,
    fileName: string,
    controls?: BackgroundJobControls,
    target?: PreparedDownloadTarget
  ) {
    if (target) {
      await target.saveBlob(blob, fileName, controls)
      return
    }

    updateDownloadStart(controls, fileName)
    controls?.update({
      progressPercent: 100,
      detail: formatBytes(blob.size)
    })
    triggerBlobDownload(blob, fileName)
  }

  return {
    downloadBlob,
    downloadBlobResponse,
    prepareDownload,
    runBackgroundJob
  }
}

export async function prepareDownloadTarget(suggestedName: string): Promise<PreparedDownloadTarget | null> {
  const fileName = sanitizeDownloadFileName(suggestedName, 'download')
  const showSaveFilePicker = getSaveFilePicker()

  if (!showSaveFilePicker) {
    return createBrowserDownloadTarget()
  }

  try {
    const handle = await showSaveFilePicker({ suggestedName: fileName })
    return createFileSystemDownloadTarget(handle)
  } catch (error: unknown) {
    if (isDownloadPickerCancellation(error)) return null
    return createBrowserDownloadTarget()
  }
}

export async function readResponseBlob(response: Response, controls?: BackgroundJobControls): Promise<Blob> {
  const contentLength = Number(response.headers.get('content-length'))
  const totalBytes = Number.isFinite(contentLength) && contentLength > 0 ? contentLength : null
  if (!response.body || totalBytes === null) {
    const blob = await response.blob()
    controls?.update({
      progressPercent: 100,
      detail: formatBytes(blob.size)
    })
    return blob
  }

  const reader = response.body.getReader()
  const chunks: ArrayBuffer[] = []
  let receivedBytes = 0

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    if (!value) continue
    const chunk = new Uint8Array(value.byteLength)
    chunk.set(value)
    chunks.push(chunk.buffer)
    receivedBytes += value.byteLength
    controls?.update({
      progressPercent: Math.min(99, Math.round((receivedBytes / totalBytes) * 100)),
      detail: `${formatBytes(receivedBytes)} / ${formatBytes(totalBytes)}`
    })
  }

  controls?.update({
    progressPercent: 100,
    detail: `${formatBytes(receivedBytes)} / ${formatBytes(totalBytes)}`
  })
  return new Blob(chunks, {
    type: response.headers.get('content-type') || undefined
  })
}

export async function isCompleteZipBlob(blob: Blob): Promise<boolean> {
  const minimumEndRecordSize = 22
  const maximumCommentSize = 65_535
  if (blob.size < minimumEndRecordSize) return false

  const tailSize = Math.min(blob.size, minimumEndRecordSize + maximumCommentSize)
  const tail = new Uint8Array(await blob.slice(blob.size - tailSize).arrayBuffer())

  for (let index = tail.length - minimumEndRecordSize; index >= 0; index--) {
    const hasEndSignature = tail[index] === 0x50
      && tail[index + 1] === 0x4B
      && tail[index + 2] === 0x05
      && tail[index + 3] === 0x06
    if (!hasEndSignature) continue

    const commentLength = tail[index + 20]! | (tail[index + 21]! << 8)
    if (index + minimumEndRecordSize + commentLength === tail.length) return true
  }

  return false
}

function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.min(sizes.length - 1, Math.floor(Math.log(bytes) / Math.log(k)))
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(1))} ${sizes[i]}`
}

export function getResponseFileName(response: Response, fallbackName: string): string {
  const contentDisposition = response.headers.get('content-disposition')
  if (!contentDisposition) return sanitizeDownloadFileName(fallbackName, 'download')

  const encodedMatch = contentDisposition.match(/filename\*\s*=\s*(?:UTF-8''|"?)([^";]+)/i)
  if (encodedMatch?.[1]) {
    const decoded = decodeFileName(encodedMatch[1])
    if (decoded) return sanitizeDownloadFileName(decoded, fallbackName)
  }

  const plainMatch = contentDisposition.match(/filename\s*=\s*(?:"([^"]+)"|([^;]+))/i)
  const plainName = plainMatch?.[1] || plainMatch?.[2]
  return sanitizeDownloadFileName(plainName?.trim() || fallbackName, fallbackName)
}

export function triggerBlobDownload(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  document.body.appendChild(anchor)
  anchor.click()
  document.body.removeChild(anchor)
  URL.revokeObjectURL(url)
}

function getSaveFilePicker(): SaveFilePicker | null {
  if (typeof window === 'undefined' || window.isSecureContext === false) return null

  const candidate = (window as Window & { showSaveFilePicker?: unknown }).showSaveFilePicker
  return typeof candidate === 'function' ? candidate.bind(window) as SaveFilePicker : null
}

function isDownloadPickerCancellation(error: unknown): boolean {
  return typeof error === 'object'
    && error !== null
    && 'name' in error
    && (error as { name?: unknown }).name === 'AbortError'
}

function createBrowserDownloadTarget(): PreparedDownloadTarget {
  return {
    kind: 'browser',
    async saveBlob(blob, fileName, controls) {
      const normalizedFileName = sanitizeDownloadFileName(fileName, 'download')
      updateDownloadStart(controls, normalizedFileName)
      controls?.update({
        progressPercent: 100,
        detail: formatBytes(blob.size)
      })
      triggerBlobDownload(blob, normalizedFileName)
    },
    async saveResponse(response, fallbackName, controls) {
      const fileName = getResponseFileName(response, fallbackName)
      updateDownloadStart(controls, fileName)
      const blob = await readResponseBlob(response, controls)
      triggerBlobDownload(blob, fileName)
    }
  }
}

function createFileSystemDownloadTarget(handle: SaveFileHandleLike): PreparedDownloadTarget {
  return {
    kind: 'file-system',
    async saveBlob(blob, fileName, controls) {
      const normalizedFileName = sanitizeDownloadFileName(fileName, 'download')
      updateDownloadStart(controls, normalizedFileName)
      await writeBlobToFile(handle, blob, controls)
    },
    async saveResponse(response, fallbackName, controls) {
      const fileName = getResponseFileName(response, fallbackName)
      updateDownloadStart(controls, fileName)
      await writeResponseToFile(handle, response, controls)
    }
  }
}

async function writeBlobToFile(handle: SaveFileHandleLike, blob: Blob, controls?: BackgroundJobControls): Promise<void> {
  await withWritableFile(handle, async (writable) => {
    await writable.write(blob)
    controls?.update({
      progressPercent: 100,
      detail: formatBytes(blob.size)
    })
  })
}

async function writeResponseToFile(
  handle: SaveFileHandleLike,
  response: Response,
  controls?: BackgroundJobControls
): Promise<void> {
  await withWritableFile(handle, async (writable) => {
    const contentLength = Number(response.headers.get('content-length'))
    const totalBytes = Number.isFinite(contentLength) && contentLength > 0 ? contentLength : null

    if (!response.body) {
      const blob = await response.blob()
      await writable.write(blob)
      controls?.update({
        progressPercent: 100,
        detail: formatBytes(blob.size)
      })
      return
    }

    const reader = response.body.getReader()
    let receivedBytes = 0

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      if (!value) continue

      const chunk = new Uint8Array(value.byteLength)
      chunk.set(value)
      await writable.write(chunk)
      receivedBytes += value.byteLength
      controls?.update({
        progressPercent: totalBytes === null
          ? null
          : Math.min(99, Math.round((receivedBytes / totalBytes) * 100)),
        detail: totalBytes === null
          ? formatBytes(receivedBytes)
          : `${formatBytes(receivedBytes)} / ${formatBytes(totalBytes)}`
      })
    }

    controls?.update({
      progressPercent: 100,
      detail: totalBytes === null
        ? formatBytes(receivedBytes)
        : `${formatBytes(receivedBytes)} / ${formatBytes(totalBytes)}`
    })
  })
}

async function withWritableFile(
  handle: SaveFileHandleLike,
  write: (writable: WritableFileLike) => Promise<void>
): Promise<void> {
  const writable = await handle.createWritable()
  try {
    await write(writable)
    await writable.close()
  } catch (error: unknown) {
    if (writable.abort) {
      await writable.abort(error).catch(() => undefined)
    }
    throw error
  }
}

function updateDownloadStart(controls: BackgroundJobControls | undefined, fileName: string): void {
  controls?.update({
    subtitle: fileName,
    statusLabel: 'Downloading',
    progressPercent: 0,
    detail: 'Starting download'
  })
}

function decodeFileName(value: string): string | null {
  try {
    return decodeURIComponent(value)
  } catch {
    return null
  }
}
