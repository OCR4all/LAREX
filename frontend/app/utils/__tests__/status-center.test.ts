import { describe, expect, it } from 'vitest'
import type { ActiveUpload } from '@/stores/upload.store'
import type { TrackedActionRun } from '@/stores/action-runs.store'
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
    pageIds: ['page-1', 'page-2'],
    targetSelection: { type: 'PAGE', pages: [] },
    status: 'RUNNING',
    lockMode: 'PAGES',
    progressPercent: 62,
    statusMessage: null,
    errorMessage: null,
    cancelRequested: false,
    lastHeartbeatAt: null,
    created: '2026-05-29T10:01:00.000Z',
    updated: '2026-05-29T10:01:30.000Z',
    completedAt: null,
    projectName: 'Action Project',
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

  it('auto-opens only when active job count increases', () => {
    expect(shouldAutoOpenStatusPopover(0, 1)).toBe(true)
    expect(shouldAutoOpenStatusPopover(1, 2)).toBe(true)
    expect(shouldAutoOpenStatusPopover(2, 2)).toBe(false)
    expect(shouldAutoOpenStatusPopover(2, 1)).toBe(false)
  })
})
