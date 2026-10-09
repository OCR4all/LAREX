import { afterEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick, reactive, ref } from 'vue'
import type { TrackedActionRun } from '@/stores/action-runs.store'
import { useStatusCenter } from '../use-status-center'

function setup() {
  const runs = reactive<TrackedActionRun[]>([])
  const states = new Map<string, ReturnType<typeof ref>>()
  vi.stubGlobal('useState', (key: string, init: () => unknown) => {
    if (!states.has(key)) states.set(key, ref(init()))
    return states.get(key)
  })
  vi.stubGlobal('useUserSession', () => ({ user: ref({ id: 'me' }) }))
  vi.stubGlobal('useUploadStore', () => ({ uploadsArray: [] }))
  vi.stubGlobal('useActionRunsStore', () => ({ runsArray: runs }))
  vi.stubGlobal('useBackgroundJobsStore', () => ({ jobsArray: [] }))
  vi.stubGlobal('useIiifImportJobsStore', () => ({ jobsArray: [] }))
  vi.stubGlobal('useWorkspaceStore', () => ({}))
  vi.stubGlobal('useRealtimeSocket', () => ({}))
  vi.stubGlobal('useStatusIssues', () => ({ issues: ref([]), hasIssues: ref(false) }))
  vi.stubGlobal('onMounted', () => {})
  vi.stubGlobal('onBeforeUnmount', () => {})
  const scope = effectScope()
  const center = scope.run(useStatusCenter)!
  return { runs, center, stop: () => scope.stop() }
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

function run(id: string, createdByUserId: string) {
  return createRun({ id, createdByUserId, status: 'QUEUED' })
}

afterEach(() => vi.unstubAllGlobals())

describe('status center auto-open', () => {
  it('keeps other users’ jobs in the badge without opening the queue', async () => {
    const { runs, center, stop } = setup()
    try {
      runs.push(run('other-run', 'other'))
      await nextTick()
      expect(center.activeJobs.value).toHaveLength(1)
      expect(center.isOverlayOpen.value).toBe(false)
      runs.push(run('my-run', 'me'))
      await nextTick()
      expect(center.isOverlayOpen.value).toBe(true)
    } finally { stop() }
  })

  it('filters jobs without changing the global badge and stays closed on progress updates', async () => {
    const { runs, center, stop } = setup()
    try {
      runs.push(run('my-run', 'me'), run('other-run', 'other'))
      await nextTick()
      expect(center.jobScope.value).toBe('mine')
      expect(center.filteredJobs.value.map(job => job.id)).toEqual(['my-run'])
      expect(center.activeJobs.value).toHaveLength(2)
      center.jobScope.value = 'all'
      expect(center.filteredJobs.value).toHaveLength(2)
      center.closeOverlay()
      runs[0]!.progressPercent = 75
      runs[1]!.status = 'RUNNING'
      await nextTick()
      expect(center.isOverlayOpen.value).toBe(false)
      runs.push(run('another-own-run', 'me'))
      await nextTick()
      expect(center.isOverlayOpen.value).toBe(true)
      expect(center.jobScope.value).toBe('mine')
    } finally { stop() }
  })

  it('opens for an own job even when another job finishes in the same update', async () => {
    const { runs, center, stop } = setup()
    try {
      runs.push(run('other-run', 'other'))
      await nextTick()
      center.closeOverlay()
      runs.splice(0, 1, run('my-run', 'me'))
      await nextTick()
      expect(center.isOverlayOpen.value).toBe(true)
    } finally { stop() }
  })
})
