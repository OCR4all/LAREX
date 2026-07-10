/* eslint-disable @typescript-eslint/no-explicit-any */
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { IiifImportJob } from '@/types/iiif-import'

function createJob(overrides: Partial<IiifImportJob> = {}): IiifImportJob {
  return {
    id: 'iiif-1',
    projectId: 'project-1',
    projectName: 'Project One',
    workspaceId: 'workspace-1',
    sourceType: 'MANIFEST_URL',
    sourceReference: 'https://example.org/manifest',
    status: 'PENDING',
    queuePosition: 1,
    totalCanvases: 3,
    processedCanvases: 0,
    skippedCanvases: 0,
    failedCanvases: 0,
    progressPercent: 0,
    estimatedStorageBytes: 30 * 1024 * 1024,
    manifest: null,
    warnings: [],
    results: [],
    errorMessage: null,
    created: '2026-07-06T10:00:00.000Z',
    updated: '2026-07-06T10:00:00.000Z',
    completedAt: null,
    ...overrides
  }
}

async function createStore(fetchMock: ReturnType<typeof vi.fn>) {
  const vue = await import('vue')
  const pinia = await import('pinia')
  ;(globalThis as any).ref = vue.ref
  ;(globalThis as any).computed = vue.computed
  ;(globalThis as any).$fetch = fetchMock
  pinia.setActivePinia(pinia.createPinia())
  const { useIiifImportJobsStore } = await import('../iiif-import-jobs.store')
  return useIiifImportJobsStore()
}

describe('iiif-import-jobs.store', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.clearAllMocks()
  })

  it('loads persisted workspace jobs and removes jobs absent from a later refresh', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce([createJob()])
      .mockResolvedValueOnce([])
    const store = await createStore(fetchMock)

    await store.refreshWorkspaceJobs('workspace-1')
    expect(store.jobsArray).toHaveLength(1)
    expect(store.hasActiveJobs).toBe(true)

    await store.refreshWorkspaceJobs('workspace-1')
    expect(store.jobsArray).toHaveLength(0)
  })

  it('cancels through the persisted job endpoint and stores the terminal response', async () => {
    const cancelled = createJob({
      status: 'CANCELLED',
      queuePosition: null,
      completedAt: '2026-07-06T10:01:00.000Z'
    })
    const fetchMock = vi.fn().mockResolvedValue(cancelled)
    const store = await createStore(fetchMock)
    const pending = createJob()
    store.upsertJob(pending)

    await store.cancelJob(pending)

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/workspaces/workspace-1/projects/project-1/iiif-import/jobs/iiif-1',
      { method: 'DELETE' }
    )
    expect(store.jobsArray[0]?.status).toBe('CANCELLED')
    expect(store.isCancelling('iiif-1')).toBe(false)
  })

  it('persists dismissal and removes a completed job from the panel', async () => {
    const fetchMock = vi.fn().mockResolvedValue(undefined)
    const store = await createStore(fetchMock)
    const completed = createJob({
      status: 'COMPLETED',
      queuePosition: null,
      processedCanvases: 3,
      progressPercent: 100,
      completedAt: '2026-07-06T10:01:00.000Z'
    })
    store.upsertJob(completed)

    await store.dismissJob(completed)

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/workspaces/workspace-1/iiif-import/jobs/iiif-1/dismiss',
      { method: 'POST' }
    )
    expect(store.jobsArray).toHaveLength(0)
  })

  it('invalidates the page cache and emits a terminal event when an import creates pages', async () => {
    const fetchMock = vi.fn().mockResolvedValue(undefined)
    const store = await createStore(fetchMock)
    store.upsertJob(createJob())

    store.upsertJob(createJob({
      status: 'COMPLETED',
      queuePosition: null,
      processedCanvases: 3,
      progressPercent: 100,
      updated: '2026-07-09T10:01:00.000Z',
      completedAt: '2026-07-09T10:01:00.000Z'
    }))

    expect(store.terminalEvents).toHaveLength(1)
    expect(store.terminalEvents[0]?.job.projectId).toBe('project-1')
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/projects/project-1/pages/invalidate-cache',
      { method: 'POST' }
    )
  })
})
