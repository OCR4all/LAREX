import { defineStore } from 'pinia'

export type BackgroundJobStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'

export interface BackgroundJob {
  id: string
  title: string
  subtitle: string
  status: BackgroundJobStatus
  statusLabel: string
  progressPercent: number | null
  icon: string
  created: string
  updated: string
  error?: string
  detail?: string
  retryable?: boolean
}

type StartBackgroundJobOptions = {
  title: string
  subtitle?: string
  statusLabel?: string
  progressPercent?: number | null
  icon?: string
}

type UpdateBackgroundJobOptions = Partial<Pick<BackgroundJob, 'subtitle' | 'statusLabel' | 'progressPercent' | 'icon' | 'error' | 'detail'>>

function isActiveStatus(status: BackgroundJobStatus): boolean {
  return status === 'PENDING' || status === 'RUNNING'
}

function isTerminalStatus(status: BackgroundJobStatus): boolean {
  return status === 'COMPLETED' || status === 'FAILED'
}

function createJobId(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID()
  }
  return `background-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

export const useBackgroundJobsStore = defineStore('background-jobs', () => {
  const jobsById = ref<Map<string, BackgroundJob>>(new Map())
  const retryHandlers = new Map<string, () => Promise<unknown>>()
  const retryingJobIds = ref<Set<string>>(new Set())

  const jobsArray = computed(() => Array.from(jobsById.value.values())
    .sort((left, right) => Date.parse(right.created) - Date.parse(left.created)))

  const hasActiveJobs = computed(() => jobsArray.value.some(job => isActiveStatus(job.status)))

  function startJob(options: StartBackgroundJobOptions): string {
    const now = new Date().toISOString()
    const id = createJobId()
    jobsById.value.set(id, {
      id,
      title: options.title,
      subtitle: options.subtitle ?? 'Preparing',
      status: 'RUNNING',
      statusLabel: options.statusLabel ?? 'Running',
      progressPercent: options.progressPercent ?? null,
      icon: options.icon ?? 'i-lucide-download',
      created: now,
      updated: now
    })
    return id
  }

  function setRetryHandler(id: string, retry: () => Promise<unknown>) {
    const job = jobsById.value.get(id)
    if (!job) return
    retryHandlers.set(id, retry)
    jobsById.value.set(id, {
      ...job,
      retryable: true
    })
  }

  function updateJob(id: string, updates: UpdateBackgroundJobOptions) {
    const job = jobsById.value.get(id)
    if (!job || isTerminalStatus(job.status)) return
    jobsById.value.set(id, {
      ...job,
      ...updates,
      updated: new Date().toISOString()
    })
  }

  function completeJob(id: string, updates: UpdateBackgroundJobOptions = {}) {
    const job = jobsById.value.get(id)
    if (!job || isTerminalStatus(job.status)) return
    jobsById.value.set(id, {
      ...job,
      ...updates,
      status: 'COMPLETED',
      statusLabel: updates.statusLabel ?? 'Completed',
      progressPercent: updates.progressPercent ?? 100,
      updated: new Date().toISOString()
    })
  }

  function failJob(id: string, error: string, updates: UpdateBackgroundJobOptions = {}) {
    const job = jobsById.value.get(id)
    if (!job || isTerminalStatus(job.status)) return
    jobsById.value.set(id, {
      ...job,
      ...updates,
      status: 'FAILED',
      statusLabel: updates.statusLabel ?? 'Failed',
      error,
      updated: new Date().toISOString()
    })
  }

  function removeJob(id: string) {
    jobsById.value.delete(id)
    retryHandlers.delete(id)
    setRetrying(id, false)
  }

  function clearCompletedJobs() {
    for (const job of jobsArray.value) {
      if (isTerminalStatus(job.status)) {
        jobsById.value.delete(job.id)
        retryHandlers.delete(job.id)
        setRetrying(job.id, false)
      }
    }
  }

  function canRetryJob(id: string): boolean {
    return retryHandlers.has(id)
  }

  function isRetrying(id: string): boolean {
    return retryingJobIds.value.has(id)
  }

  async function retryJob(id: string) {
    const retry = retryHandlers.get(id)
    if (!retry || isRetrying(id)) return
    setRetrying(id, true)
    try {
      await retry()
    } finally {
      setRetrying(id, false)
    }
  }

  function setRetrying(id: string, value: boolean) {
    const next = new Set(retryingJobIds.value)
    if (value) {
      next.add(id)
    } else {
      next.delete(id)
    }
    retryingJobIds.value = next
  }

  return {
    jobsById,
    retryingJobIds,
    jobsArray,
    hasActiveJobs,
    startJob,
    setRetryHandler,
    updateJob,
    completeJob,
    failJob,
    removeJob,
    clearCompletedJobs,
    canRetryJob,
    isRetrying,
    retryJob
  }
})
