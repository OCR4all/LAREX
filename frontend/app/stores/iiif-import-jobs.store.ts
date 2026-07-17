import { defineStore } from 'pinia'
import type {
  DismissIiifImportJobsResponse,
  IiifImportJob,
  IiifImportJobStatus
} from '@/types/iiif-import'

function isActiveStatus(status: IiifImportJobStatus): boolean {
  return status === 'PENDING' || status === 'IMPORTING'
}

function isTerminalStatus(status: IiifImportJobStatus): boolean {
  return status === 'COMPLETED' || status === 'FAILED' || status === 'CANCELLED'
}

let realtimeInitialized = false
let realtimeRefreshTimer: ReturnType<typeof setTimeout> | null = null
const pendingRealtimeWorkspaceIds = new Set<string>()

export interface IiifImportTerminalEvent {
  sequence: number
  job: IiifImportJob
}

export const useIiifImportJobsStore = defineStore('iiif-import-jobs', () => {
  const jobsById = ref<Map<string, IiifImportJob>>(new Map())
  const cancellingJobIds = ref<Set<string>>(new Set())
  const dismissedJobIds = ref<Set<string>>(new Set())
  const terminalEvents = ref<IiifImportTerminalEvent[]>([])
  const refreshRequests = new Map<string, Promise<IiifImportJob[]>>()
  const latestInvalidatedCompletionByProject = new Map<string, number>()
  let terminalEventSequence = 0

  const jobsArray = computed(() => Array.from(jobsById.value.values())
    .sort((left, right) => Date.parse(right.created) - Date.parse(left.created)))
  const hasActiveJobs = computed(() => jobsArray.value.some(job => isActiveStatus(job.status)))

  function upsertJob(job: IiifImportJob) {
    if (dismissedJobIds.value.has(job.id) && isTerminalStatus(job.status)) return

    const existing = jobsById.value.get(job.id)
    if (existing && Date.parse(job.updated) < Date.parse(existing.updated)) return
    const next = { ...existing, ...job }
    if (existing && isTerminalStatus(existing.status) && !isTerminalStatus(job.status)) {
      next.status = existing.status
    }
    jobsById.value.set(job.id, next)
    if (isTerminalStatus(next.status)) {
      setCancelling(job.id, false)
      recordPageChangingTerminalJob(next)
    }
  }

  function upsertJobs(jobs: IiifImportJob[]) {
    jobs.forEach(upsertJob)
  }

  function refreshWorkspaceJobs(workspaceId: string): Promise<IiifImportJob[]> {
    const existingRequest = refreshRequests.get(workspaceId)
    if (existingRequest) return existingRequest

    const request = $fetch<IiifImportJob[]>(`/api/workspaces/${workspaceId}/iiif-import/jobs`)
      .then((jobs) => {
        const returnedIds = new Set(jobs.map(job => job.id))

        for (const existing of jobsArray.value) {
          if (existing.workspaceId === workspaceId && !returnedIds.has(existing.id)) {
            jobsById.value.delete(existing.id)
            setCancelling(existing.id, false)
          }
        }
        upsertJobs(jobs)
        return jobs
      })
      .finally(() => {
        refreshRequests.delete(workspaceId)
      })
    refreshRequests.set(workspaceId, request)
    return request
  }

  async function refreshActiveJobs() {
    const workspaceIds = new Set(
      jobsArray.value.filter(job => isActiveStatus(job.status)).map(job => job.workspaceId)
    )
    await Promise.allSettled(Array.from(workspaceIds).map(refreshWorkspaceJobs))
  }

  function initializeRealtime() {
    if (import.meta.server || realtimeInitialized) return
    realtimeInitialized = true
    useRealtimeSocket().subscribe((message) => {
      if (message.type !== 'JOB_UPDATED') return
      const payload = message.payload as { kind?: unknown, workspaceId?: unknown } | null
      if (payload?.kind !== 'IIIF_IMPORT' || typeof payload.workspaceId !== 'string') return
      pendingRealtimeWorkspaceIds.add(payload.workspaceId)
      if (realtimeRefreshTimer) return
      realtimeRefreshTimer = setTimeout(() => {
        realtimeRefreshTimer = null
        const workspaceIds = Array.from(pendingRealtimeWorkspaceIds)
        pendingRealtimeWorkspaceIds.clear()
        void Promise.allSettled(workspaceIds.map(refreshWorkspaceJobs))
      }, 50)
    })
  }

  async function cancelJob(job: IiifImportJob) {
    setCancelling(job.id, true)
    try {
      const updated = await $fetch<IiifImportJob>(
        `/api/workspaces/${job.workspaceId}/projects/${job.projectId}/iiif-import/jobs/${job.id}`,
        { method: 'DELETE' }
      )
      upsertJob(updated)
      return updated
    } finally {
      setCancelling(job.id, false)
    }
  }

  async function dismissJob(job: IiifImportJob) {
    if (!isTerminalStatus(job.status)) return
    acknowledgeJob(job.id)
    try {
      await $fetch<unknown>(
        `/api/workspaces/${job.workspaceId}/iiif-import/jobs/${job.id}/dismiss`,
        { method: 'POST' }
      )
    } catch (error) {
      forgetDismissal(job.id)
      upsertJob(job)
      throw error
    }
  }

  async function dismissCompletedJobs() {
    const workspaceIds = new Set(
      jobsArray.value.filter(job => isTerminalStatus(job.status)).map(job => job.workspaceId)
    )
    const jobsToDismiss = jobsArray.value.filter(job => isTerminalStatus(job.status))
    jobsToDismiss.forEach(job => acknowledgeJob(job.id))

    const results = await Promise.allSettled(Array.from(workspaceIds).map(workspaceId =>
      $fetch<DismissIiifImportJobsResponse>(
        `/api/workspaces/${workspaceId}/iiif-import/jobs/history/dismiss`,
        { method: 'POST' }
      )
    ))
    const rejected = results.find(result => result.status === 'rejected')
    if (rejected?.status === 'rejected') throw rejected.reason
  }

  function acknowledgeJob(jobId: string) {
    const next = new Set(dismissedJobIds.value)
    next.add(jobId)
    dismissedJobIds.value = next
    jobsById.value.delete(jobId)
    setCancelling(jobId, false)
  }

  function forgetDismissal(jobId: string) {
    const next = new Set(dismissedJobIds.value)
    next.delete(jobId)
    dismissedJobIds.value = next
  }

  function isCancelling(jobId: string): boolean {
    return cancellingJobIds.value.has(jobId)
  }

  function setCancelling(jobId: string, value: boolean) {
    const next = new Set(cancellingJobIds.value)
    if (value) {
      next.add(jobId)
    } else {
      next.delete(jobId)
    }
    cancellingJobIds.value = next
  }

  function recordPageChangingTerminalJob(job: IiifImportJob) {
    if (job.processedCanvases <= 0) return
    const completedAt = Date.parse(job.completedAt || job.updated)
    const previousCompletion = latestInvalidatedCompletionByProject.get(job.projectId) ?? 0
    if (!Number.isFinite(completedAt) || completedAt <= previousCompletion) return

    latestInvalidatedCompletionByProject.set(job.projectId, completedAt)
    terminalEvents.value = [
      ...terminalEvents.value.slice(-49),
      {
        sequence: ++terminalEventSequence,
        job
      }
    ]
    void $fetch(`/api/projects/${job.projectId}/pages/invalidate-cache`, {
      method: 'POST'
    }).catch(() => {
      // The project view also performs best-effort invalidation before refreshing.
    })
  }

  return {
    jobsById,
    cancellingJobIds,
    terminalEvents,
    jobsArray,
    hasActiveJobs,
    upsertJob,
    upsertJobs,
    refreshWorkspaceJobs,
    refreshActiveJobs,
    initializeRealtime,
    cancelJob,
    dismissJob,
    dismissCompletedJobs,
    isCancelling
  }
})
