import { describe, expect, it } from 'vitest'
import type { ActiveUpload } from '@/stores/upload.store'
import type { TrackedActionRun } from '@/stores/action-runs.store'
import type { BackgroundJob } from '@/stores/background-jobs.store'
import type { IiifImportJob } from '@/types/iiif-import'
import { buildStatusJobs, shouldAutoOpenStatusPopover } from '../status-center'

function createUpload(overrides: Partial<ActiveUpload> = {}): ActiveUpload {
  return {
    sessionId: 'upload-1',
    projectId: 'project-1',
    projectName: 'Upload Project',
    workspaceId: 'ws-1',
    status: 'UPLOADING',
    totalFiles: 2,
    processedFiles: 0,
    failedFiles: 0,
    progressPercent: 35,
    files: [
      {
        source: 'recovered',
        fileName: 'page-001.png',
        fileSize: 1024,
        mimeType: 'image/png',
        baseName: 'page-001',
        variant: 'default',
        status: 'uploading',
        progress: 35,
        chunksReceived: 7,
        totalChunks: 20
      }
    ],
    created: '2026-05-29T10:00:00.000Z',
    ...overrides
  }
}

function createRun(overrides: Partial<TrackedActionRun> = {}): TrackedActionRun {
  return {
    id: 'run-1',
    processorDefinitionId: 'processor-1',
    processorKey: 'ocr-main',
    processorName: 'OCR Main',
    workspaceId: 'ws-1',
    projectId: 'project-1',
    projectLabel: 'Action Project',
    pageCount: 2,
    pageIds: ['page-1', 'page-2'],
    targetSelection: { type: 'PAGE', pages: [] },
    status: 'RUNNING',
    lockMode: 'PAGES',
    progressPercent: 62,
    queuePosition: null,
    statusMessage: null,
    errorMessage: null,
    canCancel: true,
    cancelRequested: false,
    lastHeartbeatAt: null,
    created: '2026-05-29T10:01:00.000Z',
    updated: '2026-05-29T10:01:30.000Z',
    completedAt: null,
    projectName: 'Action Project',
    ...overrides
  }
}

function createBackgroundJob(overrides: Partial<BackgroundJob> = {}): BackgroundJob {
  return {
    id: 'background-1',
    title: 'Exporting project output',
    subtitle: 'Demo Project',
    status: 'RUNNING',
    statusLabel: 'Generating',
    progressPercent: null,
    icon: 'i-lucide-file-output',
    created: '2026-05-29T10:02:00.000Z',
    updated: '2026-05-29T10:02:10.000Z',
    ...overrides
  }
}

function createIiifImport(overrides: Partial<IiifImportJob> = {}): IiifImportJob {
  return {
    id: 'iiif-1',
    projectId: 'project-1',
    projectName: 'IIIF Project',
    workspaceId: 'ws-1',
    sourceType: 'MANIFEST_URL',
    sourceReference: 'https://example.org/manifest',
    status: 'PENDING',
    queuePosition: 2,
    totalCanvases: 12,
    processedCanvases: 0,
    skippedCanvases: 0,
    failedCanvases: 0,
    progressPercent: 0,
    estimatedStorageBytes: 120 * 1024 * 1024,
    manifest: null,
    warnings: [],
    results: [],
    errorMessage: null,
    created: '2026-05-29T10:03:00.000Z',
    updated: '2026-05-29T10:03:00.000Z',
    completedAt: null,
    ...overrides
  }
}

describe('status-center utils', () => {
  it('combines uploads and action runs into jobs', () => {
    const jobs = buildStatusJobs([createUpload()], [createRun()])

    expect(jobs).toHaveLength(2)
    expect(jobs[0]?.kind).toBe('action')
    expect(jobs[0]?.title).toBe('OCR Main')
    expect(jobs[1]?.kind).toBe('upload')
    expect(jobs[1]?.title).toBe('Upload Project')
  })

  it('sorts active jobs before terminal jobs regardless of timestamp', () => {
    const activeUpload = createUpload({ created: '2026-05-29T09:00:00.000Z', status: 'UPLOADING' })
    const completedRun = createRun({ status: 'COMPLETED', created: '2026-05-29T11:00:00.000Z', completedAt: '2026-05-29T11:00:10.000Z' })

    const jobs = buildStatusJobs([activeUpload], [completedRun])

    expect(jobs[0]?.kind).toBe('upload')
    expect(jobs[0]?.active).toBe(true)
    expect(jobs[1]?.kind).toBe('action')
    expect(jobs[1]?.terminal).toBe(true)
  })

  it('treats queued action runs as active warning jobs', () => {
    const jobs = buildStatusJobs([], [createRun({ status: 'QUEUED', progressPercent: 0 })])

    expect(jobs[0]?.kind).toBe('action')
    expect(jobs[0]?.active).toBe(true)
    expect(jobs[0]?.statusLabel).toBe('Queued')
    expect(jobs[0]?.color).toBe('warning')
  })

  it('includes active background jobs in the same queue', () => {
    const jobs = buildStatusJobs([], [], [createBackgroundJob()])

    expect(jobs).toHaveLength(1)
    expect(jobs[0]?.kind).toBe('background')
    expect(jobs[0]?.active).toBe(true)
    expect(jobs[0]?.statusLabel).toBe('Generating')
    expect(jobs[0]?.progress).toBeNull()
  })

  it('treats failed background jobs as terminal error jobs', () => {
    const jobs = buildStatusJobs([], [], [createBackgroundJob({ status: 'FAILED', statusLabel: 'Failed', error: 'Export failed' })])

    expect(jobs[0]?.kind).toBe('background')
    expect(jobs[0]?.terminal).toBe(true)
    expect(jobs[0]?.color).toBe('error')
  })

  it('includes queued IIIF imports with their persisted queue state', () => {
    const jobs = buildStatusJobs([], [], [], [createIiifImport()])

    expect(jobs).toHaveLength(1)
    expect(jobs[0]?.kind).toBe('iiif')
    expect(jobs[0]?.title).toBe('IIIF import')
    expect(jobs[0]?.subtitle).toBe('IIIF Project · 12 canvases')
    expect(jobs[0]?.statusLabel).toBe('Queued')
    expect(jobs[0]?.progress).toBeNull()
    expect(jobs[0]?.color).toBe('warning')
  })

  it('treats completed IIIF imports as terminal jobs', () => {
    const jobs = buildStatusJobs([], [], [], [createIiifImport({
      status: 'COMPLETED',
      queuePosition: null,
      processedCanvases: 12,
      progressPercent: 100,
      completedAt: '2026-05-29T10:04:00.000Z'
    })])

    expect(jobs[0]?.kind).toBe('iiif')
    expect(jobs[0]?.terminal).toBe(true)
    expect(jobs[0]?.statusLabel).toBe('Completed')
    expect(jobs[0]?.color).toBe('success')
  })

  it('auto-opens only when active job count increases', () => {
    expect(shouldAutoOpenStatusPopover(0, 1)).toBe(true)
    expect(shouldAutoOpenStatusPopover(1, 2)).toBe(true)
    expect(shouldAutoOpenStatusPopover(2, 2)).toBe(false)
    expect(shouldAutoOpenStatusPopover(2, 1)).toBe(false)
  })
})
