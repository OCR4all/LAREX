import { computed, nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { FocusedWorkSession } from '@/stores/editor/editor.session.store'
import { useFocusedWorkQueue } from '../use-focused-work-queue'

vi.mock('vue', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue')>()
  return { ...actual, onMounted: (callback: () => void) => callback(), onBeforeUnmount: vi.fn() }
})
vi.mock('@vueuse/core', () => ({ useEventListener: vi.fn() }))
vi.mock('@/stores/editor/editor.session.store', () => ({ useEditorSessionStore: vi.fn() }))

const subscribers: Array<(message: { type?: string, payload?: unknown }) => void> = []
const fetchMock = vi.fn()

vi.stubGlobal('$fetch', fetchMock)
vi.stubGlobal('useRealtimeSocket', () => ({
  connectionStatus: ref('connected'),
  connect: vi.fn(),
  subscribe: (listener: (message: { type?: string, payload?: unknown }) => void) => {
    subscribers.push(listener)
    return () => undefined
  }
}))

function session(overrides: Partial<FocusedWorkSession>): FocusedWorkSession {
  return {
    kind: 'assigned',
    workspaceId: 'workspace-1',
    startedAt: '2026-11-04T00:00:00.000Z',
    completedSubtaskIds: ['subtask-1'],
    reviewedPageIds: ['page-9', 'page-8'],
    ...overrides
  }
}

function mountQueue(focusedWork: FocusedWorkSession) {
  const openPage = vi.fn(async () => true)
  const queue = useFocusedWorkQueue({
    workspaceId: computed(() => 'workspace-1'),
    currentPageId: computed(() => 'page-1'),
    focusedWork: computed(() => focusedWork),
    openPage
  })
  return { queue, openPage }
}

async function settle() {
  await nextTick()
  await new Promise(resolve => setTimeout(resolve))
  await nextTick()
}

const reviewQueue = {
  workspaceId: 'workspace-1',
  workspaceName: 'Workspace',
  totalPageCount: 2,
  blockedPageCount: 1,
  pages: [
    { pageId: 'page-1', pageName: 'One', projectId: 'project-1', projectName: 'Project', blocked: false },
    { pageId: 'page-2', pageName: 'Two', projectId: 'project-1', projectName: 'Project', blocked: true }
  ]
}

describe('useFocusedWorkQueue', () => {
  beforeEach(() => {
    subscribers.length = 0
    fetchMock.mockReset()
  })

  it('loads the assigned queue by default', async () => {
    fetchMock.mockResolvedValue({ openAssignedSubtaskCount: 3, blockedAssignedPageCount: 0, pages: [] })
    const { queue } = mountQueue(session({}))
    await settle()

    expect(fetchMock).toHaveBeenCalledWith('/api/workspaces/workspace-1/tasks/assigned-to-me/queue')
    expect(queue.remainingTaskCount.value).toBe(3)
    expect(queue.completedTaskCount.value).toBe(1)
  })

  it('loads the review queue with its filters and maps it to queue pages', async () => {
    fetchMock.mockResolvedValue(reviewQueue)
    const reviewFilters = { projectId: 'project-1' }
    const { queue } = mountQueue(session({ kind: 'review', reviewFilters }))
    await settle()

    expect(fetchMock).toHaveBeenCalledWith('/api/workspaces/workspace-1/review-queue', { query: reviewFilters })
    expect(queue.remainingTaskCount.value).toBe(2)
    expect(queue.blockedPageCount.value).toBe(1)
    expect(queue.completedTaskCount.value).toBe(2)
    expect(queue.actionablePages.value.map(page => page.pageId)).toEqual(['page-1'])
    expect(queue.pages.value[0]?.subtasks).toEqual([])
  })

  it('is complete once no review pages remain', async () => {
    fetchMock.mockResolvedValue({ ...reviewQueue, totalPageCount: 0, blockedPageCount: 0, pages: [] })
    const { queue } = mountQueue(session({ kind: 'review' }))
    await settle()

    expect(queue.isComplete.value).toBe(true)
  })

  it('refreshes a review session on workflow events only', async () => {
    vi.useFakeTimers()
    try {
      fetchMock.mockResolvedValue(reviewQueue)
      mountQueue(session({ kind: 'review' }))
      await vi.advanceTimersByTimeAsync(0)
      fetchMock.mockClear()

      subscribers[0]?.({ type: 'TASK_QUEUE_CHANGED', payload: { workspaceId: 'workspace-1' } })
      await vi.advanceTimersByTimeAsync(200)
      expect(fetchMock).not.toHaveBeenCalled()

      subscribers[0]?.({ type: 'PAGE_WORKFLOW_CHANGED', payload: { workspaceId: 'other' } })
      await vi.advanceTimersByTimeAsync(200)
      expect(fetchMock).not.toHaveBeenCalled()

      subscribers[0]?.({ type: 'PAGE_WORKFLOW_CHANGED', payload: { workspaceId: 'workspace-1' } })
      await vi.advanceTimersByTimeAsync(200)
      expect(fetchMock).toHaveBeenCalledTimes(1)
    } finally {
      vi.useRealTimers()
    }
  })
})
