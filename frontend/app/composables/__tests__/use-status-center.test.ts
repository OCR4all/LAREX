import { afterEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick, reactive, ref } from 'vue'
import type { TrackedActionRun } from '@/stores/action-runs.store'
import { useStatusCenter } from '../use-status-center'
import { useEditorPreferences } from '../use-editor-preferences'

function setup() {
  const runs = reactive<TrackedActionRun[]>([])
  const request = vi.fn(async (_url: string, options?: { body: unknown }) => options?.body ?? { autoOpenJobsPanel: null })
  vi.stubGlobal('$fetch', request)
  vi.stubGlobal('useEditorPreferences', useEditorPreferences)
  const states = new Map<string, ReturnType<typeof ref>>()
  vi.stubGlobal('useState', (key: string, init: () => unknown) => {
    if (!states.has(key)) states.set(key, ref(init()))
    return states.get(key)
  })
  vi.stubGlobal('useUserSession', () => ({ user: ref({ id: 'me' }) }))
  vi.stubGlobal('useUploadStore', () => ({ uploadsArray: [] }))
  vi.stubGlobal('useActionRunsStore', () => ({ runsArray: runs, workflowRunsArray: [], workflowRunsById: new Map() }))
  vi.stubGlobal('useBackgroundJobsStore', () => ({ jobsArray: [] }))
  vi.stubGlobal('useIiifImportJobsStore', () => ({ jobsArray: [] }))
  const workspace = reactive({ selectedWorkspaceId: 'ws-1' })
  vi.stubGlobal('useWorkspaceStore', () => workspace)
  vi.stubGlobal('useRealtimeSocket', () => ({}))
  vi.stubGlobal('useStatusIssues', () => ({ issues: ref([]), hasIssues: ref(false) }))
  vi.stubGlobal('onMounted', () => {})
  vi.stubGlobal('onBeforeUnmount', () => {})
  const scope = effectScope()
  const center = scope.run(useStatusCenter)!
  return { runs, center, workspace, request, preferences: useEditorPreferences(), stop: () => scope.stop() }
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
  it('combines workspace and creator filters and follows the current workspace', () => {
    const { runs, center, workspace, stop } = setup()
    try {
      runs.push(run('mine-here', 'me'), run('other-here', 'other'),
        createRun({ id: 'mine-elsewhere', createdByUserId: 'me', workspaceId: 'ws-2' }))
      center.currentWorkspaceOnly.value = true
      expect(center.filteredJobs.value.map(job => job.id)).toEqual(['mine-here'])
      center.jobScope.value = 'all'
      expect(center.filteredJobs.value.map(job => job.id)).toEqual(['mine-here', 'other-here'])
      expect(center.scopedOwnJobs.value).toHaveLength(1)
      expect(center.activeJobs.value).toHaveLength(3)
      workspace.selectedWorkspaceId = 'ws-2'
      expect(center.filteredJobs.value.map(job => job.id)).toEqual(['mine-elsewhere'])
      center.currentWorkspaceOnly.value = false
      expect(center.filteredJobs.value).toHaveLength(3)
    } finally { stop() }
  })

  it('reveals a new own job from another workspace when auto-opening', async () => {
    const { runs, center, stop } = setup()
    try {
      center.currentWorkspaceOnly.value = true
      runs.push(createRun({ createdByUserId: 'me', workspaceId: 'ws-2' }))
      await nextTick()
      expect(center.isOverlayOpen.value).toBe(true)
      expect(center.currentWorkspaceOnly.value).toBe(false)
      expect(center.filteredJobs.value).toHaveLength(1)
    } finally { stop() }
  })

  it('loads the disabled preference and keeps new jobs from opening or expanding the panel', async () => {
    const { runs, center, request, preferences, stop } = setup()
    try {
      request.mockResolvedValueOnce({ autoOpenJobsPanel: false })
      await preferences.fetchPreferences()
      center.isOverlayMinimized.value = true
      runs.push(run('my-run', 'me'))
      await nextTick()
      expect(center.autoOpenJobsPanel.value).toBe(false)
      expect(center.isOverlayOpen.value).toBe(false)
      expect(center.isOverlayMinimized.value).toBe(true)
      expect(center.activeJobs.value).toHaveLength(1)
      center.openOverlay()
      expect(center.isOverlayOpen.value).toBe(true)
    } finally { stop() }
  })

  it('saves toggle changes immediately and resumes auto-opening when enabled', async () => {
    const { runs, center, request, stop } = setup()
    try {
      center.autoOpenJobsPanel.value = false
      await nextTick()
      expect(request).toHaveBeenLastCalledWith('/api/editor/preferences', {
        method: 'PUT', body: { autoOpenJobsPanel: false }
      })
      runs.push(run('first-run', 'me'))
      await nextTick()
      expect(center.isOverlayOpen.value).toBe(false)
      center.autoOpenJobsPanel.value = true
      await nextTick()
      expect(request).toHaveBeenLastCalledWith('/api/editor/preferences', {
        method: 'PUT', body: { autoOpenJobsPanel: true }
      })
      expect(center.isOverlayOpen.value).toBe(false)
      runs.push(run('second-run', 'me'))
      await nextTick()
      expect(center.isOverlayOpen.value).toBe(true)
    } finally { stop() }
  })

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
