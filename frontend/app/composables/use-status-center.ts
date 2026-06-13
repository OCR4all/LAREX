import {
  buildStatusJobs,
  shouldAutoOpenStatusPopover
} from '@/utils/status-center'

let actionPollTimer: ReturnType<typeof setInterval> | null = null
let pollSubscriberCount = 0

function startPolling(actionRunsStore: ReturnType<typeof useActionRunsStore>) {
  if (!import.meta.client || actionPollTimer) return
  actionPollTimer = setInterval(() => {
    if (actionRunsStore.hasActiveRuns) {
      void actionRunsStore.refreshActiveRuns()
    }
  }, 2500)
}

function stopPolling() {
  if (!actionPollTimer) return
  clearInterval(actionPollTimer)
  actionPollTimer = null
}

export function useStatusCenter() {
  const uploadStore = useUploadStore()
  const actionRunsStore = useActionRunsStore()
  const backgroundJobsStore = useBackgroundJobsStore()
  const { issues, hasIssues } = useStatusIssues()
  const isOverlayOpen = useState('app.statusCenter.overlayOpen', () => false)
  const isOverlayMinimized = useState('app.statusCenter.overlayMinimized', () => false)
  const overlayAnchorId = useState<string | null>('app.statusCenter.overlayAnchorId', () => null)

  const jobs = computed(() => buildStatusJobs(uploadStore.uploadsArray, actionRunsStore.runsArray, backgroundJobsStore.jobsArray))
  const activeJobs = computed(() => jobs.value.filter(job => job.active))
  const terminalJobs = computed(() => jobs.value.filter(job => job.terminal))
  const hasActiveJobs = computed(() => activeJobs.value.length > 0)

  const activeCountLabel = computed(() => `${activeJobs.value.length} active`)
  const completedCountLabel = computed(() => `${terminalJobs.value.length} done`)
  const issueCountLabel = computed(() => `${issues.value.length} issue${issues.value.length === 1 ? '' : 's'}`)

  const headerTitle = computed(() => {
    if (issues.value.length > 0 && activeJobs.value.length === 0) {
      return issues.value[0]?.severity === 'error' ? 'Attention required' : 'Jobs'
    }
    if (activeJobs.value.length === 0) return 'Jobs'
    if (activeJobs.value.length === 1) return activeJobs.value[0]?.statusLabel || 'Jobs'
    return 'Jobs'
  })

  const overallProgress = computed(() => {
    if (activeJobs.value.length === 0) return 0
    const total = activeJobs.value.reduce((sum, job) => sum + (job.progress ?? 0), 0)
    return Math.round(total / activeJobs.value.length)
  })

  onMounted(() => {
    pollSubscriberCount += 1
    if (pollSubscriberCount === 1) {
      startPolling(actionRunsStore)
    }
  })

  onBeforeUnmount(() => {
    pollSubscriberCount = Math.max(0, pollSubscriberCount - 1)
    if (pollSubscriberCount === 0) {
      stopPolling()
    }
  })

  watch(() => activeJobs.value.length, (count, previousCount) => {
    if (shouldAutoOpenStatusPopover(previousCount ?? 0, count)) {
      isOverlayOpen.value = true
      isOverlayMinimized.value = false
    }
  })

  watch(() => issues.value.length, (count, previousCount) => {
    if (shouldAutoOpenStatusPopover(previousCount ?? 0, count)) {
      isOverlayOpen.value = true
      isOverlayMinimized.value = false
    }
  })

  function openOverlay(anchorId: string | null = null) {
    overlayAnchorId.value = anchorId
    isOverlayOpen.value = true
  }

  function closeOverlay() {
    isOverlayOpen.value = false
    overlayAnchorId.value = null
  }

  function toggleOverlay(anchorId: string | null = null) {
    if (isOverlayOpen.value && overlayAnchorId.value === anchorId) {
      closeOverlay()
      return
    }
    openOverlay(anchorId)
  }

  function toggleOverlayMinimized() {
    isOverlayMinimized.value = !isOverlayMinimized.value
  }

  function setOverlayAnchor(anchorId: string | null) {
    overlayAnchorId.value = anchorId
  }

  return {
    isOverlayOpen,
    isOverlayMinimized,
    overlayAnchorId,
    jobs,
    activeJobs,
    terminalJobs,
    issues,
    hasIssues,
    hasActiveJobs,
    activeCountLabel,
    completedCountLabel,
    issueCountLabel,
    headerTitle,
    overallProgress,
    openOverlay,
    closeOverlay,
    toggleOverlay,
    toggleOverlayMinimized,
    setOverlayAnchor
  }
}
