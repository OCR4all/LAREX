import type { ActiveUpload } from '@/stores/upload.store'
import type { TrackedActionRun } from '@/stores/action-runs.store'

export type JobStatusColor = 'primary' | 'success' | 'error' | 'warning' | 'neutral'

export type StatusJob
  = | {
    kind: 'upload'
    id: string
    title: string
    subtitle: string
    status: string
    statusLabel: string
    progress: number | null
    progressLabel: string
    color: JobStatusColor
    icon: string
    active: boolean
    terminal: boolean
    upload: ActiveUpload
  }
  | {
    kind: 'action'
    id: string
    title: string
    subtitle: string
    status: string
    statusLabel: string
    progress: number | null
    progressLabel: string
    color: JobStatusColor
    icon: string
    active: boolean
    terminal: boolean
    run: TrackedActionRun
  }

const uploadStatusLabels: Record<string, string> = {
  PENDING: 'Pending',
  UPLOADING: 'Uploading',
  PROCESSING: 'Processing',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
  CANCELLED: 'Cancelled'
}

const actionStatusLabels: Record<string, string> = {
  PENDING: 'Pending',
  DISPATCHING: 'Dispatching',
  RUNNING: 'Running',
  IMPORTING_RESULTS: 'Importing',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
  CANCEL_REQUESTED: 'Cancelling',
  CANCELLED: 'Cancelled'
}

export function isActiveUpload(status: ActiveUpload['status']): boolean {
  return status === 'PENDING' || status === 'UPLOADING' || status === 'PROCESSING'
}

export function isTerminalUpload(status: ActiveUpload['status']): boolean {
  return status === 'COMPLETED' || status === 'FAILED' || status === 'CANCELLED'
}

export function isActiveAction(status: TrackedActionRun['status']): boolean {
  return status === 'PENDING'
    || status === 'DISPATCHING'
    || status === 'RUNNING'
    || status === 'IMPORTING_RESULTS'
    || status === 'CANCEL_REQUESTED'
}

export function isTerminalAction(status: TrackedActionRun['status']): boolean {
  return status === 'COMPLETED' || status === 'FAILED' || status === 'CANCELLED'
}

export function getUploadStatusColor(status: ActiveUpload['status']): JobStatusColor {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED' || status === 'CANCELLED') return 'error'
  return 'primary'
}

export function getActionStatusColor(status: TrackedActionRun['status']): JobStatusColor {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED' || status === 'CANCELLED') return 'error'
  if (status === 'CANCEL_REQUESTED') return 'warning'
  return 'primary'
}

export function getUploadStatusLabel(upload: ActiveUpload): string {
  if (upload.status === 'PROCESSING') return 'Finalizing'
  return uploadStatusLabels[upload.status] || upload.status
}

export function getUploadedFileCount(upload: ActiveUpload): number {
  return upload.files.filter((file) => {
    if (file.totalChunks > 0 && file.chunksReceived >= file.totalChunks) return true
    return file.status === 'uploaded'
      || file.status === 'processing'
      || file.status === 'completed'
      || file.status === 'failed'
      || file.status === 'conflict'
      || file.status === 'skipped'
  }).length
}

export function getSettledFileCount(upload: ActiveUpload): number {
  const settledByFileState = upload.files.filter(file =>
    file.status === 'completed'
    || file.status === 'failed'
    || file.status === 'conflict'
    || file.status === 'skipped'
  ).length
  const settledBySessionState = upload.processedFiles + upload.failedFiles
  const settledCount = Math.max(settledByFileState, settledBySessionState)
  return upload.status === 'COMPLETED' ? Math.max(settledCount, upload.totalFiles) : settledCount
}

export function getUploadProgressSummary(upload: ActiveUpload): string {
  if (upload.status === 'PENDING' || upload.status === 'UPLOADING') {
    return `${getUploadedFileCount(upload)} / ${upload.totalFiles} files uploaded`
  }
  return `${getSettledFileCount(upload)} / ${upload.totalFiles} files processed`
}

export function getJobKey(job: StatusJob): string {
  return `${job.kind}:${job.id}`
}

export function getJobTimestamp(job: StatusJob): number {
  return Date.parse(job.kind === 'upload' ? job.upload.created : job.run.created)
}

export function buildStatusJobs(uploads: ActiveUpload[], runs: TrackedActionRun[]): StatusJob[] {
  const uploadJobs: StatusJob[] = uploads.map(upload => ({
    kind: 'upload',
    id: upload.sessionId,
    title: upload.projectName,
    subtitle: getUploadProgressSummary(upload),
    status: upload.status,
    statusLabel: getUploadStatusLabel(upload),
    progress: upload.status === 'PROCESSING' ? null : upload.progressPercent,
    progressLabel: upload.status === 'PROCESSING' ? 'Finalizing' : `${upload.progressPercent}%`,
    color: getUploadStatusColor(upload.status),
    icon: upload.status === 'PROCESSING' ? 'i-lucide-loader' : 'i-lucide-upload-cloud',
    active: isActiveUpload(upload.status),
    terminal: isTerminalUpload(upload.status),
    upload
  }))

  const actionJobs: StatusJob[] = runs.map(run => ({
    kind: 'action',
    id: run.id,
    title: run.processorName,
    subtitle: `${run.projectName} · ${run.pageIds.length} page${run.pageIds.length === 1 ? '' : 's'}`,
    status: run.status,
    statusLabel: actionStatusLabels[run.status] || run.status,
    progress: run.progressPercent,
    progressLabel: `${run.progressPercent}%`,
    color: getActionStatusColor(run.status),
    icon: 'i-lucide-circle-play',
    active: isActiveAction(run.status),
    terminal: isTerminalAction(run.status),
    run
  }))

  return [...uploadJobs, ...actionJobs].sort((left, right) => {
    if (left.active !== right.active) return left.active ? -1 : 1
    return getJobTimestamp(right) - getJobTimestamp(left)
  })
}

export function shouldAutoOpenStatusPopover(previousActiveCount: number, nextActiveCount: number): boolean {
  return nextActiveCount > previousActiveCount
}
