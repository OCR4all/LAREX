import type { BackgroundJob } from '@/stores/background-jobs.store'

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

export function useBackgroundDownloads() {
  const backgroundJobsStore = useBackgroundJobsStore()

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

  async function downloadBlobResponse(response: Response, fallbackName: string, controls?: BackgroundJobControls) {
    const fileName = getResponseFileName(response, fallbackName)
    controls?.update({
      subtitle: fileName,
      statusLabel: 'Downloading',
      progressPercent: 0,
      detail: 'Starting download'
    })
    const blob = await readResponseBlob(response, controls)
    triggerBlobDownload(blob, fileName)
  }

  async function downloadBlob(blob: Blob, fileName: string, controls?: BackgroundJobControls) {
    controls?.update({
      subtitle: fileName,
      statusLabel: 'Downloading',
      progressPercent: 100,
      detail: formatBytes(blob.size)
    })
    triggerBlobDownload(blob, fileName)
  }

  return {
    downloadBlob,
    downloadBlobResponse,
    runBackgroundJob
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
  const match = contentDisposition?.match(/filename\*?=(?:UTF-8''|"?)([^";]+)/i)
  return match ? decodeURIComponent(match[1]!) : fallbackName
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
