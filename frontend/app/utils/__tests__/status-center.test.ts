import { describe, expect, it } from 'vitest'
import type { ActiveUpload } from '@/stores/upload.store'
import type { TrackedWorkflowRun, TrackedActionRun } from '@/stores/action-runs.store'
import type { BackgroundJob } from '@/stores/background-jobs.store'
import type { IiifImportJob } from '@/types/iiif-import'
import { buildStatusJobs, getJobCreatorId, getJobWorkspaceId, isOwnStatusJob, shouldAutoOpenStatusPopover } from '../status-center'

function createUpload(overrides: Partial<ActiveUpload> = {}): ActiveUpload {
  return {
    sessionId: 'upload-1',
    projectId: 'project-1',
    projectName: 'Upload Project',
    workspaceId: 'ws-1',
    status: 'UPLOADING',
    totalFiles: 2,
    uploadedFiles: 0,
    processedFiles: 0,
    failedFiles: 0,
    progressPercent: 35,
    processingCompletedItems: 0,
    processingTotalItems: 0,
    processingProgressPercent: 0,
    files: [
      {
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
    completedPageIds: [],
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
  it('retains workspace and creator context across all job types', () => {
    const jobs = buildStatusJobs(
      [createUpload({ workspaceId: 'upload-ws', createdByUserId: 'uploader' })],
      [createRun({ workspaceId: 'action-ws', createdByUserId: 'runner' })],
      [createBackgroundJob({ workspaceId: 'download-ws' })],
      [createIiifImport({ workspaceId: 'iiif-ws', createdByUserId: 'importer' })]
    )
    expect(Object.fromEntries(jobs.map(job => [job.kind, [getJobCreatorId(job), getJobWorkspaceId(job)]]))).toEqual({
      upload: ['uploader', 'upload-ws'],
      action: ['runner', 'action-ws'],
      background: [null, 'download-ws'],
      iiif: ['importer', 'iiif-ws']
    })
  })

  it('combines uploads and action runs into jobs', () => {
    const jobs = buildStatusJobs([createUpload()], [createRun()])

    expect(jobs).toHaveLength(2)
    expect(jobs[0]?.kind).toBe('action')
    expect(jobs[0]?.title).toBe('OCR Main')
    expect(jobs[1]?.kind).toBe('upload')
    expect(jobs[1]?.title).toBe('Upload Project')
  })

  it('describes training runs with their dataset and input count', () => {
    const jobs = buildStatusJobs([], [createRun({
      kind: 'TRAINING',
      projectId: null,
      projectLabel: null,
      datasetId: 'dataset-1',
      datasetLabel: 'Training Dataset',
      inputCount: 10,
      pageCount: 0,
      pageIds: [],
      projectName: 'Training Dataset'
    })])

    expect(jobs[0]?.kind).toBe('action')
    expect(jobs[0]?.subtitle).toBe('Training Dataset · 10 inputs')
    expect(jobs[0]?.icon).toBe('i-lucide-brain-circuit')
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

  it('shows conversion work progress for processing uploads', () => {
    const uploadJobs = buildStatusJobs([createUpload({
      status: 'PROCESSING',
      processingCompletedItems: 3,
      processingTotalItems: 10,
      processingProgressPercent: 30,
      processingCurrentFileName: 'document.pdf'
    })], [], [], [])

    expect(uploadJobs[0]?.progress).toBe(30)
    expect(uploadJobs[0]?.progressLabel).toBe('30%')
    expect(uploadJobs[0]?.subtitle).toContain('3 / 10 conversion steps processed')
    expect(uploadJobs[0]?.subtitle).toContain('document.pdf')
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

  it('identifies ownership across uploads, action runs, IIIF imports, and local tasks', () => {
    const jobs = buildStatusJobs(
      [createUpload({ createdByUserId: 'me' }), createUpload({ sessionId: 'other-upload', createdByUserId: 'other' })],
      [createRun({ createdByUserId: 'me' }), createRun({ id: 'other-run', createdByUserId: 'other' })],
      [createBackgroundJob()],
      [createIiifImport({ createdByUserId: 'me' }), createIiifImport({ id: 'other-import', createdByUserId: 'other' })]
    )
    expect(jobs.filter(job => isOwnStatusJob(job, 'me')).map(job => job.id).sort()).toEqual([
      'background-1', 'iiif-1', 'run-1', 'upload-1'
    ])
    expect(jobs.filter(job => isOwnStatusJob(job, undefined)).map(job => job.id)).toEqual(['background-1'])
  })

  it('includes locally scheduled uploads before the server assigns their creator', () => {
    const jobs = buildStatusJobs([createUpload({ locallyScheduled: true })], [])
    expect(isOwnStatusJob(jobs[0]!, 'me')).toBe(true)
    expect(isOwnStatusJob(buildStatusJobs([createUpload()], [])[0]!, 'me')).toBe(false)
  })

  it('auto-opens only when active job count increases', () => {
    expect(shouldAutoOpenStatusPopover(0, 1)).toBe(true)
    expect(shouldAutoOpenStatusPopover(1, 2)).toBe(true)
    expect(shouldAutoOpenStatusPopover(2, 2)).toBe(false)
    expect(shouldAutoOpenStatusPopover(2, 1)).toBe(false)
  })
})

describe('workflow jobs', () => {
  const workflow: TrackedWorkflowRun = {
    id: 'workflow-run', workflowId: 'workflow', workflowName: 'Page OCR', workspaceId: 'ws-1', projectId: 'project-1',
    projectName: 'Action Project', createdByUserId: 'me', stepNames: ['Layout', 'OCR', 'Export'], status: 'RUNNING',
    steps: [0, 1, 2].map(() => ({ processorDefinitionId: 'processor-1', parameters: {}, imageVariantSelection: null })),
    pageIds: ['page-1'], stepRunIds: ['layout', 'ocr'], canCancel: true, errorMessage: null,
    created: '2026-05-29T10:01:00.000Z', completedAt: null
  }

  it('shows one multi-step job and preserves unrelated Actions', () => {
    const jobs = buildStatusJobs([], [
      createRun({ id: 'layout', status: 'COMPLETED', progressPercent: 100 }),
      createRun({ id: 'ocr', progressPercent: 50 }), createRun({ id: 'unrelated' })
    ], [], [], [workflow])
    expect(jobs).toHaveLength(2)
    const job = jobs.find(job => job.kind === 'workflow')!
    expect(job.title).toBe('Page OCR')
    expect(job.progress).toBe(50)
    expect(job.progressLabel).toBe('1 / 3 steps · 50%')
    if (job.kind !== 'workflow') throw new Error('Expected workflow job')
    expect(job.steps.map(step => step.status)).toEqual(['COMPLETED', 'RUNNING', 'WAITING'])
    expect(isOwnStatusJob(job, 'me')).toBe(true)
    expect(getJobWorkspaceId(job)).toBe('ws-1')
  })

  it('keeps a stable parent job between steps and after failure or completion', () => {
    for (const status of ['RUNNING', 'FAILED', 'CANCELLED', 'COMPLETED'] as const) {
      const [job] = buildStatusJobs([], [], [], [], [{ ...workflow, status }])
      expect(job?.id).toBe('workflow-run')
      expect(job?.terminal).toBe(status !== 'RUNNING')
      if (status === 'COMPLETED') expect(job?.progress).toBe(100)
      if (job?.kind === 'workflow' && status === 'FAILED') {
        expect(job.steps.map(step => step.status)).toEqual(['COMPLETED', 'FAILED', 'NOT_RUN'])
      }
    }
  })

  it('uses the terminal workflow status when the final child update has not arrived yet', () => {
    const [job] = buildStatusJobs([], [createRun({ id: 'ocr', status: 'RUNNING', progressPercent: 50 })], [], [], [{ ...workflow, status: 'CANCELLED' }])
    if (job?.kind !== 'workflow') throw new Error('Expected workflow job')
    expect(job.steps.map(step => step.status)).toEqual(['COMPLETED', 'CANCELLED', 'NOT_RUN'])
    expect(job.progress).toBe(33)
  })

  it('does not resurface children of dismissed workflows as individual jobs', () => {
    expect(buildStatusJobs([], [createRun({ id: 'layout' })], [], [], [], new Set(['layout']))).toEqual([])
  })
})
