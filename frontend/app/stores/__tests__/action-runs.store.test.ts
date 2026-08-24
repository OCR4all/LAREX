import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ActionRun } from '@/types/action'

function createRun(overrides: Partial<ActionRun> = {}): ActionRun {
  return {
    id: 'run-1',
    processorDefinitionId: 'processor-1',
    processorKey: 'processor',
    processorName: 'Processor',
    workspaceId: 'workspace-1',
    projectId: 'project-1',
    projectLabel: 'Project One',
    pageCount: 2,
    pageIds: ['page-1', 'page-2'],
    completedPageIds: [],
    targetSelection: { type: 'PAGE', pages: [] },
    status: 'RUNNING',
    lockMode: 'PAGES',
    progressPercent: 0,
    queuePosition: null,
    statusMessage: null,
    errorMessage: null,
    canCancel: true,
    cancelRequested: false,
    lastHeartbeatAt: null,
    created: '2026-07-16T10:00:00.000Z',
    updated: '2026-07-16T10:00:00.000Z',
    completedAt: null,
    ...overrides
  }
}

async function createStore(
  fetchMock: ReturnType<typeof vi.fn>,
  subscribe = vi.fn(),
  connectionStatus?: { value: string }
) {
  const vue = await import('vue')
  const pinia = await import('pinia')
  ;(globalThis as any).ref = vue.ref
  ;(globalThis as any).computed = vue.computed
  ;(globalThis as any).$fetch = fetchMock
  ;(globalThis as any).useRealtimeSocket = () => ({ subscribe, connectionStatus })
  pinia.setActivePinia(pinia.createPinia())
  const { useActionRunsStore } = await import('../action-runs.store')
  return useActionRunsStore()
}

describe('action-runs.store', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.clearAllMocks()
    vi.useRealTimers()
  })

  it('emits polling fallback page events and removes completed page locks', async () => {
    const store = await createStore(vi.fn())
    store.upsertRun(createRun())
    expect(store.getPageActionLockReason('project-1', 'page-1')).toContain('Processor')
    expect(store.isProjectActionRunning('project-1')).toBe(true)
    expect(store.isPageActionRunning('project-1', 'page-1')).toBe(true)

    store.upsertRun(createRun({ completedPageIds: ['page-1'], progressPercent: 50 }))

    expect(store.pageResultEvents).toHaveLength(1)
    expect(store.pageResultEvents[0]?.pageId).toBe('page-1')
    expect(store.getPageActionLockReason('project-1', 'page-1')).toBeNull()
    expect(store.getPageActionLockReason('project-1', 'page-2')).toContain('Processor')
    expect(store.isPageActionRunning('project-1', 'page-1')).toBe(false)
    expect(store.isPageActionRunning('project-1', 'page-2')).toBe(true)
    expect(store.isProjectActionRunning('project-1')).toBe(true)
  })

  it('retains project-wide locking after a page result', async () => {
    const store = await createStore(vi.fn())
    store.upsertRun(createRun({ lockMode: 'PROJECT', completedPageIds: ['page-1'] }))

    expect(store.getPageActionLockReason('project-1', 'page-1')).toContain('Processor')
  })

  it.each(['COMPLETED', 'FAILED', 'CANCELLED'] as const)(
    'emits a terminal reconciliation event when a run becomes %s',
    async (status) => {
      const store = await createStore(vi.fn())
      store.upsertRun(createRun())

      store.upsertRun(createRun({ status }))

      expect(store.terminalEvents).toHaveLength(1)
      expect(store.terminalEvents[0]?.run.status).toBe(status)
      expect(store.terminalEvents[0]?.run.pageIds).toEqual(['page-1', 'page-2'])
    }
  )

  it('coalesces realtime run refreshes for the same scope', async () => {
    vi.useFakeTimers()
    let listener: ((message: { type?: string, payload?: unknown }) => void) | undefined
    const subscribe = vi.fn((callback) => {
      listener = callback
      return () => undefined
    })
    const fetchMock = vi.fn().mockResolvedValue({ run: createRun() })
    const store = await createStore(fetchMock, subscribe)
    store.initializeRealtime()

    const payload = { runId: 'run-1', workspaceId: 'workspace-1', projectId: 'project-1' }
    listener?.({ type: 'ACTION_RUN_UPDATED', payload })
    listener?.({ type: 'ACTION_RUN_UPDATED', payload })
    await vi.advanceTimersByTimeAsync(50)

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock).toHaveBeenCalledWith('/api/workspaces/workspace-1/actions/projects/project-1/runs/run-1')
  })

  it('uses one visible-tab polling fallback for active run scopes', async () => {
    vi.useFakeTimers()
    const fetchMock = vi.fn().mockResolvedValue([])
    const store = await createStore(fetchMock)
    store.upsertRun(createRun())
    store.initializeRealtime()

    await vi.advanceTimersByTimeAsync(2500)

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/workspaces/workspace-1/actions/projects/project-1/runs'
    )
  })

  it('uses a slow audit while realtime is connected', async () => {
    vi.useFakeTimers()
    const fetchMock = vi.fn().mockResolvedValue([])
    const store = await createStore(fetchMock, vi.fn(), { value: 'connected' })
    store.upsertRun(createRun())
    store.initializeRealtime()

    await vi.advanceTimersByTimeAsync(59_999)
    expect(fetchMock).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(1)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('stops page processing immediately when the realtime result event arrives', async () => {
    let listener: ((message: { type?: string, payload?: unknown }) => void) | undefined
    const subscribe = vi.fn((callback) => {
      listener = callback
      return () => undefined
    })
    const store = await createStore(vi.fn().mockResolvedValue([]), subscribe)
    store.upsertRun(createRun())
    store.initializeRealtime()

    listener?.({
      type: 'ACTION_PAGE_RESULT_IMPORTED',
      payload: {
        runId: 'run-1',
        workspaceId: 'workspace-1',
        projectId: 'project-1',
        pageId: 'page-1',
        resultTypes: ['XML']
      }
    })

    expect(store.isPageActionRunning('project-1', 'page-1')).toBe(false)
    expect(store.getPageActionLockReason('project-1', 'page-1')).toBeNull()
  })
})
