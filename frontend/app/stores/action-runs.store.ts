import { defineStore } from 'pinia'
import type { ActionRun, ActionRunDetail, ClearActionRunsResponse } from '@/types/action'

type ActionRunStatus = ActionRun['status']

export interface TrackedActionRun extends ActionRun {
  projectName: string
}

export interface ActionRunTerminalEvent {
  sequence: number
  run: TrackedActionRun
}

export interface ActionPageResultEvent {
  sequence: number
  runId: string
  workspaceId: string
  projectId: string
  pageId: string
  resultTypes: string[]
}

type ActionRealtimePayload = {
  runId?: string
  workspaceId?: string
  projectId?: string
  pageId?: string
  resultTypes?: unknown
}

function isTerminalStatus(status: ActionRunStatus): boolean {
  return status === 'COMPLETED' || status === 'FAILED' || status === 'CANCELLED'
}

function isActiveStatus(status: ActionRunStatus): boolean {
  return status === 'QUEUED'
    || status === 'PENDING'
    || status === 'DISPATCHING'
    || status === 'RUNNING'
    || status === 'IMPORTING_RESULTS'
    || status === 'CANCEL_REQUESTED'
}

function isLockingStatus(status: ActionRunStatus): boolean {
  return status === 'PENDING'
    || status === 'DISPATCHING'
    || status === 'RUNNING'
    || status === 'IMPORTING_RESULTS'
    || status === 'CANCEL_REQUESTED'
}

export const useActionRunsStore = defineStore('action-runs', () => {
  const runsById = ref<Map<string, TrackedActionRun>>(new Map())
  const terminalEvents = ref<ActionRunTerminalEvent[]>([])
  const pageResultEvents = ref<ActionPageResultEvent[]>([])
  const showProgressPanel = ref(false)
  const minimized = ref(false)
  const cancellingRunIds = ref<Set<string>>(new Set())
  const dismissedRunIds = ref<Set<string>>(new Set())
  let terminalEventSequence = 0
  let pageResultEventSequence = 0
  let realtimeInitialized = false
  let fallbackPollingInitialized = false
  const emittedPageResultKeys = new Set<string>()
  const pendingRealtimeScopes = new Map<string, {
    workspaceId: string
    projectId: string
    runId?: string
    projectName?: string | null
  }>()
  let realtimeRefreshTimer: ReturnType<typeof setTimeout> | null = null

  const runsArray = computed(() => Array.from(runsById.value.values())
    .sort((left, right) => Date.parse(right.created) - Date.parse(left.created)))

  const hasActiveRuns = computed(() => runsArray.value.some(run => isActiveStatus(run.status)))
  const totalActiveRuns = computed(() => runsArray.value.filter(run => isActiveStatus(run.status)).length)
  const overallProgress = computed(() => {
    const active = runsArray.value.filter(run => isActiveStatus(run.status))
    if (active.length === 0) return 0
    return Math.round(active.reduce((sum, run) => sum + run.progressPercent, 0) / active.length)
  })
  const activeProjectIds = computed(() => new Set(
    runsArray.value.filter(run => isLockingStatus(run.status)).map(run => run.projectId)
  ))
  const activePageReasons = computed(() => {
    const reasons = new Map<string, string>()
    for (const run of runsArray.value) {
      if (!isLockingStatus(run.status)) continue
      for (const pageId of run.pageIds) {
        if (run.lockMode !== 'PROJECT' && !isActionRunPageActive(run.id, pageId)) continue
        reasons.set(`${run.projectId}:${pageId}`, `LAREX Action running: ${run.processorName}`)
      }
    }
    return reasons
  })
  const activePageKeys = computed(() => {
    const keys = new Set<string>()
    for (const run of runsArray.value) {
      if (!isLockingStatus(run.status)) continue
      for (const pageId of run.pageIds) {
        if (isActionRunPageActive(run.id, pageId)) keys.add(`${run.projectId}:${pageId}`)
      }
    }
    return keys
  })

  function upsertRun(run: ActionRun, projectName?: string | null) {
    if (dismissedRunIds.value.has(run.id) && isTerminalStatus(run.status)) {
      setCancelling(run.id, false)
      return
    }
    if (!isTerminalStatus(run.status) && dismissedRunIds.value.has(run.id)) {
      const nextDismissed = new Set(dismissedRunIds.value)
      nextDismissed.delete(run.id)
      dismissedRunIds.value = nextDismissed
    }

    const existing = runsById.value.get(run.id)
    const existingWasActive = existing ? isActiveStatus(existing.status) : false
    const next: TrackedActionRun = {
      ...existing,
      ...run,
      projectName: projectName || existing?.projectName || run.projectId
    }
    if (existing && isTerminalStatus(existing.status) && !isTerminalStatus(run.status)) {
      next.status = existing.status
    }
    runsById.value.set(run.id, next)
    if (existing) {
      const previousCompletedPageIds = new Set(existing.completedPageIds ?? [])
      for (const pageId of next.completedPageIds ?? []) {
        if (!previousCompletedPageIds.has(pageId)) {
          appendPageResultEvent({
            runId: next.id,
            workspaceId: next.workspaceId,
            projectId: next.projectId,
            pageId,
            resultTypes: []
          })
        }
      }
    }
    if (isActiveStatus(next.status) || showProgressPanel.value) {
      showProgressPanel.value = true
    }
    if (isActiveStatus(next.status)) {
      minimized.value = false
    }
    if (isTerminalStatus(next.status)) {
      setCancelling(run.id, false)
      if (existingWasActive) {
        terminalEvents.value = [
          ...terminalEvents.value.slice(-49),
          {
            sequence: ++terminalEventSequence,
            run: next
          }
        ]
      }
    }
  }

  function appendPageResultEvent(event: Omit<ActionPageResultEvent, 'sequence'>) {
    const key = `${event.runId}:${event.pageId}`
    if (emittedPageResultKeys.has(key)) return
    emittedPageResultKeys.add(key)
    if (emittedPageResultKeys.size > 1000) {
      const oldestKey = emittedPageResultKeys.values().next().value
      if (oldestKey) emittedPageResultKeys.delete(oldestKey)
    }
    pageResultEvents.value = [
      ...pageResultEvents.value.slice(-99),
      { ...event, sequence: ++pageResultEventSequence }
    ]
  }

  function scheduleRealtimeRefresh(payload: ActionRealtimePayload) {
    if (!payload.workspaceId || !payload.projectId) return
    const knownRun = payload.runId ? runsById.value.get(payload.runId) : undefined
    pendingRealtimeScopes.set(`${payload.workspaceId}:${payload.projectId}:${payload.runId ?? '*'}`, {
      workspaceId: payload.workspaceId,
      projectId: payload.projectId,
      runId: payload.runId,
      projectName: knownRun?.projectName
    })
    if (realtimeRefreshTimer) return
    realtimeRefreshTimer = setTimeout(() => {
      realtimeRefreshTimer = null
      const scopes = Array.from(pendingRealtimeScopes.values())
      pendingRealtimeScopes.clear()
      void Promise.allSettled(scopes.map(async (scope) => {
        if (scope.runId) {
          await refreshRun(scope.workspaceId, scope.projectId, scope.runId, scope.projectName)
          return
        }
        await refreshProjectRuns(scope.workspaceId, scope.projectId, scope.projectName)
      }))
    }, 50)
  }

  function initializeRealtime() {
    if (import.meta.server || realtimeInitialized) return
    realtimeInitialized = true
    const realtime = useRealtimeSocket()
    realtime.subscribe((message) => {
      if (message.type !== 'ACTION_RUN_UPDATED' && message.type !== 'ACTION_PAGE_RESULT_IMPORTED') return
      const payload = (message.payload ?? {}) as ActionRealtimePayload
      scheduleRealtimeRefresh(payload)
      if (
        message.type === 'ACTION_PAGE_RESULT_IMPORTED'
        && payload.runId
        && payload.workspaceId
        && payload.projectId
        && payload.pageId
      ) {
        appendPageResultEvent({
          runId: payload.runId,
          workspaceId: payload.workspaceId,
          projectId: payload.projectId,
          pageId: payload.pageId,
          resultTypes: Array.isArray(payload.resultTypes)
            ? payload.resultTypes.filter((value): value is string => typeof value === 'string')
            : []
        })
      }
    })
    initializeFallbackPolling()
  }

  function initializeFallbackPolling() {
    if (import.meta.server || fallbackPollingInitialized) return
    fallbackPollingInitialized = true
    setInterval(() => {
      const pageVisible = typeof document === 'undefined' || document.visibilityState === 'visible'
      if (pageVisible && hasActiveRuns.value) {
        void refreshActiveRuns()
      }
    }, 2500)
  }

  function upsertRuns(runs: ActionRun[], projectName?: string | null) {
    runs.forEach(run => upsertRun(run, projectName))
  }

  async function refreshProjectRuns(workspaceId: string, projectId: string, projectName?: string | null) {
    const runs = await $fetch<ActionRun[]>(`/api/workspaces/${workspaceId}/actions/projects/${projectId}/runs`)
    upsertRuns(runs, projectName)
    return runs
  }

  async function refreshRun(
    workspaceId: string,
    projectId: string,
    runId: string,
    projectName?: string | null
  ) {
    const detail = await $fetch<ActionRunDetail>(
      `/api/workspaces/${workspaceId}/actions/projects/${projectId}/runs/${runId}`
    )
    upsertRun(detail.run, projectName)
    return detail.run
  }

  async function refreshWorkspaceRuns(workspaceId: string) {
    const runs = await $fetch<ActionRun[]>(`/api/workspaces/${workspaceId}/actions/runs`)
    runs.forEach(run => upsertRun(run, run.projectLabel))
    return runs
  }

  async function refreshActiveRuns() {
    const active = runsArray.value.filter(run => isActiveStatus(run.status))
    const scopes = new Map<string, TrackedActionRun>()
    active.forEach((run) => {
      scopes.set(`${run.workspaceId}:${run.projectId}`, run)
    })
    await Promise.allSettled(Array.from(scopes.values()).map(run =>
      refreshProjectRuns(run.workspaceId, run.projectId, run.projectName)
    ))
  }

  async function cancelRun(run: TrackedActionRun) {
    setCancelling(run.id, true)
    try {
      const updated = await $fetch<ActionRun>(
        `/api/workspaces/${run.workspaceId}/actions/projects/${run.projectId}/runs/${run.id}/cancel`,
        { method: 'POST' }
      )
      upsertRun(updated, run.projectName)
    } finally {
      setCancelling(run.id, false)
    }
  }

  async function dismissRun(run: TrackedActionRun) {
    try {
      if (isTerminalStatus(run.status)) {
        await $fetch<unknown>(
          `/api/workspaces/${run.workspaceId}/actions/projects/${run.projectId}/runs/${run.id}/dismiss`,
          { method: 'POST' }
        )
      }
    } finally {
      removeRun(run.id)
    }
  }

  async function dismissCompletedRuns() {
    const terminalRuns = runsArray.value.filter(run => isTerminalStatus(run.status))
    if (terminalRuns.length === 0) return
    const scopes = new Map<string, TrackedActionRun>()
    terminalRuns.forEach((run) => {
      scopes.set(`${run.workspaceId}:${run.projectId}`, run)
    })
    try {
      const results = await Promise.allSettled(Array.from(scopes.values()).map(run =>
        $fetch<ClearActionRunsResponse>(
          `/api/workspaces/${run.workspaceId}/actions/projects/${run.projectId}/runs/history/dismiss`,
          { method: 'POST' }
        )
      ))
      const rejected = results.find(result => result.status === 'rejected')
      if (rejected && rejected.status === 'rejected') {
        throw rejected.reason
      }
    } finally {
      clearCompletedRuns()
    }
  }

  function removeRun(runId: string) {
    acknowledgeRun(runId)
    runsById.value.delete(runId)
    setCancelling(runId, false)
    if (runsById.value.size === 0) {
      showProgressPanel.value = false
    }
  }

  function clearCompletedRuns() {
    for (const run of runsArray.value) {
      if (isTerminalStatus(run.status)) {
        acknowledgeRun(run.id)
        runsById.value.delete(run.id)
        setCancelling(run.id, false)
      }
    }
    if (runsById.value.size === 0) {
      showProgressPanel.value = false
    }
  }

  function acknowledgeRun(runId: string) {
    const run = runsById.value.get(runId)
    if (!run || !isTerminalStatus(run.status)) return
    const next = new Set(dismissedRunIds.value)
    next.add(runId)
    dismissedRunIds.value = next
  }

  function toggleMinimized() {
    minimized.value = !minimized.value
  }

  function hidePanel() {
    showProgressPanel.value = false
  }

  function isCancelling(runId: string): boolean {
    return cancellingRunIds.value.has(runId)
  }

  function setCancelling(runId: string, value: boolean) {
    const next = new Set(cancellingRunIds.value)
    if (value) {
      next.add(runId)
    } else {
      next.delete(runId)
    }
    cancellingRunIds.value = next
  }

  function getPageActionLockReason(projectId: string | null | undefined, pageId: string): string | null {
    if (!projectId || !pageId) return null
    return activePageReasons.value.get(`${projectId}:${pageId}`) ?? null
  }

  function isActionRunPageActive(runId: string, pageId: string): boolean {
    const run = runsById.value.get(runId)
    if (!run || !isLockingStatus(run.status) || !run.pageIds.includes(pageId)) return false
    return !(run.completedPageIds ?? []).includes(pageId)
      && !emittedPageResultKeys.has(`${runId}:${pageId}`)
  }

  function isProjectActionRunning(projectId: string): boolean {
    return activeProjectIds.value.has(projectId)
  }

  function isPageActionRunning(projectId: string, pageId: string): boolean {
    return activePageKeys.value.has(`${projectId}:${pageId}`)
  }

  return {
    runsById,
    terminalEvents,
    pageResultEvents,
    showProgressPanel,
    minimized,
    cancellingRunIds,
    dismissedRunIds,
    runsArray,
    hasActiveRuns,
    totalActiveRuns,
    overallProgress,
    upsertRun,
    upsertRuns,
    refreshProjectRuns,
    refreshRun,
    refreshWorkspaceRuns,
    refreshActiveRuns,
    initializeRealtime,
    cancelRun,
    dismissRun,
    dismissCompletedRuns,
    removeRun,
    clearCompletedRuns,
    acknowledgeRun,
    toggleMinimized,
    hidePanel,
    isCancelling,
    setCancelling,
    getPageActionLockReason,
    isActionRunPageActive,
    isProjectActionRunning,
    isPageActionRunning
  }
})
