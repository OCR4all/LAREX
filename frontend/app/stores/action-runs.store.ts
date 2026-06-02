import { defineStore } from 'pinia'
import type { ActionRun, ClearActionRunsResponse } from '@/types/action'

type ActionRunStatus = ActionRun['status']

export interface TrackedActionRun extends ActionRun {
  projectName: string
}

export interface ActionRunTerminalEvent {
  sequence: number
  run: TrackedActionRun
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
  const showProgressPanel = ref(false)
  const minimized = ref(false)
  const cancellingRunIds = ref<Set<string>>(new Set())
  const dismissedRunIds = ref<Set<string>>(new Set())
  let terminalEventSequence = 0

  const runsArray = computed(() => Array.from(runsById.value.values())
    .sort((left, right) => Date.parse(right.created) - Date.parse(left.created)))

  const hasActiveRuns = computed(() => runsArray.value.some(run => isActiveStatus(run.status)))
  const totalActiveRuns = computed(() => runsArray.value.filter(run => isActiveStatus(run.status)).length)
  const overallProgress = computed(() => {
    const active = runsArray.value.filter(run => isActiveStatus(run.status))
    if (active.length === 0) return 0
    return Math.round(active.reduce((sum, run) => sum + run.progressPercent, 0) / active.length)
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

  function upsertRuns(runs: ActionRun[], projectName?: string | null) {
    runs.forEach(run => upsertRun(run, projectName))
  }

  async function refreshProjectRuns(workspaceId: string, projectId: string, projectName?: string | null) {
    const runs = await $fetch<ActionRun[]>(`/api/workspaces/${workspaceId}/actions/projects/${projectId}/runs`)
    upsertRuns(runs, projectName)
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
        await $fetch<void>(
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
    const run = runsArray.value.find(candidate =>
      candidate.projectId === projectId
      && isLockingStatus(candidate.status)
      && candidate.pageIds.includes(pageId)
    )
    return run ? `LAREX Action running: ${run.processorName}` : null
  }

  return {
    runsById,
    terminalEvents,
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
    refreshActiveRuns,
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
    getPageActionLockReason
  }
})
